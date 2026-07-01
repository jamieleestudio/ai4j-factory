package org.ai4j.factory.bi.clarification;

import org.ai4j.factory.sse.ClarificationOption;

import java.time.Instant;
import java.util.List;

public record PendingClarification(
        String originalQuestion,
        String reason,
        List<ClarificationOption> options,
        String contextSubject,
        Instant createdAt
) {}
