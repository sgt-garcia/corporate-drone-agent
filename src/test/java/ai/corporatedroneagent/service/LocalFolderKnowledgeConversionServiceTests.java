package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class LocalFolderKnowledgeConversionServiceTests {

    private final DocumentTextExtractor extractor = mock(DocumentTextExtractor.class);
    private final LocalFolderKnowledgeConversionService service =
            new LocalFolderKnowledgeConversionService(extractor);

    @Test
    void decodesTextFormatsAsUtf8WithoutTheExtractor() {
        ReadResult read = read("md", "# Hello".getBytes(StandardCharsets.UTF_8));

        ConversionResult result = service.convert(read);

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("# Hello");
        verifyNoInteractions(extractor);
    }

    @Test
    void routesDocumentFormatsToTheExtractor() {
        when(extractor.extract(any())).thenReturn("Extracted body");
        ReadResult read = read("pdf", new byte[] {1, 2, 3});

        ConversionResult result = service.convert(read);

        assertThat(result.success()).isTrue();
        assertThat(result.text()).isEqualTo("Extracted body");
        verify(extractor).extract(read.value());
    }

    @Test
    void mapsExtractionFailureToATypedReason() {
        when(extractor.extract(any()))
                .thenThrow(new DocumentTextExtractor.ExtractionException("boom", new RuntimeException()));
        ReadResult read = read("docx", new byte[] {1});

        ConversionResult result = service.convert(read);

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).isEqualTo(KnowledgePipelineReason.DOCUMENT_EXTRACTION_FAILED);
        assertThat(result.text()).isEmpty();
    }

    @Test
    void mapsInvalidUtf8ToATypedReason() {
        ReadResult read = read("txt", new byte[] {(byte) 0xff, (byte) 0xfe, (byte) 0xfd});

        ConversionResult result = service.convert(read);

        assertThat(result.success()).isFalse();
        assertThat(result.reason()).isEqualTo(KnowledgePipelineReason.UTF8_DECODE_FAILED);
    }

    private ReadResult read(String format, byte[] value) {
        return ReadResult.of("ref", "name", format, value.length, Instant.now(), value);
    }
}
