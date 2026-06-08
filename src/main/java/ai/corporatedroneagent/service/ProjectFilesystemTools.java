package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.Project;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

class ProjectFilesystemTools {

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
        Path file = resolveReadableFile(path);
        validateLineWindow(head, tail);

        try {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return new ToolContent(window(content, head, tail));
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not read file: " + virtualPath(file), exception);
        }
    }

    private Path resolveReadableFile(String path) {
        Path root = root();
        Path resolved = resolveInsideRoot(root, path);
        Path realPath;
        try {
            realPath = resolved.toRealPath();
        } catch (IOException exception) {
            throw new IllegalArgumentException("File does not exist or cannot be accessed: " + displayPath(path));
        }

        if (!realPath.startsWith(root)) {
            throw new IllegalArgumentException("Path is outside the project working folder: " + displayPath(path));
        }
        if (!Files.isRegularFile(realPath)) {
            throw new IllegalArgumentException("Path is not a file: " + displayPath(path));
        }
        return realPath;
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

    private String virtualPath(Path file) {
        Path root = root();
        Path relative = root.relativize(file);
        String path = relative.toString().replace('\\', '/');
        return path.isEmpty() ? "/" : "/" + path;
    }

    private String displayPath(String path) {
        return path == null || path.isBlank() ? "<blank>" : path.trim();
    }

    public record ToolContent(String content) {
    }
}
