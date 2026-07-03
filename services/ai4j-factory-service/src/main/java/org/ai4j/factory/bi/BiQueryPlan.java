package org.ai4j.factory.bi;

import org.ai4j.factory.bi.intent.QueryIntent;
import org.ai4j.factory.bi.query.SqlBuilder;
import org.ai4j.factory.bi.semantic.Subject;

public record BiQueryPlan(
        QueryIntent intent,
        Subject subject,
        SqlBuilder.SqlResult sqlResult
) {
}
