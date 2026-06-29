package org.ai4j.factory.bi.query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class QueryExecutionService {

    private static final Logger log = LoggerFactory.getLogger(QueryExecutionService.class);

    private final JdbcTemplate jdbcTemplate;

    public QueryExecutionService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> execute(SqlBuilder.SqlResult sqlResult) {
        log.info("Executing SQL: {} with params: {}", sqlResult.sql(), java.util.Arrays.toString(sqlResult.params()));

        try {
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sqlResult.sql(), sqlResult.params());
            log.info("Query returned {} rows", results.size());
            return results;
        } catch (Exception e) {
            log.error("Query execution failed: {}", e.getMessage());
            throw new RuntimeException("Database query failed: " + e.getMessage(), e);
        }
    }
}
