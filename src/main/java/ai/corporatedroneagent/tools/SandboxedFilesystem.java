package ai.corporatedroneagent.tools;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The sandboxed filesystem engine behind {@link ProjectFilesystemTools}: everything below the
 * MCP {@code @Tool} surface — path sandboxing (the security-critical traversal/symlink checks),
 * charset/BOM-aware decode/encode, line-document editing, glob→regex matching, directory-tree
 * building, and metadata/size formatting. It is stateless apart from the project working folder,
 * so it can be unit-tested directly without the Spring AI tool machinery.
 *
 * <p>Every path operation is confined to the working folder, which the tools present as {@code /}.
 */
class SandboxedFilesystem {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());

    private final String workingFolder;
    // The working folder is fixed for this instance, so its resolved real path never changes during
    // a session. Cache it (on first successful resolve) instead of re-running toRealPath() filesystem
    // I/O on every helper call — building a directory tree calls root() per node.
    private Path resolvedRoot;

    SandboxedFilesystem(String workingFolder) {
        this.workingFolder = workingFolder;
    }

    Path existingFile(String path) {
        Path file = existingPath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Path is not a file: " + displayPath(path));
        }
        return file;
    }

    Path existingDirectory(String path) {
        Path directory = existingPath(path);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + displayPath(path));
        }
        return directory;
    }

    Path existingPath(String path) {
        Path root = root();
        Path resolved = resolveInsideRoot(root, path);
        try {
            Path realPath = resolved.toRealPath();
            if (!realPath.startsWith(root)) {
                throw new IllegalArgumentException("Path is outside the project working folder: " + displayPath(path));
            }
            return realPath;
        } catch (IOException exception) {
            throw new IllegalArgumentException("Path does not exist or cannot be accessed: " + displayPath(path));
        }
    }

    Path writablePath(String path) {
        Path root = root();
        Path resolved = resolveInsideRoot(root, path);
        Path ancestor = nearestExistingAncestor(resolved);
        if (ancestor != null) {
            try {
                Path realAncestor = ancestor.toRealPath();
                if (!realAncestor.startsWith(root)) {
                    throw new IllegalArgumentException("Path is outside the project working folder: " + displayPath(path));
                }
            } catch (IOException exception) {
                throw new IllegalArgumentException("Parent path cannot be accessed: " + displayPath(path));
            }
        }
        return resolved;
    }

    void rejectExistingSymlink(Path path, String originalPath) {
        if (Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("Refusing to write through symbolic link: " + displayPath(originalPath));
        }
    }

    private Path nearestExistingAncestor(Path path) {
        Path current = Files.isDirectory(path) ? path : path.getParent();
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        return current;
    }

    Path root() {
        if (resolvedRoot != null) {
            return resolvedRoot;
        }
        if (workingFolder == null || workingFolder.isBlank()) {
            throw new IllegalStateException("Project working folder is not configured.");
        }

        try {
            Path root = Path.of(workingFolder).toRealPath();
            if (!Files.isDirectory(root)) {
                throw new IllegalStateException("Project working folder is not a directory.");
            }
            resolvedRoot = root;
            return resolvedRoot;
        } catch (IOException exception) {
            throw new UncheckedIOException("Project working folder cannot be accessed.", exception);
        } catch (InvalidPathException exception) {
            throw new IllegalStateException("Project working folder path is invalid.", exception);
        }
    }

    private Path resolveInsideRoot(Path root, String path) {
        String relativePath = normalizeVirtualPath(path);
        Path relative;
        try {
            relative = relativePath.isEmpty() ? Path.of("") : Path.of(relativePath).normalize();
        } catch (InvalidPathException exception) {
            throw new IllegalArgumentException("Path is invalid: " + displayPath(path), exception);
        }

        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new IllegalArgumentException("Path is outside the project working folder: " + displayPath(path));
        }

        Path resolved = root.resolve(relative).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("Path is outside the project working folder: " + displayPath(path));
        }
        return resolved;
    }

    private String normalizeVirtualPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path is required.");
        }

        String normalized = path.trim().replace('\\', '/');
        if (normalized.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("Path is invalid.");
        }
        if (normalized.matches("^[A-Za-z]:.*") || normalized.startsWith("//")) {
            throw new IllegalArgumentException("Use paths under /, not local absolute paths: " + displayPath(path));
        }
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }

    List<Path> directoryEntries(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.sorted(Comparator.comparing(this::fileNameLower)).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list directory: " + virtualPath(directory), exception);
        }
    }

    TreeNode treeNode(Path path, List<String> excludePatterns) throws IOException {
        if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            List<TreeNode> children = new ArrayList<>();
            try (Stream<Path> stream = Files.list(path)) {
                for (Path child : stream.sorted(Comparator.comparing(this::fileNameLower)).toList()) {
                    if (!isExcluded(child, excludePatterns)) {
                        children.add(treeNode(child, excludePatterns));
                    }
                }
            }
            return new TreeNode(treeNodeName(path), "directory", children);
        }
        return new TreeNode(treeNodeName(path), "file", null);
    }

    private String treeNodeName(Path path) {
        if (path.equals(root())) {
            return "/";
        }
        return path.getFileName() == null ? "" : path.getFileName().toString();
    }

    boolean isExcluded(Path candidate, List<String> excludePatterns) {
        if (excludePatterns == null || excludePatterns.isEmpty()) {
            return false;
        }
        return excludePatterns.stream()
                .filter(Objects::nonNull)
                .filter(pattern -> !pattern.isBlank())
                .map(this::globPattern)
                .anyMatch(pattern -> matches(pattern, candidate));
    }

    boolean matches(Pattern pattern, Path candidate) {
        String virtualPath = virtualPath(candidate).substring(1);
        return pattern.matcher(virtualPath).matches();
    }

    boolean matches(Pattern pattern, String relativePath) {
        return pattern.matcher(relativePath.replace('\\', '/')).matches();
    }

    Pattern globPattern(String glob) {
        StringBuilder regex = new StringBuilder("^");
        String normalized = glob.trim().replace('\\', '/');
        if (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }

        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '*') {
                boolean doubleStar = index + 1 < normalized.length() && normalized.charAt(index + 1) == '*';
                if (doubleStar) {
                    boolean slashAfter = index + 2 < normalized.length() && normalized.charAt(index + 2) == '/';
                    regex.append(slashAfter ? "(?:.*/)?" : ".*");
                    index += slashAfter ? 2 : 1;
                } else {
                    regex.append("[^/]*");
                }
            } else if (current == '?') {
                regex.append("[^/]");
            } else if (current == '{') {
                regex.append("(?:");
            } else if (current == '}') {
                regex.append(")");
            } else if (current == ',') {
                regex.append("|");
            } else {
                if ("\\.[]()+-^$|".indexOf(current) >= 0) {
                    regex.append('\\');
                }
                regex.append(current);
            }
        }
        regex.append("$");
        return Pattern.compile(regex.toString());
    }

    String readText(Path file) {
        return readDecodedText(file).content();
    }

    DecodedText readDecodedText(Path file) {
        try {
            return decodeText(Files.readAllBytes(file));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read file: " + virtualPath(file), exception);
        }
    }

    private DecodedText decodeText(byte[] bytes) throws CharacterCodingException {
        if (startsWith(bytes, 0xEF, 0xBB, 0xBF)) {
            return new DecodedText(decode(bytes, 3, StandardCharsets.UTF_8), StandardCharsets.UTF_8, new byte[] {
                    (byte) 0xEF,
                    (byte) 0xBB,
                    (byte) 0xBF
            });
        }
        if (startsWith(bytes, 0xFE, 0xFF)) {
            return new DecodedText(decode(bytes, 2, StandardCharsets.UTF_16BE), StandardCharsets.UTF_16BE, new byte[] {
                    (byte) 0xFE,
                    (byte) 0xFF
            });
        }
        if (startsWith(bytes, 0xFF, 0xFE)) {
            return new DecodedText(decode(bytes, 2, StandardCharsets.UTF_16LE), StandardCharsets.UTF_16LE, new byte[] {
                    (byte) 0xFF,
                    (byte) 0xFE
            });
        }

        List<Charset> charsets = List.of(
                StandardCharsets.UTF_8,
                Charset.forName("windows-1252"),
                StandardCharsets.ISO_8859_1
        );
        CharacterCodingException lastException = null;
        for (Charset charset : charsets) {
            try {
                return new DecodedText(decode(bytes, 0, charset), charset, new byte[0]);
            } catch (CharacterCodingException exception) {
                lastException = exception;
            }
        }
        throw lastException == null ? new CharacterCodingException() : lastException;
    }

    private boolean startsWith(byte[] bytes, int... prefix) {
        if (bytes.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if ((bytes[index] & 0xFF) != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private String decode(byte[] bytes, int offset, Charset charset) throws CharacterCodingException {
        return charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                .toString();
    }

    byte[] encodeText(String content, DecodedText original) throws CharacterCodingException {
        ByteBuffer encoded = original.charset().newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(content));
        byte[] body = new byte[encoded.remaining()];
        encoded.get(body);
        byte[] bom = original.bom();
        byte[] bytes = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, bytes, 0, bom.length);
        System.arraycopy(body, 0, bytes, bom.length, body.length);
        return bytes;
    }

    LineDocument applyLineEdit(LineDocument document, String oldText, String newText, String path) {
        List<String> oldLines = parseLineSequence(oldText);
        if (oldLines.isEmpty()) {
            throw new IllegalArgumentException("oldText must contain at least one complete line.");
        }

        List<Integer> matches = lineSequenceMatches(document.lines(), oldLines);
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("oldText line sequence was not found in " + path + ".");
        }
        if (matches.size() > 1) {
            throw new IllegalArgumentException("oldText line sequence matched more than once in " + path + ".");
        }

        List<String> updatedLines = new ArrayList<>(document.lines());
        int start = matches.getFirst();
        updatedLines.subList(start, start + oldLines.size()).clear();
        updatedLines.addAll(start, parseLineSequence(newText));
        return new LineDocument(updatedLines, document.trailingLineSeparator(), document.lineSeparator());
    }

    private List<Integer> lineSequenceMatches(List<String> lines, List<String> sequence) {
        List<Integer> matches = new ArrayList<>();
        if (sequence.size() > lines.size()) {
            return matches;
        }

        for (int start = 0; start <= lines.size() - sequence.size(); start++) {
            boolean matched = true;
            for (int index = 0; index < sequence.size(); index++) {
                if (!lines.get(start + index).equals(sequence.get(index))) {
                    matched = false;
                    break;
                }
            }
            if (matched) {
                matches.add(start);
            }
        }
        return matches;
    }

    LineDocument parseDocument(String content) {
        String lineSeparator = content.contains("\r\n") ? "\r\n" : "\n";
        String normalized = normalizeLineSeparators(content);
        boolean trailingLineSeparator = normalized.endsWith("\n");
        List<String> lines = splitLines(normalized);
        return new LineDocument(lines, trailingLineSeparator, lineSeparator);
    }

    private List<String> parseLineSequence(String content) {
        return splitLines(normalizeLineSeparators(content));
    }

    private List<String> splitLines(String normalizedContent) {
        if (normalizedContent.isEmpty()) {
            return List.of();
        }

        String[] parts = normalizedContent.split("\n", -1);
        int limit = parts.length;
        if (normalizedContent.endsWith("\n")) {
            limit--;
        }

        List<String> lines = new ArrayList<>();
        for (int index = 0; index < limit; index++) {
            lines.add(parts[index]);
        }
        return lines;
    }

    private String normalizeLineSeparators(String content) {
        return content.replace("\r\n", "\n").replace('\r', '\n');
    }

    String renderDocument(LineDocument document) {
        String rendered = String.join(document.lineSeparator(), document.lines());
        if (document.trailingLineSeparator() && !document.lines().isEmpty()) {
            rendered += document.lineSeparator();
        }
        return rendered;
    }

    String mediaType(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return "image";
        }
        if (mimeType.startsWith("audio/")) {
            return "audio";
        }
        return "blob";
    }

    String prefix(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attributes.isDirectory() ? "[DIR]" : "[FILE]";
        } catch (IOException exception) {
            return "[FILE]";
        }
    }

    long size(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attributes.isDirectory() ? directorySize(path) : attributes.size();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private long directorySize(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.mapToLong(this::regularFileSizeNoFollow)
                    .sum();
        }
    }

    private long regularFileSizeNoFollow(Path path) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attributes.isRegularFile() ? attributes.size() : 0L;
        } catch (IOException exception) {
            return 0L;
        }
    }

    String fileType(BasicFileAttributes attributes) {
        if (attributes.isDirectory()) {
            return "directory";
        }
        if (attributes.isRegularFile()) {
            return "file";
        }
        if (attributes.isSymbolicLink()) {
            return "symlink";
        }
        return "other";
    }

    String formatTime(FileTime time) {
        return time == null ? "" : TIME_FORMATTER.format(time.toInstant());
    }

    String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kib);
        }
        return String.format(Locale.ROOT, "%.1f MB", kib / 1024.0);
    }

    String fileNameLower(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
    }

    String virtualPath(Path file) {
        Path root = root();
        Path normalized = file.normalize();
        Path relative = root.relativize(normalized);
        String path = relative.toString().replace('\\', '/');
        return path.isEmpty() ? "/" : "/" + path;
    }

    String displayPath(String path) {
        return path == null || path.isBlank() ? "<blank>" : path.trim();
    }

    record LineDocument(List<String> lines, boolean trailingLineSeparator, String lineSeparator) {
    }

    record TreeNode(String name, String type, List<TreeNode> children) {
    }

    record DecodedText(String content, Charset charset, byte[] bom) {
    }
}
