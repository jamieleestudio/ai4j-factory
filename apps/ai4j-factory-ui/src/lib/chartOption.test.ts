import { describe, expect, it } from "vitest";
import type { EChartsOption } from "echarts";
import { buildOption } from "./chartOption";
import type { IntentPayload } from "../utils/fetchSSE";

function intent(
  dimensions: IntentPayload["dimensions"],
  metrics: string[] = ["销售额"]
): IntentPayload {
  return { subject: "订单分析", dimensions, metrics, filters: [] };
}

function seriesOf(opt: EChartsOption | null): Record<string, unknown>[] {
  const s = opt?.series;
  if (Array.isArray(s)) return s as Record<string, unknown>[];
  return s ? [s as Record<string, unknown>] : [];
}

describe("buildOption", () => {
  it("returns null for empty data", () => {
    expect(buildOption("bar", [], intent([{ name: "区域", type: "STRING" }]))).toBeNull();
  });

  it("returns null for single_value (KPI rendered separately)", () => {
    const data = [{ 销售额: 1000 }];
    expect(buildOption("single_value", data, intent([], ["销售额"]))).toBeNull();
  });

  it("builds bar option with category x-axis and value y-axis", () => {
    const data = [
      { 区域: "华东", 销售额: 1000 },
      { 区域: "华北", 销售额: 600 },
    ];
    const opt = buildOption("bar", data, intent([{ name: "区域", type: "STRING" }]));
    expect(opt).not.toBeNull();
    expect(opt?.xAxis).toMatchObject({ type: "category", data: ["华东", "华北"] });
    expect(opt?.yAxis).toMatchObject({ type: "value" });
    expect(opt?.series).toHaveLength(1);
    expect(seriesOf(opt)[0]).toMatchObject({ type: "bar", data: [1000, 600] });
  });

  it("builds pie option with name/value pairs", () => {
    const data = [
      { 区域: "华东", 销售额: 1000 },
      { 区域: "华北", 销售额: 600 },
    ];
    const opt = buildOption("pie", data, intent([{ name: "区域", type: "STRING" }]));
    expect(seriesOf(opt)[0]).toMatchObject({
      type: "pie",
      data: [
        { name: "华东", value: 1000 },
        { name: "华北", value: 600 },
      ],
    });
  });

  it("builds line option with line series", () => {
    const data = [
      { 下单时间: "2024-01", 销售额: 1000 },
      { 下单时间: "2024-02", 销售额: 1200 },
    ];
    const opt = buildOption("line", data, intent([{ name: "下单时间", type: "TIME" }]));
    expect(seriesOf(opt)[0]).toMatchObject({ type: "line", data: [1000, 1200] });
    expect(opt?.xAxis).toMatchObject({ data: ["2024-01", "2024-02"] });
  });

  it("builds grouped_bar with one series per dim[1] value", () => {
    const data = [
      { 区域: "华东", 产品线: "A", 销售额: 1000 },
      { 区域: "华东", 产品线: "B", 销售额: 800 },
      { 区域: "华北", 产品线: "A", 销售额: 600 },
      { 区域: "华北", 产品线: "B", 销售额: 400 },
    ];
    const opt = buildOption(
      "grouped_bar",
      data,
      intent([
        { name: "区域", type: "STRING" },
        { name: "产品线", type: "STRING" },
      ])
    );
    expect(opt?.xAxis).toMatchObject({ data: ["华东", "华北"] });
    expect(opt?.series).toHaveLength(2);
    expect(seriesOf(opt)[0]).toMatchObject({ name: "A", type: "bar", data: [1000, 600] });
    expect(seriesOf(opt)[1]).toMatchObject({ name: "B", type: "bar", data: [800, 400] });
  });

  it("builds stacked_bar with stack: 'total' on each series", () => {
    const data = [
      { 区域: "华东", 产品线: "A", 销售额: 1000 },
      { 区域: "华北", 产品线: "A", 销售额: 600 },
    ];
    const opt = buildOption(
      "stacked_bar",
      data,
      intent([
        { name: "区域", type: "STRING" },
        { name: "产品线", type: "STRING" },
      ])
    );
    expect(seriesOf(opt)[0]).toMatchObject({ stack: "total", type: "bar" });
  });

  it("grouped_bar series do NOT have stack field", () => {
    const data = [
      { 区域: "华东", 产品线: "A", 销售额: 1000 },
      { 区域: "华北", 产品线: "A", 销售额: 600 },
    ];
    const opt = buildOption(
      "grouped_bar",
      data,
      intent([
        { name: "区域", type: "STRING" },
        { name: "产品线", type: "STRING" },
      ])
    );
    expect(seriesOf(opt)[0]).not.toHaveProperty("stack");
  });

  it("builds heatmap with x/y category axes and visualMap", () => {
    const data = [
      { 区域: "华东", 产品线: "A", 销售额: 1000 },
      { 区域: "华东", 产品线: "B", 销售额: 800 },
      { 区域: "华北", 产品线: "A", 销售额: 600 },
      { 区域: "华北", 产品线: "B", 销售额: 400 },
    ];
    const opt = buildOption(
      "heatmap",
      data,
      intent([
        { name: "区域", type: "STRING" },
        { name: "产品线", type: "STRING" },
      ])
    );
    expect(opt?.xAxis).toMatchObject({ data: ["华东", "华北"] });
    expect(opt?.yAxis).toMatchObject({ data: ["A", "B"] });
    expect(opt?.visualMap).toMatchObject({ min: 400, max: 1000 });
    expect(seriesOf(opt)[0]).toMatchObject({ type: "heatmap" });
    const heatmapData = seriesOf(opt)[0].data as unknown[];
    expect(heatmapData).toHaveLength(4);
    expect(heatmapData[0]).toEqual([0, 0, 1000]);
  });

  it("builds line_multi with TIME dim on x-axis and other dim as series", () => {
    const data = [
      { 下单时间: "2024-01", 区域: "华东", 销售额: 1000 },
      { 下单时间: "2024-01", 区域: "华北", 销售额: 600 },
      { 下单时间: "2024-02", 区域: "华东", 销售额: 1200 },
      { 下单时间: "2024-02", 区域: "华北", 销售额: 800 },
    ];
    const opt = buildOption(
      "line_multi",
      data,
      intent([
        { name: "下单时间", type: "TIME" },
        { name: "区域", type: "STRING" },
      ])
    );
    expect(opt?.xAxis).toMatchObject({ data: ["2024-01", "2024-02"] });
    expect(opt?.series).toHaveLength(2);
    expect(seriesOf(opt)[0]).toMatchObject({ name: "华东", type: "line", data: [1000, 1200] });
    expect(seriesOf(opt)[1]).toMatchObject({ name: "华北", type: "line", data: [600, 800] });
  });

  it("line_multi uses dim[0] as x-axis even when not TIME (fallback)", () => {
    const data = [
      { 区域: "华东", 产品线: "A", 销售额: 1000 },
      { 区域: "华北", 产品线: "A", 销售额: 600 },
    ];
    const opt = buildOption(
      "line_multi",
      data,
      intent([
        { name: "区域", type: "STRING" },
        { name: "产品线", type: "STRING" },
      ])
    );
    expect(opt?.xAxis).toMatchObject({ data: ["华东", "华北"] });
  });

  it("coerces string numeric values to numbers", () => {
    const data = [{ 区域: "华东", 销售额: "1000" }];
    const opt = buildOption("bar", data, intent([{ name: "区域", type: "STRING" }]));
    expect(seriesOf(opt)[0]).toMatchObject({ data: [1000] });
  });

  it("treats null values as 0", () => {
    const data = [{ 区域: "华东", 销售额: null }];
    const opt = buildOption("bar", data, intent([{ name: "区域", type: "STRING" }]));
    expect(seriesOf(opt)[0]).toMatchObject({ data: [0] });
  });
});
