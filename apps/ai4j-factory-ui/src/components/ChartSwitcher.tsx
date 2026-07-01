"use client";

import { chartLabel } from "../lib/chartTypes";
import type { ChartType } from "../lib/chartTypes";

interface ChartSwitcherProps {
  candidateCharts: ChartType[];
  activeChart: ChartType;
  onChange: (type: ChartType) => void;
}

export default function ChartSwitcher({
  candidateCharts,
  activeChart,
  onChange,
}: ChartSwitcherProps) {
  if (candidateCharts.length === 0) return null;

  return (
    <div className="flex flex-wrap gap-2 mt-2">
      {candidateCharts.map((c) => {
        const isActive = c === activeChart;
        return (
          <button
            key={c}
            onClick={() => onChange(c)}
            className={
              "px-3 py-1.5 text-sm rounded-full transition-colors " +
              (isActive
                ? "bg-foreground text-background border border-foreground"
                : "bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 text-foreground hover:border-foreground hover:bg-gray-50 dark:hover:bg-gray-700/50")
            }
          >
            {chartLabel(c)}
          </button>
        );
      })}
    </div>
  );
}
