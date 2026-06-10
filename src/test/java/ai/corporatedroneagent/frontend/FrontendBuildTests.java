package ai.corporatedroneagent.frontend;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class FrontendBuildTests {

    @Test
    void frontendBuildScriptProducesPackagedStaticAssets() throws Exception {
        Path frontendPackage = Path.of("frontend", "package.json");
        JsonNode packageJson = new ObjectMapper().readTree(frontendPackage.toFile());

        assertThat(packageJson.path("scripts").path("build").asText()).isEqualTo("vite build");
        assertThat(Files.readString(Path.of("pom.xml")))
                .contains("<arguments>run build</arguments>");

        Path distIndex = Path.of("frontend", "dist", "index.html");
        Path packagedIndex = Path.of("target", "classes", "static", "index.html");
        assertThat(distIndex).exists().isRegularFile();
        assertThat(packagedIndex).exists().isRegularFile();
        assertThat(Files.readString(distIndex)).contains("type=\"module\"");
        assertThat(Files.readString(packagedIndex)).contains("type=\"module\"");
    }
}
