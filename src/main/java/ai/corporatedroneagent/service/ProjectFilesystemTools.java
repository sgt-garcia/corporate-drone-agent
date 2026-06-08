package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.Project;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

class ProjectFilesystemTools {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME
            .withZone(ZoneId.systemDefault());

    private final String workingFolder;

    ProjectFilesystemTools(Project project) {
        this.workingFolder = project == null ? "" : project.getWorkingFolder();
    }

    @Tool(
            name = "read_file",
            description = "Read the complete contents of a file as text. DEPRECATED: Use read_text_file instead. Paths are relative to the current project working folder, which appears as /."
    )
    public ToolContent readFile(
            @ToolParam(description = "File path under /. Example: /README.md") String path,
            @ToolParam(required = false, description = "If provided, returns only the last N lines of the file") Integer tail,
            @ToolParam(required = false, description = "If provided, returns only the first N lines of the file") Integer head
    ) {
        return readTextFile(path, tail, head);
    }

    @Tool(
            name = "read_text_file",
            description = "Read the complete contents of a file from the file system as text. Use the head parameter to read only the first N lines of a file, or the tail parameter to read only the last N lines of a file. Operates on the file as text regardless of extension. Only works within the current project working folder, which appears as /."
    )
    public ToolContent readTextFile(
            @ToolParam(description = "File path under /. Example: /README.md") String path,
            @ToolParam(required = false, description = "If provided, returns only the last N lines of the file") Integer tail,
            @ToolParam(required = false, description = "If provided, returns only the first N lines of the file") Integer head
    ) {
        validateLineWindow(head, tail);
        Path file = existingFile(path);
        return new ToolContent(window(readText(file), head, tail));
    }

    @Tool(
            name = "read_media_file",
            description = "Read an image or audio file. Returns the base64 encoded data and MIME type. Only works within the current project working folder, which appears as /."
    )
    public MediaToolContent readMediaFile(
            @ToolParam(description = "Media file path under /. Example: /assets/logo.png") String path
    ) {
        Path file = existingFile(path);
        try {
            String mimeType = Files.probeContentType(file);
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "application/octet-stream";
            }
            String type = mediaType(mimeType);
            String data = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
            return new MediaToolContent(List.of(new MediaContent(type, data, mimeType)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read media file: " + virtualPath(file), exception);
        }
    }

    @Tool(
            name = "read_multiple_files",
            description = "Read the contents of multiple text files simultaneously. Each file's content is returned with its virtual path as a reference. Failed reads for individual files won't stop the operation. Only works within the current project working folder, which appears as /."
    )
    public ToolContent readMultipleFiles(
            @ToolParam(description = "Array of file paths under /.") List<String> paths
    ) {
        if (paths == null || paths.isEmpty()) {
            throw new IllegalArgumentException("paths must contain at least one path.");
        }

        List<String> parts = new ArrayList<>();
        for (String path : paths) {
            String label = displayPath(path);
            try {
                Path file = existingFile(path);
                label = virtualPath(file);
                parts.add(label + "\n```\n" + readText(file) + "\n```");
            } catch (RuntimeException exception) {
                parts.add(label + "\nERROR: " + exception.getMessage());
            }
        }
        return new ToolContent(String.join("\n\n", parts));
    }

    @Tool(
            name = "write_file",
            description = "Create a new file or completely overwrite an existing file with new text content. Only works within the current project working folder, which appears as /."
    )
    public ToolContent writeFile(
            @ToolParam(description = "File path under /. Example: /notes/todo.txt") String path,
            @ToolParam(description = "Text content to write using UTF-8") String content
    ) {
        Path file = writablePath(path);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
            return new ToolContent("Successfully wrote " + virtualPath(file) + ".");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write file: " + virtualPath(file), exception);
        }
    }

    @Tool(
            name = "edit_file",
            description = "Make line-based edits to a text file. Each edit replaces exact text with new content. Returns a git-style diff showing the changes made. Only works within the current project working folder, which appears as /."
    )
    public ToolContent editFile(
            @ToolParam(description = "File path under /. Example: /src/App.jsx") String path,
            @ToolParam(description = "Exact text replacements to apply in order") List<FileEdit> edits,
            @ToolParam(required = false, description = "Preview changes using git-style diff format") Boolean dryRun
    ) {
        if (edits == null || edits.isEmpty()) {
            throw new IllegalArgumentException("edits must contain at least one edit.");
        }

        Path file = existingFile(path);
        String original = readText(file);
        String updated = original;
        for (FileEdit edit : edits) {
            if (edit == null || edit.oldText() == null || edit.newText() == null) {
                throw new IllegalArgumentException("Each edit requires oldText and newText.");
            }
            int index = updated.indexOf(edit.oldText());
            if (index < 0) {
                throw new IllegalArgumentException("oldText was not found in " + virtualPath(file) + ".");
            }
            updated = updated.substring(0, index) + edit.newText() + updated.substring(index + edit.oldText().length());
        }

        String diff = diff(virtualPath(file), original, updated);
        if (!Boolean.TRUE.equals(dryRun)) {
            try {
                Files.writeString(file, updated, StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not edit file: " + virtualPath(file), exception);
            }
        }
        return new ToolContent(diff);
    }

    @Tool(
            name = "create_directory",
            description = "Create a new directory or ensure a directory exists. Can create multiple nested directories in one operation. Only works within the current project working folder, which appears as /."
    )
    public ToolContent createDirectory(
            @ToolParam(description = "Directory path under /. Example: /docs/plans") String path
    ) {
        Path directory = writablePath(path);
        try {
            Files.createDirectories(directory);
            return new ToolContent("Successfully created directory " + virtualPath(directory) + ".");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create directory: " + virtualPath(directory), exception);
        }
    }

    @Tool(
            name = "list_directory",
            description = "Get a detailed listing of all files and directories in a specified path. Results distinguish files and directories with [FILE] and [DIR] prefixes. Only works within the current project working folder, which appears as /."
    )
    public ToolContent listDirectory(
            @ToolParam(description = "Directory path under /. Example: /src") String path
    ) {
        return new ToolContent(String.join("\n", directoryEntries(existingDirectory(path)).stream()
                .map(entry -> prefix(entry) + " " + entry.getFileName())
                .toList()));
    }

    @Tool(
            name = "list_directory_with_sizes",
            description = "Get a detailed listing of all files and directories in a specified path, including sizes. Results distinguish files and directories with [FILE] and [DIR] prefixes. Only works within the current project working folder, which appears as /."
    )
    public ToolContent listDirectoryWithSizes(
            @ToolParam(description = "Directory path under /. Example: /src") String path,
            @ToolParam(required = false, description = "Sort entries by name or size") String sortBy
    ) {
        List<Path> entries = directoryEntries(existingDirectory(path));
        Comparator<Path> comparator = "size".equalsIgnoreCase(sortBy)
                ? Comparator.comparingLong(this::size).reversed().thenComparing(this::fileNameLower)
                : Comparator.comparing(this::fileNameLower);
        entries = entries.stream().sorted(comparator).toList();
        return new ToolContent(String.join("\n", entries.stream()
                .map(entry -> prefix(entry) + " " + entry.getFileName() + " (" + formatBytes(size(entry)) + ")")
                .toList()));
    }

    @Tool(
            name = "directory_tree",
            description = "Get a recursive tree view of files and directories as a JSON structure. Directories include children arrays; files do not. Only works within the current project working folder, which appears as /."
    )
    public ToolContent directoryTree(
            @ToolParam(description = "Directory or file path under /. Example: /src") String path,
            @ToolParam(required = false, description = "Glob patterns to exclude, relative to /") List<String> excludePatterns
    ) {
        Path rootPath = existingPath(path);
        try {
            return new ToolContent(OBJECT_MAPPER.writeValueAsString(treeNode(rootPath, excludePatterns)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not build directory tree for: " + virtualPath(rootPath), exception);
        }
    }

    @Tool(
            name = "move_file",
            description = "Move or rename files and directories. If the destination exists, the operation fails. Both source and destination must be under /."
    )
    public ToolContent moveFile(
            @ToolParam(description = "Source path under /.") String source,
            @ToolParam(description = "Destination path under /.") String destination
    ) {
        Path sourcePath = existingPath(source);
        Path destinationPath = writablePath(destination);
        if (Files.exists(destinationPath)) {
            throw new IllegalArgumentException("Destination already exists: " + displayPath(destination));
        }

        try {
            Path parent = destinationPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(sourcePath, destinationPath, StandardCopyOption.ATOMIC_MOVE);
            return new ToolContent("Successfully moved " + displayPath(source) + " to " + virtualPath(destinationPath) + ".");
        } catch (FileAlreadyExistsException exception) {
            throw new IllegalArgumentException("Destination already exists: " + displayPath(destination), exception);
        } catch (IOException exception) {
            try {
                Files.move(sourcePath, destinationPath);
                return new ToolContent("Successfully moved " + displayPath(source) + " to " + virtualPath(destinationPath) + ".");
            } catch (IOException fallbackException) {
                throw new UncheckedIOException("Could not move " + displayPath(source) + " to " + displayPath(destination), fallbackException);
            }
        }
    }

    @Tool(
            name = "search_files",
            description = "Recursively search for files and directories matching a glob-style pattern relative to /. Use *.ext for current-directory matches and **/*.ext for all subdirectories. Only searches within the current project working folder, which appears as /."
    )
    public ToolContent searchFiles(
            @ToolParam(description = "Directory path under / to start searching from") String path,
            @ToolParam(description = "Glob-style pattern relative to /. Example: **/*.java") String pattern,
            @ToolParam(required = false, description = "Glob patterns to exclude, relative to /") List<String> excludePatterns
    ) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("pattern is required.");
        }

        Path start = existingDirectory(path);
        Pattern include = globPattern(pattern);
        List<String> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(candidate -> !candidate.equals(start))
                    .filter(candidate -> matches(include, candidate)
                            || matches(include, start.relativize(candidate).toString()))
                    .filter(candidate -> !isExcluded(candidate, excludePatterns))
                    .sorted(Comparator.comparing(this::virtualPath))
                    .map(this::virtualPath)
                    .forEach(results::add);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not search files under: " + virtualPath(start), exception);
        }
        return new ToolContent(String.join("\n", results));
    }

    @Tool(
            name = "get_file_info",
            description = "Retrieve detailed metadata about a file or directory. Returns size, creation time, last modified time, permissions, and type. Only works within the current project working folder, which appears as /."
    )
    public ToolContent getFileInfo(
            @ToolParam(description = "File or directory path under /. Example: /README.md") String path
    ) {
        Path file = existingPath(path);
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            List<String> lines = new ArrayList<>();
            lines.add("Path: " + virtualPath(file));
            lines.add("Type: " + fileType(attributes));
            lines.add("Size: " + attributes.size() + " bytes");
            lines.add("Created: " + formatTime(attributes.creationTime()));
            lines.add("Modified: " + formatTime(attributes.lastModifiedTime()));
            lines.add("Accessed: " + formatTime(attributes.lastAccessTime()));
            lines.add("Readable: " + Files.isReadable(file));
            lines.add("Writable: " + Files.isWritable(file));
            lines.add("Executable: " + Files.isExecutable(file));
            return new ToolContent(String.join("\n", lines));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read file info: " + virtualPath(file), exception);
        }
    }

    @Tool(
            name = "list_allowed_directories",
            description = "Returns the list of directories this tool can access. The current project working folder appears to the agent as /."
    )
    public ToolContent listAllowedDirectories() {
        root();
        return new ToolContent("/");
    }

    private Path existingFile(String path) {
        Path file = existingPath(path);
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Path is not a file: " + displayPath(path));
        }
        return file;
    }

    private Path existingDirectory(String path) {
        Path directory = existingPath(path);
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + displayPath(path));
        }
        return directory;
    }

    private Path existingPath(String path) {
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

    private Path writablePath(String path) {
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

    private Path nearestExistingAncestor(Path path) {
        Path current = Files.isDirectory(path) ? path : path.getParent();
        while (current != null && !Files.exists(current)) {
            current = current.getParent();
        }
        return current;
    }

    private Path root() {
        if (workingFolder == null || workingFolder.isBlank()) {
            throw new IllegalStateException("Project working folder is not configured.");
        }

        try {
            Path root = Path.of(workingFolder).toRealPath();
            if (!Files.isDirectory(root)) {
                throw new IllegalStateException("Project working folder is not a directory.");
            }
            return root;
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

    private List<Path> directoryEntries(Path directory) {
        try (Stream<Path> stream = Files.list(directory)) {
            return stream.sorted(Comparator.comparing(this::fileNameLower)).toList();
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not list directory: " + virtualPath(directory), exception);
        }
    }

    private TreeNode treeNode(Path path, List<String> excludePatterns) throws IOException {
        if (Files.isDirectory(path)) {
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

    private boolean isExcluded(Path candidate, List<String> excludePatterns) {
        if (excludePatterns == null || excludePatterns.isEmpty()) {
            return false;
        }
        return excludePatterns.stream()
                .filter(Objects::nonNull)
                .filter(pattern -> !pattern.isBlank())
                .map(this::globPattern)
                .anyMatch(pattern -> matches(pattern, candidate));
    }

    private boolean matches(Pattern pattern, Path candidate) {
        String virtualPath = virtualPath(candidate).substring(1);
        return pattern.matcher(virtualPath).matches();
    }

    private boolean matches(Pattern pattern, String relativePath) {
        return pattern.matcher(relativePath.replace('\\', '/')).matches();
    }

    private Pattern globPattern(String glob) {
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

    private String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read file: " + virtualPath(file), exception);
        }
    }

    private void validateLineWindow(Integer head, Integer tail) {
        if (head != null && tail != null) {
            throw new IllegalArgumentException("Use either head or tail, not both.");
        }
        if (head != null && head < 0) {
            throw new IllegalArgumentException("head must be greater than or equal to 0.");
        }
        if (tail != null && tail < 0) {
            throw new IllegalArgumentException("tail must be greater than or equal to 0.");
        }
    }

    private String window(String content, Integer head, Integer tail) {
        if (head == null && tail == null) {
            return content;
        }

        List<String> lines = content.lines().toList();
        if (head != null) {
            return String.join("\n", lines.stream().limit(head).toList());
        }

        int start = Math.max(0, lines.size() - tail);
        return String.join("\n", lines.subList(start, lines.size()));
    }

    private String diff(String path, String original, String updated) {
        if (original.equals(updated)) {
            return "No changes.";
        }

        return "--- " + path + "\n"
                + "+++ " + path + "\n"
                + "@@\n"
                + prefixedLines("-", original)
                + prefixedLines("+", updated);
    }

    private String prefixedLines(String prefix, String content) {
        if (content.isEmpty()) {
            return "";
        }
        String normalized = content.endsWith("\n") ? content.substring(0, content.length() - 1) : content;
        return normalized.lines()
                .map(line -> prefix + line)
                .reduce("", (left, right) -> left + right + "\n");
    }

    private String mediaType(String mimeType) {
        if (mimeType.startsWith("image/")) {
            return "image";
        }
        if (mimeType.startsWith("audio/")) {
            return "audio";
        }
        return "blob";
    }

    private String prefix(Path path) {
        return Files.isDirectory(path) ? "[DIR]" : "[FILE]";
    }

    private long size(Path path) {
        try {
            return Files.isDirectory(path) ? directorySize(path) : Files.size(path);
        } catch (IOException exception) {
            return 0L;
        }
    }

    private long directorySize(Path directory) throws IOException {
        try (Stream<Path> stream = Files.walk(directory)) {
            return stream.filter(Files::isRegularFile)
                    .mapToLong(path -> {
                        try {
                            return Files.size(path);
                        } catch (IOException exception) {
                            return 0L;
                        }
                    })
                    .sum();
        }
    }

    private String fileType(BasicFileAttributes attributes) {
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

    private String formatTime(FileTime time) {
        return time == null ? "" : TIME_FORMATTER.format(time.toInstant());
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kib = bytes / 1024.0;
        if (kib < 1024) {
            return String.format(Locale.ROOT, "%.1f KB", kib);
        }
        return String.format(Locale.ROOT, "%.1f MB", kib / 1024.0);
    }

    private String fileNameLower(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString().toLowerCase(Locale.ROOT);
    }

    private String virtualPath(Path file) {
        Path root = root();
        Path normalized = file.normalize();
        Path relative = root.relativize(normalized);
        String path = relative.toString().replace('\\', '/');
        return path.isEmpty() ? "/" : "/" + path;
    }

    private String displayPath(String path) {
        return path == null || path.isBlank() ? "<blank>" : path.trim();
    }

    public record ToolContent(String content) {
    }

    public record MediaToolContent(List<MediaContent> content) {
    }

    public record MediaContent(String type, String data, String mimeType) {
    }

    public record FileEdit(String oldText, String newText) {
    }

    private record TreeNode(String name, String type, List<TreeNode> children) {
    }
}
