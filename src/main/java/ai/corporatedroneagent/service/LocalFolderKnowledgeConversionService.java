package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgeResource;
import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceConversion;
import ai.corporatedroneagent.model.knowledge.KnowledgeResourceRead;
import ai.corporatedroneagent.model.knowledge.WorkStatus;
import ai.corporatedroneagent.repository.KnowledgeResourcePipelineRepository;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class LocalFolderKnowledgeConversionService {

    private final KnowledgeResourcePipelineRepository pipelineRepository;

    public LocalFolderKnowledgeConversionService(KnowledgeResourcePipelineRepository pipelineRepository) {
        this.pipelineRepository = pipelineRepository;
    }

    public KnowledgeResourceConversion convert(KnowledgeResource resource, KnowledgeResourceRead read) {
        KnowledgeResourceConversion conversion = startConversion(resource);
        if (!Boolean.TRUE.equals(read.getSuccess())) {
            return finishConversion(
                    conversion,
                    false,
                    KnowledgePipelineReason.READ_DID_NOT_SUCCEED,
                    "Read did not succeed",
                    ""
            );
        }

        try {
            return finishConversion(conversion, true, null, "", decodeUtf8(read.getValue()));
        } catch (CharacterCodingException exception) {
            return finishConversion(
                    conversion,
                    false,
                    KnowledgePipelineReason.UTF8_DECODE_FAILED,
                    "Could not decode resource as UTF-8",
                    ""
            );
        }
    }

    private KnowledgeResourceConversion startConversion(KnowledgeResource resource) {
        KnowledgeResourceConversion conversion = pipelineRepository.findConversionByResourceId(resource.getId())
                .orElseGet(KnowledgeResourceConversion::new);
        conversion.setResourceId(resource.getId());
        conversion.setStatus(WorkStatus.IN_PROGRESS);
        conversion.setSuccess(null);
        conversion.setReason(null);
        conversion.setMessage("");
        conversion.setValue("");
        return pipelineRepository.saveConversion(conversion);
    }

    private KnowledgeResourceConversion finishConversion(
            KnowledgeResourceConversion conversion,
            boolean success,
            KnowledgePipelineReason reason,
            String message,
            String value
    ) {
        conversion.setStatus(WorkStatus.DONE);
        conversion.setSuccess(success);
        conversion.setReason(reason);
        conversion.setMessage(message);
        conversion.setValue(value);
        conversion.setConvertedAt(Instant.now());
        return pipelineRepository.saveConversion(conversion);
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
