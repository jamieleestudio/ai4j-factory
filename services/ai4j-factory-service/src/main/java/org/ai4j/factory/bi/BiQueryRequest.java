package org.ai4j.factory.bi;

public record BiQueryRequest(
        String question,
        Long credentialId,
        String modelName,
        String sessionId
) {
}
