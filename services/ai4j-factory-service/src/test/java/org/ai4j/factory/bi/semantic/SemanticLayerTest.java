package org.ai4j.factory.bi.semantic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SemanticLayerTest {

    private SemanticLayer semanticLayer;

    @BeforeEach
    void setUp() throws IOException {
        semanticLayer = new SemanticLayer();
        semanticLayer.loadFromResources("classpath:/semantic/orders.json");
    }

    @Test
    void toTracePayloadExposesBusinessFieldsOnly() {
        List<SubjectTracePayload> payloads = semanticLayer.toTracePayload();

        assertEquals(1, payloads.size());
        SubjectTracePayload sp = payloads.get(0);
        assertEquals("订单分析", sp.name());
        assertEquals("所有订单数据，一条记录是一个订单", sp.description());

        assertEquals(3, sp.metrics().size());
        MetricTracePayload mp = sp.metrics().get(0);
        assertEquals("销售额", mp.name());
        assertEquals("订单金额总和", mp.description());
        assertEquals("SUM", mp.aggregation());

        assertEquals(4, sp.dimensions().size());
        DimensionTracePayload dp = sp.dimensions().get(0);
        assertEquals("区域", dp.name());
        assertEquals("STRING", dp.type());
    }

    @Test
    void toTracePayloadDoesNotExposePhysicalSchema() {
        // Records are immutable data carriers — assert their components exclude column/table
        for (var comp : SubjectTracePayload.class.getRecordComponents()) {
            assertNotEquals("column", comp.getName());
            assertNotEquals("table", comp.getName());
        }
        for (var comp : MetricTracePayload.class.getRecordComponents()) {
            assertNotEquals("column", comp.getName());
        }
        for (var comp : DimensionTracePayload.class.getRecordComponents()) {
            assertNotEquals("column", comp.getName());
        }
    }
}
