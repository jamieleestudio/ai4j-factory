export const CHART_TYPES = [
  "single_value",
  "bar",
  "pie",
  "line",
  "grouped_bar",
  "stacked_bar",
  "heatmap",
  "line_multi",
] as const;

export type ChartType = (typeof CHART_TYPES)[number];

const CHART_LABELS: Record<ChartType, string> = {
  single_value: "单值",
  bar: "柱状图",
  pie: "饼图",
  line: "折线图",
  grouped_bar: "分组柱状图",
  stacked_bar: "堆叠柱状图",
  heatmap: "热力图",
  line_multi: "多线折线图",
};

export function chartLabel(type: ChartType): string {
  return CHART_LABELS[type];
}

export function isChartType(value: string): value is ChartType {
  return (CHART_TYPES as readonly string[]).includes(value);
}
