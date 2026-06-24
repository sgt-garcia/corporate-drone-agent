package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KnowledgeFileFormatsTests {

    @Test
    void classifiesTextFormatsCaseInsensitively() {
        assertThat(KnowledgeFileFormats.isText("md")).isTrue();
        assertThat(KnowledgeFileFormats.isText("GO")).isTrue();
        assertThat(KnowledgeFileFormats.isText("toml")).isTrue();
        assertThat(KnowledgeFileFormats.isDocument("md")).isFalse();
        assertThat(KnowledgeFileFormats.isSupported("yaml")).isTrue();
    }

    @Test
    void classifiesDocumentFormatsCaseInsensitively() {
        assertThat(KnowledgeFileFormats.isDocument("pdf")).isTrue();
        assertThat(KnowledgeFileFormats.isDocument("DOCX")).isTrue();
        assertThat(KnowledgeFileFormats.isDocument("xlsx")).isTrue();
        assertThat(KnowledgeFileFormats.isText("pdf")).isFalse();
        assertThat(KnowledgeFileFormats.isSupported("pptx")).isTrue();
    }

    @Test
    void rejectsUnknownAndBlankFormats() {
        assertThat(KnowledgeFileFormats.isSupported("bin")).isFalse();
        assertThat(KnowledgeFileFormats.isSupported("")).isFalse();
        assertThat(KnowledgeFileFormats.isSupported(null)).isFalse();
    }
}
