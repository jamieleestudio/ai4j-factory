"use client";

import { buildOption } from "../lib/chartOption";
import type { ChartType } from "../lib/chartTypes";
import type { IntentPayload } from "../utils/fetchSSE";
import EChart from "./EChart";

interface ChartRendererProps {
  chartType: ChartType;
  data: Record<string, unknown>[];
  intent: IntentPayload;
}

function formatValue(value: unknown): string {
  if (value == null) return "-";
  const n = typeof value === "number" ? value : Number(value);
  if (!Number.isFinite(n)) return String(value);
  return n.toLocaleString("zh-CN");
}

export default function ChartRenderer({ chartType, data, intent }: ChartRendererProps) {
  if (chartType === "single_value") {
    const metric = intent.metrics[0];
    const value = data[0]?.[metric];
    return (
      <div className="flex flex-col items-center justify-center py-8">
        <div className="text-xs text-gray-500 dark:text-gray-400">{metric}</div>
        <div className="text-2xl font-medium text-foreground">
          {formatValue(value)}
        </div>
      </div>
    );
  }

  const option = buildOption(chartType, data, intent);
  if (!option) return null;

  return <EChart option={option} />;
}
