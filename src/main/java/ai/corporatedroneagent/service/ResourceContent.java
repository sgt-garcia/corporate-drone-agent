package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;

/**
 * The read + conversion outcome an adapter produces for one item. The engine records
 * both pipeline stages from it (so stage persistence stays generic) and then chunks +
 * indexes the resulting text. Adapters acquire bytes and render text however their
 * source requires; failures are typed so the UI can explain a skipped resource.
 */
public record ResourceContent(
        boolean readSuccess,
        KnowledgePipelineReason readReason,
        String readMessage,
        byte[] readValue,
        boolean conversionSuccess,
        KnowledgePipelineReason conversionReason,
        String conversionMessage,
        String text
) {

    /** Read and conversion both succeeded: {@code readValue} is the raw bytes, {@code text} the rendered text. */
    public static ResourceContent of(byte[] readValue, String text) {
        return new ResourceContent(true, null, "", readValue, true, null, "", text);
    }

    /** The item could not be read (unsupported/too large/unreadable); conversion is marked not-attempted. */
    public static ResourceContent readFailed(KnowledgePipelineReason reason, String message) {
        return new ResourceContent(
                false, reason, message, null,
                false, KnowledgePipelineReason.READ_DID_NOT_SUCCEED, "Read did not succeed", ""
        );
    }

    /** Bytes were read but could not be rendered to text. */
    public static ResourceContent conversionFailed(byte[] readValue, KnowledgePipelineReason reason, String message) {
        return new ResourceContent(true, null, "", readValue, false, reason, message, "");
    }
}
