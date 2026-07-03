import type { EChartsOption } from "echarts";
import type { IntentPayload } from "../utils/sse";
import type { ChartType } from "./chartTypes";

type Row = Record<string, unknown>;
type Intent = Pick<IntentPayload, "dimensions" | "metrics">;

function num(v: unknown): number {
  if (v == null) return 0;
  const n = typeof v === "number" ? v : Number(v);
  return Number.isFinite(n) ? n : 0;
}

function str(v: unknown): string {
  return v == null ? "" : String(v);
}

const THEME_COLORS = [
  "#2563eb", // blue-600
  "#0ea5e9", // sky-500
  "#6366f1", // indigo-500
  "#38bdf8", // sky-400
  "#818cf8", // indigo-400
  "#0284c7", // sky-600
  "#4f46e5", // indigo-600
  "#93c5fd", // blue-300
];

export function buildOption(
  chartType: ChartType,
  data: Row[],
  intent: Intent
): EChartsOption | null {
  if (data.length === 0) return null;

  const dims = intent.dimensions;
  const metric = intent.metrics[0];

  switch (chartType) {
    case "single_value":
      return null;

    case "bar": {
      const d0 = dims[0].name;
      return {
        color: THEME_COLORS,
        xAxis: { type: "category", data: data.map((r) => str(r[d0])) },
        yAxis: { type: "value" },
        series: [{ type: "bar", data: data.map((r) => num(r[metric])) }],
        tooltip: { trigger: "axis" },
        grid: { left: "3%", right: "4%", bottom: "3%", containLabel: true },
      };
    }

    case "pie": {
      const d0 = dims[0].name;
      return {
        color: THEME_COLORS,
        series: [
          {
            type: "pie",
            data: data.map((r) => ({ name: str(r[d0]), value: num(r[metric]) })),
          },
        ],
        tooltip: { trigger: "item" },
      };
    }

    case "line": {
      const d0 = dims[0].name;
      return {
        color: THEME_COLORS,
        xAxis: { type: "category", data: data.map((r) => str(r[d0])) },
        yAxis: { type: "value" },
        series: [{ type: "line", data: data.map((r) => num(r[metric])) }],
        tooltip: { trigger: "axis" },
        grid: { left: "3%", right: "4%", bottom: "3%", containLabel: true },
      };
    }

    case "grouped_bar":
    case "stacked_bar": {
      const stacked = chartType === "stacked_bar";
      const d0 = dims[0].name;
      const d1 = dims[1].name;
      const xValues = [...new Set(data.map((r) => str(r[d0])))];
      const seriesValues = [...new Set(data.map((r) => str(r[d1])))];
      return {
        color: THEME_COLORS,
        xAxis: { type: "category", data: xValues },
        yAxis: { type: "value" },
        series: seriesValues.map((sv) => ({
          name: sv,
          type: "bar",
          ...(stacked ? { stack: "total" } : {}),
          data: xValues.map((xv) => {
            const row = data.find(
              (r) => str(r[d0]) === xv && str(r[d1]) === sv
            );
            return row ? num(row[metric]) : 0;
          }),
        })),
        legend: { top: 0 },
        tooltip: { trigger: "axis" },
        grid: { left: "3%", right: "4%", bottom: "3%", containLabel: true },
      };
    }

    case "heatmap": {
      const d0 = dims[0].name;
      const d1 = dims[1].name;
      const xValues = [...new Set(data.map((r) => str(r[d0])))];
      const yValues = [...new Set(data.map((r) => str(r[d1])))];
      const values = data.map((r) => num(r[metric]));
      const min = values.length > 0 ? Math.min(...values) : 0;
      const max = values.length > 0 ? Math.max(...values) : 0;
      return {
        color: THEME_COLORS,
        tooltip: { position: "top" },
        grid: { height: "60%", top: "10%" },
        xAxis: { type: "category", data: xValues },
        yAxis: { type: "category", data: yValues },
        visualMap: {
          min,
          max,
          calculable: true,
          orient: "horizontal",
          left: "center",
          bottom: "5%",
          inRange: {
            color: ["#eff6ff", "#60a5fa", "#1e40af"], // 浅蓝到深蓝
          },
        },
        series: [
          {
            type: "heatmap",
            data: data.map((r) => [
              xValues.indexOf(str(r[d0])),
              yValues.indexOf(str(r[d1])),
              num(r[metric]),
            ]),
            label: { show: true },
          },
        ],
      };
    }

    case "line_multi": {
      const timeDim = dims.find((d) => d.type === "TIME") ?? dims[0];
      const otherDim = dims.find((d) => d !== timeDim) ?? dims[1];
      const d0 = timeDim.name;
      const d1 = otherDim.name;
      const xValues = [...new Set(data.map((r) => str(r[d0])))];
      const seriesValues = [...new Set(data.map((r) => str(r[d1])))];
      return {
        color: THEME_COLORS,
        xAxis: { type: "category", data: xValues },
        yAxis: { type: "value" },
        series: seriesValues.map((sv) => ({
          name: sv,
          type: "line",
          data: xValues.map((xv) => {
            const row = data.find(
              (r) => str(r[d0]) === xv && str(r[d1]) === sv
            );
            return row ? num(row[metric]) : 0;
          }),
        })),
        legend: { top: 0 },
        tooltip: { trigger: "axis" },
        grid: { left: "3%", right: "4%", bottom: "3%", containLabel: true },
      };
    }
  }
}
