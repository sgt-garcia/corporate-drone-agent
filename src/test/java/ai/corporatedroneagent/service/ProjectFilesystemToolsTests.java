package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ai.corporatedroneagent.model.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFilesystemToolsTests {

    @TempDir
    Path temporaryDirectory;

    @Test
    void readTextFileResolvesVirtualRootToProjectWorkingFolder() throws Exception {
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "alpha\nbravo\ncharlie\n");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.readTextFile("/notes.txt", null, 2);

        assertThat(content.content()).isEqualTo("alpha\nbravo");
    }

    @Test
    void readFileAliasDelegatesToReadTextFile() throws Exception {
        Files.writeString(temporaryDirectory.resolve("notes.txt"), "alpha\nbravo\ncharlie\n");
        ProjectFilesystemTools tools = new ProjectFilesystemTools(project(temporaryDirectory));

        ProjectFilesystemTools.ToolContent content = tools.readFile("notes.txt", 1, null);

        assertThat(content.content()).isEqualTo("charlie");
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

    private Project project(Path workingFolder) {
        Project project = new Project();
        project.setWorkingFolder(workingFolder.toString());
        return project;
    }
}
