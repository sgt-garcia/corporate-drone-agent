package ai.corporatedroneagent.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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
