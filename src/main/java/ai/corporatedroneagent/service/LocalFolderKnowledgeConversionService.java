package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Renders a local file's bytes to text, routing by format: plain-text formats are decoded
 * as strict UTF-8, while document formats (PDF, Office, OpenDocument, RTF) go through
 * {@link DocumentTextExtractor}. Returns a pure {@link ConversionResult} (the engine records
 * the conversion stage). Only called when the read succeeded.
 */
@Service
public class LocalFolderKnowledgeConversionService {

    private static final Logger log = LoggerFactory.getLogger(LocalFolderKnowledgeConversionService.class);

    private final DocumentTextExtractor documentTextExtractor;

    public LocalFolderKnowledgeConversionService(DocumentTextExtractor documentTextExtractor) {
        this.documentTextExtractor = documentTextExtractor;
    }

    public ConversionResult convert(ReadResult read) {
        if (KnowledgeFileFormats.isDocument(read.format())) {
            return convertDocument(read);
        }
        return convertText(read);
    }

    private ConversionResult convertText(ReadResult read) {
        try {
            return ConversionResult.of(decodeUtf8(read.value()));
        } catch (CharacterCodingException exception) {
            return ConversionResult.failed(
                    KnowledgePipelineReason.UTF8_DECODE_FAILED,
                    "Could not decode resource as UTF-8");
        }
    }

    private ConversionResult convertDocument(ReadResult read) {
        try {
            return ConversionResult.of(documentTextExtractor.extract(read.value()));
        } catch (DocumentTextExtractor.ExtractionException exception) {
            log.warn("Could not extract document text for knowledge resource {}.", read.reference(), exception);
            return ConversionResult.failed(
                    KnowledgePipelineReason.DOCUMENT_EXTRACTION_FAILED,
                    "Could not extract document text");
        }
    }

    private String decodeUtf8(byte[] value) throws CharacterCodingException {
        if (value == null) {
            return "";
        }
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(value))
                .toString();
    }
}
