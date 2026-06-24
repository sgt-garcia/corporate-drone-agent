package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;

class DocumentTextExtractorTests {

    private final DocumentTextExtractor extractor = new DocumentTextExtractor();

    @Test
    void extractsTextFromARealDocx() throws IOException {
        byte[] docx = docxWithParagraph("Quarterly synergy realignment update");

        String text = extractor.extract(docx);

        assertThat(text).contains("Quarterly synergy realignment update");
    }

    @Test
    void returnsEmptyForNullContent() {
        assertThat(extractor.extract(null)).isEmpty();
    }

    @Test
    void wrapsCorruptDocumentFailuresAsExtractionException() throws IOException {
        // A truncated docx — a valid OOXML header with the rest of the package lopped off, the
        // shape of a real corrupt file. Whatever the parser throws (checked or unchecked), it
        // must surface as the typed ExtractionException, never a raw runtime exception.
        byte[] valid = docxWithParagraph("body");
        byte[] truncated = Arrays.copyOf(valid, valid.length / 2);

        assertThatExceptionOfType(DocumentTextExtractor.ExtractionException.class)
                .isThrownBy(() -> extractor.extract(truncated));
    }

    private byte[] docxWithParagraph(String body) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText(body);
            document.write(out);
            return out.toByteArray();
        }
    }
}
