package ai.corporatedroneagent.tools;

import ai.corporatedroneagent.model.Project;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * The MCP {@code @Tool} surface over a project's working folder, which it presents to the agent as
 * {@code /}. Each tool validates its inputs, delegates the filesystem mechanics to
 * {@link SandboxedFilesystem}, and wraps the result in the tool-output records below.
 */
public class ProjectFilesystemTools {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final SandboxedFilesystem fs;

    public ProjectFilesystemTools(Project project) {
        this.fs = new SandboxedFilesystem(project == null ? "" : project.getWorkingFolder());
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
        Path file = fs.existingFile(path);
        return new ToolContent(window(fs.readText(file), head, tail));
    }

    @Tool(
            name = "read_media_file",
            description = "Read an image or audio file. Returns the base64 encoded data and MIME type. Only works within the current project working folder, which appears as /."
    )
    public MediaToolContent readMediaFile(
            @ToolParam(description = "Media file path under /. Example: /assets/logo.png") String path
    ) {
        Path file = fs.existingFile(path);
        try {
            String mimeType = Files.probeContentType(file);
            if (mimeType == null || mimeType.isBlank()) {
                mimeType = "application/octet-stream";
            }
            String type = fs.mediaType(mimeType);
            String data = Base64.getEncoder().encodeToString(Files.readAllBytes(file));
            return new MediaToolContent(List.of(new MediaContent(type, data, mimeType)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read media file: " + fs.virtualPath(file), exception);
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
            String label = fs.displayPath(path);
            try {
                Path file = fs.existingFile(path);
                label = fs.virtualPath(file);
                parts.add(label + "\n```\n" + fs.readText(file) + "\n```");
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
        Path file = fs.writablePath(path);
        fs.rejectExistingSymlink(file, path);
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(
                    file,
                    content == null ? "" : content,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE,
                    LinkOption.NOFOLLOW_LINKS
            );
            return new ToolContent("Successfully wrote " + fs.virtualPath(file) + ".");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write file: " + fs.virtualPath(file), exception);
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

        Path file = fs.existingFile(path);
        String virtualPath = fs.virtualPath(file);
        var decoded = fs.readDecodedText(file);
        String original = decoded.content();
        var document = fs.parseDocument(original);
        for (FileEdit edit : edits) {
            if (edit == null || edit.oldText() == null || edit.newText() == null) {
                throw new IllegalArgumentException("Each edit requires oldText and newText.");
            }
            document = fs.applyLineEdit(document, edit.oldText(), edit.newText(), virtualPath);
        }

        String updated = fs.renderDocument(document);
        String diff = diff(virtualPath, original, updated);
        if (!Boolean.TRUE.equals(dryRun)) {
            try {
                Files.write(file, fs.encodeText(updated, decoded), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            } catch (IOException exception) {
                throw new UncheckedIOException("Could not edit file: " + virtualPath, exception);
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
        Path directory = fs.writablePath(path);
        try {
            Files.createDirectories(directory);
            return new ToolContent("Successfully created directory " + fs.virtualPath(directory) + ".");
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not create directory: " + fs.virtualPath(directory), exception);
        }
    }

    @Tool(
            name = "list_directory",
            description = "Get a detailed listing of all files and directories in a specified path. Results distinguish files and directories with [FILE] and [DIR] prefixes. Only works within the current project working folder, which appears as /."
    )
    public ToolContent listDirectory(
            @ToolParam(description = "Directory path under /. Example: /src") String path
    ) {
        return new ToolContent(String.join("\n", fs.directoryEntries(fs.existingDirectory(path)).stream()
                .map(entry -> fs.prefix(entry) + " " + entry.getFileName())
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
        List<Path> entries = fs.directoryEntries(fs.existingDirectory(path));
        Comparator<Path> comparator = "size".equalsIgnoreCase(sortBy)
                ? Comparator.comparingLong(fs::size).reversed().thenComparing(fs::fileNameLower)
                : Comparator.comparing(fs::fileNameLower);
        entries = entries.stream().sorted(comparator).toList();
        return new ToolContent(String.join("\n", entries.stream()
                .map(entry -> fs.prefix(entry) + " " + entry.getFileName() + " (" + fs.formatBytes(fs.size(entry)) + ")")
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
        Path rootPath = fs.existingPath(path);
        try {
            return new ToolContent(OBJECT_MAPPER.writeValueAsString(fs.treeNode(rootPath, excludePatterns)));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not build directory tree for: " + fs.virtualPath(rootPath), exception);
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
        Path sourcePath = fs.existingPath(source);
        Path destinationPath = fs.writablePath(destination);
        if (Files.exists(destinationPath)) {
            throw new IllegalArgumentException("Destination already exists: " + fs.displayPath(destination));
        }

        try {
            Path parent = destinationPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.move(sourcePath, destinationPath, StandardCopyOption.ATOMIC_MOVE);
            return new ToolContent("Successfully moved " + fs.displayPath(source) + " to " + fs.virtualPath(destinationPath) + ".");
        } catch (FileAlreadyExistsException exception) {
            throw new IllegalArgumentException("Destination already exists: " + fs.displayPath(destination), exception);
        } catch (IOException exception) {
            try {
                Files.move(sourcePath, destinationPath);
                return new ToolContent("Successfully moved " + fs.displayPath(source) + " to " + fs.virtualPath(destinationPath) + ".");
            } catch (IOException fallbackException) {
                throw new UncheckedIOException("Could not move " + fs.displayPath(source) + " to " + fs.displayPath(destination), fallbackException);
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

        Path start = fs.existingDirectory(path);
        var include = fs.globPattern(pattern);
        List<String> results = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(start)) {
            stream.filter(candidate -> !candidate.equals(start))
                    .filter(candidate -> fs.matches(include, candidate)
                            || fs.matches(include, start.relativize(candidate).toString()))
                    .filter(candidate -> !fs.isExcluded(candidate, excludePatterns))
                    .sorted(Comparator.comparing(fs::virtualPath))
                    .map(fs::virtualPath)
                    .forEach(results::add);
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not search files under: " + fs.virtualPath(start), exception);
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
        Path file = fs.existingPath(path);
        try {
            BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
            List<String> lines = new ArrayList<>();
            lines.add("Path: " + fs.virtualPath(file));
            lines.add("Type: " + fs.fileType(attributes));
            lines.add("Size: " + attributes.size() + " bytes");
            lines.add("Created: " + fs.formatTime(attributes.creationTime()));
            lines.add("Modified: " + fs.formatTime(attributes.lastModifiedTime()));
            lines.add("Accessed: " + fs.formatTime(attributes.lastAccessTime()));
            lines.add("Readable: " + Files.isReadable(file));
            lines.add("Writable: " + Files.isWritable(file));
            lines.add("Executable: " + Files.isExecutable(file));
            return new ToolContent(String.join("\n", lines));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read file info: " + fs.virtualPath(file), exception);
        }
    }

    @Tool(
            name = "list_allowed_directories",
            description = "Returns the list of directories this tool can access. The current project working folder appears to the agent as /."
    )
    public ToolContent listAllowedDirectories() {
        fs.root();
        return new ToolContent("/");
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

    public record ToolContent(String content) {
    }

    public record MediaToolContent(List<MediaContent> content) {
    }

    public record MediaContent(String type, String data, String mimeType) {
    }

    public record FileEdit(String oldText, String newText) {
    }
}
