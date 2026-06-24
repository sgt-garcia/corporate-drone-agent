package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalFolderKnowledgeReadServiceTests {

    private final LocalFolderKnowledgeReadService service = new LocalFolderKnowledgeReadService();

    @TempDir
    Path tempDir;

    @Test
    void documentFormatsGetTheLargerSizeBudget() throws IOException {
        // 2 MB exceeds the 1 MB text cap but stays under the 25 MB document cap. The byte
        // count comes from the resource metadata, so the on-disk fixture can stay tiny.
        long twoMegabytes = 2L * 1024 * 1024;
        Path file = Files.write(tempDir.resolve("report.pdf"), new byte[] {1, 2, 3});

        ReadResult document = service.read(resource("report.pdf", "pdf", twoMegabytes), file);
        ReadResult text = service.read(resource("report.txt", "txt", twoMegabytes), file);

        assertThat(document.success()).isTrue();
        assertThat(text.success()).isFalse();
        assertThat(text.reason()).isEqualTo(KnowledgePipelineReason.FILE_TOO_LARGE);
        assertThat(text.message()).isEqualTo("File is larger than 1 MB");
    }

    @Test
    void rejectsDocumentsOverTheDocumentCap() throws IOException {
        Path file = Files.write(tempDir.resolve("huge.pdf"), new byte[] {1});
        long overCap = LocalFolderKnowledgeReadService.MAX_DOCUMENT_READ_BYTES + 1;

        ReadResult read = service.read(resource("huge.pdf", "pdf", overCap), file);

        assertThat(read.success()).isFalse();
        assertThat(read.reason()).isEqualTo(KnowledgePipelineReason.FILE_TOO_LARGE);
        assertThat(read.message()).isEqualTo("File is larger than 25 MB");
    }

    @Test
    void rejectsUnsupportedFormats() throws IOException {
        Path file = Files.write(tempDir.resolve("blob.bin"), new byte[] {1});

        ReadResult read = service.read(resource("blob.bin", "bin", 1), file);

        assertThat(read.success()).isFalse();
        assertThat(read.reason()).isEqualTo(KnowledgePipelineReason.UNSUPPORTED_FILE_FORMAT);
    }

    private KnowledgeResource resource(String reference, String format, long sizeBytes) {
        KnowledgeResource resource = new KnowledgeResource();
        resource.setReference(reference);
        resource.setDisplayName(reference);
        resource.setFormat(format);
        resource.setSizeBytes(sizeBytes);
        resource.setLastModifiedAt(Instant.now());
        return resource;
    }
}
