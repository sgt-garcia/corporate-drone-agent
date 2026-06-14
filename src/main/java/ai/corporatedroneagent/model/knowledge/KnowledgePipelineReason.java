package ai.corporatedroneagent.model.knowledge;

public enum KnowledgePipelineReason {
    UNSUPPORTED_FILE_FORMAT,
    FILE_TOO_LARGE,
    READ_FAILED,
    READ_DID_NOT_SUCCEED,
    UTF8_DECODE_FAILED,
    CONVERSION_FAILED,
    INDEX_FAILED
}
