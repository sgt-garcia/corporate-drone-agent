package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;

/**
 * Renders a local file's bytes to text by strict UTF-8 decode, returning a pure
 * {@link ConversionResult} (the engine records the conversion stage). Only called when the
 * read succeeded.
 */
@Service
public class LocalFolderKnowledgeConversionService {

    public ConversionResult convert(ReadResult read) {
        try {
            return ConversionResult.of(decodeUtf8(read.value()));
        } catch (CharacterCodingException exception) {
            return ConversionResult.failed(
                    KnowledgePipelineReason.UTF8_DECODE_FAILED,
                    "Could not decode resource as UTF-8");
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
