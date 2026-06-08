package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.corporatedroneagent.model.Project;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystemException;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;

class ProjectFilesystemToolsTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void annotatedToolNamesMatchFilesystemSchema() {
        Set<String> toolNames = java.util.Arrays.stream(ProjectFilesystemTools.class.getDeclaredMethods())
                .map(method -> method.getAnnotation(Tool.class))
                .filter(annotation -> annotation != null)
                .map(Tool::name)
                .collect(Collectors.toSet());

        assertThat(toolNames).containsExactlyInAnyOrder(
                "read_file",
                "read_text_file",
                "read_media_file",
                "read_multiple_files",
                "write_file",
                "edit_file",
                "create_directory",
                "list_directory",
                "list_directory_with_sizes",
                "directory_tree",
                "move_file",
                "search_files",
                "get_file_info",
                "list_allowed_directories"
        );
    }

    @Test
    void readTextFileResolvesVirtualRootToProjectWorkingFolder() throws Exception {
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "alpha\nbravo\ncharlie\n");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.readTextFile("/notes.txt", null, 2);

        assertThat(content.content()).isEqualTo("alpha\nbravo");
    }

    @Test
    void readTextFileHandlesUtf16Text() throws Exception {
        Files.writeString(temporaryDirectory.resolve("utf16.txt"), "alpha\nbravo\n", StandardCharsets.UTF_16);
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.readTextFile("/utf16.txt", null, null);

        assertThat(content.content()).isEqualTo("alpha\nbravo\n");
    }

    @Test
    void readTextFileFallsBackToWindows1252Text() throws Exception {
        Files.writeString(temporaryDirectory.resolve("legacy.txt"), "café", Charset.forName("windows-1252"));
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.readTextFile("/legacy.txt", null, null);

        assertThat(content.content()).isEqualTo("café");
    }

    @Test
    void readFileAliasDelegatesToReadTextFile() throws Exception {
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "alpha\nbravo\ncharlie\n");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.readFile("notes.txt", 1, null);

        assertThat(content.content()).isEqualTo("charlie");
    }

    @Test
    void readTextFileCanBeInvokedThroughSpringAiToolCallback() throws Exception {
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "alpha\nbravo\n");
        ToolCallback callback = callback("read_text_file", new ProjectFilesystemTools(project(temporaryDirectory)));

        String result = callback.call("""
                {"path":"/notes.txt","head":1}
                """);

        assertThat(result)
                .contains("content")
                .contains("alpha")
                .doesNotContain("bravo");
    }

    @Test
    void readTextFileRejectsTraversalOutsideProjectWorkingFolder() throws Exception {
        Path outside = Files.createTempFile("outside-project", ".txt");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        assertThatThrownBy(() -> tools.readTextFile("/../" + outside.getFileName(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside the project working folder");
    }

    @Test
    void readTextFileRejectsLocalAbsolutePaths() {
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        assertThatThrownBy(() -> tools.readTextFile(temporaryDirectory.resolve("notes.txt").toString(), null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Use paths under /");
    }

    @Test
    void readTextFileRequiresConfiguredWorkingFolder() {
        Project project = new Project();
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project);

        assertThatThrownBy(() -> tools.readTextFile("/notes.txt", null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Project working folder is not configured");
    }

    @Test
    void readMediaFileReturnsBase64MimePayload() throws Exception {
        byte[] bytes = Base64.getDecoder().decode("iVBORw0KGgo=");
        Files.write(temporaryDirectory.resolve("logo.png"), bytes);
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.MediaToolContent content = tools.readMediaFile("/logo.png");

        assertThat(content.content()).hasSize(1);
        assertThat(content.content().getFirst().type()).isEqualTo("image");
        assertThat(content.content().getFirst().data()).isEqualTo(Base64.getEncoder().encodeToString(bytes));
        assertThat(content.content().getFirst().mimeType()).contains("png");
    }

    @Test
    void readMultipleFilesIncludesFailuresWithoutStopping() throws Exception {
        Files.writeString(temporaryDirectory.resolve("a.txt"), "alpha");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.readMultipleFiles(List.of("/a.txt", "/missing.txt"));

        assertThat(content.content())
                .contains("/a.txt")
                .contains("alpha")
                .contains("/missing.txt")
                .contains("ERROR:");
    }

    @Test
    void writeFileCreatesParentDirectoriesAndOverwritesText() throws Exception {
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        tools.writeFile("/docs/notes.txt", "alpha");
        tools.writeFile("/docs/notes.txt", "bravo");

        assertThat(Files.readString(temporaryDirectory.resolve("docs").resolve("notes.txt"))).isEqualTo("bravo");
    }

    @Test
    void writeFileRejectsExistingSymlinkDestination() throws Exception {
        Path outside = Files.createTempFile("outside-project", ".txt");
        Files.writeString(outside, "outside");
        Path symlink = temporaryDirectory.resolve("linked.txt");
        try {
            Files.createSymbolicLink(symlink, outside);
        } catch (UnsupportedOperationException | FileSystemException | SecurityException exception) {
            Assumptions.abort("Symbolic links are not available in this test environment.");
        }
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        assertThatThrownBy(() -> tools.writeFile("/linked.txt", "escaped"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Refusing to write through symbolic link");
        assertThat(Files.readString(outside)).isEqualTo("outside");
    }

    @Test
    void editFileCanDryRunAndThenApplyReplacements() throws Exception {
        Path file = temporaryDirectory.resolve("notes.txt");
        Files.writeString(file, "alpha\nbravo\n");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent dryRun = tools.editFile(
                "/notes.txt",
                List.of(new ProjectFilesystemTools.FileEdit("bravo", "charlie")),
                true
        );

        assertThat(dryRun.content())
                .contains("--- /notes.txt")
                .contains("-alpha")
                .contains("+alpha")
                .contains("+charlie");
        assertThat(Files.readString(file)).isEqualTo("alpha\nbravo\n");

        tools.editFile(
                "/notes.txt",
                List.of(new ProjectFilesystemTools.FileEdit("bravo", "charlie")),
                false
        );
        assertThat(Files.readString(file)).isEqualTo("alpha\ncharlie\n");
    }

    @Test
    void editFileDoesNotMatchInlineSubstrings() throws Exception {
        Path file = temporaryDirectory.resolve("notes.txt");
        Files.writeString(file, "alpha bravo\n");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        assertThatThrownBy(() -> tools.editFile(
                "/notes.txt",
                List.of(new ProjectFilesystemTools.FileEdit("bravo", "charlie")),
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("oldText line sequence was not found");
        assertThat(Files.readString(file)).isEqualTo("alpha bravo\n");
    }

    @Test
    void editFileRejectsAmbiguousDuplicateLineSequences() throws Exception {
        Path file = temporaryDirectory.resolve("notes.txt");
        Files.writeString(file, "target\nmiddle\ntarget\n");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        assertThatThrownBy(() -> tools.editFile(
                "/notes.txt",
                List.of(new ProjectFilesystemTools.FileEdit("target", "replacement")),
                false
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("matched more than once");
        assertThat(Files.readString(file)).isEqualTo("target\nmiddle\ntarget\n");
    }

    @Test
    void editFileCanBeInvokedThroughSpringAiToolCallbackWithEditArrayBinding() throws Exception {
        Path file = temporaryDirectory.resolve("notes.txt");
        Files.writeString(file, "alpha\nbravo\n");
        ToolCallback callback = callback("edit_file", new ProjectFilesystemTools(project(temporaryDirectory)));

        assertThat(callback.getToolDefinition().inputSchema())
                .contains("edits")
                .contains("oldText")
                .contains("newText");

        String result = callback.call("""
                {
                  "path": "/notes.txt",
                  "edits": [
                    {
                      "oldText": "bravo",
                      "newText": "charlie"
                    }
                  ],
                  "dryRun": false
                }
                """);

        assertThat(result)
                .contains("content")
                .contains("--- /notes.txt")
                .contains("+charlie");
        assertThat(Files.readString(file)).isEqualTo("alpha\ncharlie\n");
    }

    @Test
    void createDirectoryIsIdempotentForNestedDirectories() {
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        tools.createDirectory("/alpha/bravo");
        tools.createDirectory("/alpha/bravo");

        assertThat(temporaryDirectory.resolve("alpha").resolve("bravo")).isDirectory();
    }

    @Test
    void listDirectoryDistinguishesFilesAndDirectories() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("docs"));
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "alpha");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.listDirectory("/");

        assertThat(content.content())
                .contains("[DIR] docs")
                .contains("[FILE] notes.txt");
    }

    @Test
    void listDirectoryWithSizesCanSortBySize() throws Exception {
        Files.writeString(temporaryDirectory.resolve("small.txt"), "a");
        Files.writeString(temporaryDirectory.resolve("large.txt"), "abcdef");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.listDirectoryWithSizes("/", "size");

        assertThat(content.content().lines().toList().getFirst()).contains("large.txt").contains("6 B");
    }

    @Test
    void directoryTreeReturnsJsonAndHonorsExcludes() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("src"));
        Files.writeString(temporaryDirectory.resolve("src").resolve("App.java"), "class App {}");
        Files.writeString(temporaryDirectory.resolve("src").resolve("App.class"), "compiled");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.directoryTree("/", List.of("**/*.class"));

        assertThat(content.content())
                .contains("\"name\" : \"/\"")
                .contains("\"name\" : \"src\"")
                .contains("\"name\" : \"App.java\"")
                .doesNotContain("App.class")
                .doesNotContain(temporaryDirectory.getFileName().toString());
    }

    @Test
    void directoryTreeDoesNotDescendIntoSymlinkedDirectories() throws Exception {
        Path outsideDirectory = Files.createTempDirectory("outside-project");
        Files.writeString(outsideDirectory.resolve("secret.txt"), "outside");
        Path symlink = temporaryDirectory.resolve("linked");
        try {
            Files.createSymbolicLink(symlink, outsideDirectory);
        } catch (UnsupportedOperationException | FileSystemException | SecurityException exception) {
            Assumptions.abort("Symbolic links are not available in this test environment.");
        }
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.directoryTree("/", List.of());

        assertThat(content.content())
                .contains("\"name\" : \"linked\"")
                .contains("\"type\" : \"file\"")
                .doesNotContain("secret.txt");
    }

    @Test
    void moveFileFailsWhenDestinationExists() throws Exception {
        Files.writeString(temporaryDirectory.resolve("source.txt"), "source");
        Files.writeString(temporaryDirectory.resolve("destination.txt"), "destination");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        assertThatThrownBy(() -> tools.moveFile("/source.txt", "/destination.txt"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Destination already exists");
    }

    @Test
    void moveFileMovesFilesWithinVirtualRoot() throws Exception {
        Files.writeString(temporaryDirectory.resolve("source.txt"), "source");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        tools.moveFile("/source.txt", "/nested/destination.txt");

        assertThat(temporaryDirectory.resolve("source.txt")).doesNotExist();
        assertThat(temporaryDirectory.resolve("nested").resolve("destination.txt")).hasContent("source");
    }

    @Test
    void searchFilesMatchesGlobPatternsAndExcludesMatches() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("src"));
        Files.writeString(temporaryDirectory.resolve("src").resolve("App.java"), "class App {}");
        Files.writeString(temporaryDirectory.resolve("src").resolve("Generated.java"), "class Generated {}");
        Files.writeString(temporaryDirectory.resolve("src").resolve("notes.txt"), "notes");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.searchFiles("/src", "*.java", List.of("**/Generated.java"));

        assertThat(content.content())
                .contains("/src/App.java")
                .doesNotContain("Generated.java")
                .doesNotContain("notes.txt");
    }

    @Test
    void getFileInfoReturnsVirtualPathAndMetadata() throws Exception {
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "alpha");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.getFileInfo("/notes.txt");

        assertThat(content.content())
                .contains("Path: /notes.txt")
                .contains("Type: file")
                .contains("Size: 5 bytes")
                .contains("Readable:");
    }

    @Test
    void listAllowedDirectoriesOnlyShowsVirtualRoot() {
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        assertThat(tools.listAllowedDirectories().content()).isEqualTo("/");
    }

    private Project project(Path workingFolder) {
        Project project = new Project();
        project.setWorkingFolder(workingFolder.toString());
        return project;
    }

    private ToolCallback callback(String name, ProjectFilesystemTools tools) {
        return java.util.Arrays.stream(MethodToolCallbackProvider.builder()
                        .toolObjects(tools)
                        .build()
                        .getToolCallbacks())
                .filter(callback -> callback.getToolDefinition().name().equals(name))
                .findFirst()
                .orElseThrow();
    }
}
