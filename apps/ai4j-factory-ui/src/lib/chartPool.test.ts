import { describe, expect, it } from "vitest";
import { inferChartPool } from "./chartPool";
import type { IntentPayload } from "../utils/sse";

function intent(
  dimensions: IntentPayload["dimensions"],
  metrics: string[] = ["销售额"]
): Pick<IntentPayload, "dimensions" | "metrics"> {
  return { dimensions, metrics };
}

describe("inferChartPool", () => {
  it("0 dim + 1 metric → [single_value]", () => {
    expect(inferChartPool(intent([]))).toEqual(["single_value"]);
  });

  it("1 dim (STRING) + 1 metric → [bar, pie]", () => {
    expect(inferChartPool(intent([{ name: "区域", type: "STRING" }]))).toEqual([
      "bar",
      "pie",
    ]);
  });

  it("1 dim (TIME) + 1 metric → [line, bar]", () => {
    expect(inferChartPool(intent([{ name: "下单时间", type: "TIME" }]))).toEqual([
      "line",
      "bar",
    ]);
  });

  it("2 dim (both STRING) + 1 metric → [grouped_bar, stacked_bar, heatmap]", () => {
    expect(
      inferChartPool(
        intent([
          { name: "区域", type: "STRING" },
          { name: "产品线", type: "STRING" },
        ])
      )
    ).toEqual(["grouped_bar", "stacked_bar", "heatmap"]);
  });

  it("2 dim (含 TIME) + 1 metric → [line_multi, grouped_bar, stacked_bar]", () => {
    expect(
      inferChartPool(
        intent([
          { name: "下单时间", type: "TIME" },
          { name: "区域", type: "STRING" },
        ])
      )
    ).toEqual(["line_multi", "grouped_bar", "stacked_bar"]);
  });

  it("3 dim → [] (unsupported)", () => {
    expect(
      inferChartPool(
        intent([
          { name: "区域", type: "STRING" },
          { name: "产品线", type: "STRING" },
          { name: "签单人", type: "STRING" },
        ])
      )
    ).toEqual([]);
  });

  it("0 dim + 2 metric → [] (multi-metric unsupported)", () => {
    expect(inferChartPool(intent([], ["销售额", "订单量"]))).toEqual([]);
  });

  it("1 dim + 2 metric → [] (multi-metric unsupported)", () => {
    expect(
      inferChartPool(intent([{ name: "区域", type: "STRING" }], ["销售额", "订单量"]))
    ).toEqual([]);
  });

  it("2 dim where dim[0] is TIME → uses isTime branch (line_multi first)", () => {
    expect(
      inferChartPool(
        intent([
          { name: "下单时间", type: "TIME" },
          { name: "产品线", type: "STRING" },
        ])
      )
    ).toEqual(["line_multi", "grouped_bar", "stacked_bar"]);
  });
});
