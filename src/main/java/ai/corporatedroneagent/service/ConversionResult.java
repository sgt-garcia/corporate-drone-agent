package ai.corporatedroneagent.service;

import ai.corporatedroneagent.model.knowledge.KnowledgePipelineReason;

/** The conversion stage outcome for one item: the rendered text, or a typed failure. */
public record ConversionResult(
        boolean success,
        KnowledgePipelineReason reason,
        String message,
        String text
) {

    public static ConversionResult of(String text) {
        return new ConversionResult(true, null, "", text);
    }

    public static ConversionResult failed(KnowledgePipelineReason reason, String message) {
        return new ConversionResult(false, reason, message, "");
    }
}
