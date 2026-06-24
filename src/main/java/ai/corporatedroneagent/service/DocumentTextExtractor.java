package ai.corporatedroneagent.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;

/**
 * Extracts plain text from binary document formats (PDF, Office, OpenDocument, RTF) with
 * Apache Tika. A standalone, source-blind bean: any converter can reuse it, so a PDF from a
 * local folder and a PDF from a future cloud source render the same way. Tika's checked
 * failures are wrapped as {@link ExtractionException} so callers never import Tika types.
 */
@Service
public class DocumentTextExtractor {

    // Tika's facade truncates extracted text at 100k chars by default; lift it to a generous
    // ceiling that still bounds memory for a pathological document.
    static final int MAX_EXTRACTED_CHARS = 5_000_000;

    private final Tika tika;

    public DocumentTextExtractor() {
        this.tika = new Tika();
        this.tika.setMaxStringLength(MAX_EXTRACTED_CHARS);
    }

    /** Detect the format from the bytes and extract its text. Never returns null. */
    public String extract(byte[] content) {
        // Tika treats a zero-byte input as an error; an empty document is simply empty text
        // (the chunk/index stages skip empty content anyway), so short-circuit it.
        if (content == null || content.length == 0) {
            return "";
        }
        try (InputStream stream = new ByteArrayInputStream(content)) {
            String text = tika.parseToString(stream);
            return text == null ? "" : text;
        } catch (IOException | TikaException exception) {
            throw new ExtractionException("Could not extract document text", exception);
        }
    }

    public static class ExtractionException extends RuntimeException {
        public ExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
