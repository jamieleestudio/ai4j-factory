import type { IntentPayload } from "../utils/fetchSSE";
import type { ChartType } from "./chartTypes";

export function inferChartPool(
  intent: Pick<IntentPayload, "dimensions" | "metrics">
): ChartType[] {
  const ndim = intent.dimensions.length;
  const nmetric = intent.metrics.length;

  if (nmetric !== 1) {
    return [];
  }

  if (ndim === 0) {
    return ["single_value"];
  }

  if (ndim === 1) {
    const isTime = intent.dimensions[0].type === "TIME";
    return isTime ? ["line", "bar"] : ["bar", "pie"];
  }

  if (ndim === 2) {
    const isTime = intent.dimensions.some((d) => d.type === "TIME");
    return isTime
      ? ["line_multi", "grouped_bar", "stacked_bar"]
      : ["grouped_bar", "stacked_bar", "heatmap"];
  }

  return [];
}
