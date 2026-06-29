"use client";

import { useState } from "react";
import { PanelLeft, BarChart3, Loader2 } from "lucide-react";
import ChatInput from "./ChatInput";

interface BiAreaProps {
  isSidebarOpen: boolean;
  toggleSidebar: () => void;
}

interface InsightResult {
  question: string;
  summary: string;
  data: Record<string, unknown>[];
  chartType: string;
}

export default function BiArea({ isSidebarOpen, toggleSidebar }: BiAreaProps) {
  const [question, setQuestion] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [result, setResult] = useState<InsightResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleQuery = async (content: string) => {
    if (isLoading) return;

    setQuestion(content);
    setIsLoading(true);
    setError(null);
    setResult(null);

    try {
      const baseUrl = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";
      const response = await fetch(`${baseUrl}/api/bi/query`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ question: content }),
      });

      if (!response.ok) {
        throw new Error(`Query failed: ${response.statusText}`);
      }

      const data: InsightResult = await response.json();
      setResult(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Query failed");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex-1 flex flex-col h-full bg-background relative transition-all duration-300">
      {/* Header */}
      <div className="sticky top-0 z-10 flex items-center justify-between p-4 bg-background/80 backdrop-blur-md">
        <div className="flex items-center gap-2">
          {!isSidebarOpen && (
            <button
              onClick={toggleSidebar}
              className="p-2 text-gray-500 hover:text-foreground hover:bg-black/5 dark:hover:bg-white/10 rounded-full transition-colors"
            >
              <PanelLeft size={20} />
            </button>
          )}
          <div className="flex items-center gap-2 px-3 py-2">
            <BarChart3 size={20} className="text-gray-500" />
            <span className="text-lg font-medium text-foreground">BI Analysis</span>
          </div>
        </div>
      </div>

      {/* Content Area */}
      <div className="flex-1 overflow-y-auto px-4 py-6">
        <div className="max-w-4xl mx-auto space-y-6">
          {isLoading && (
            <div className="flex items-center justify-center py-12">
              <Loader2 size={32} className="animate-spin text-gray-400" />
              <span className="ml-3 text-gray-500">Analyzing...</span>
            </div>
          )}

          {error && (
            <div className="p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-red-700 dark:text-red-400">
              {error}
            </div>
          )}

          {result && (
            <div className="space-y-6">
              {/* Question */}
              <div className="p-4 bg-gray-50 dark:bg-gray-800/50 rounded-lg border border-gray-200 dark:border-gray-700">
                <div className="text-xs font-medium text-gray-500 mb-1">Question</div>
                <div className="text-foreground">{result.question}</div>
              </div>

              {/* Summary */}
              <div className="p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
                <div className="text-xs font-medium text-blue-600 dark:text-blue-400 mb-1">Insight</div>
                <div className="text-foreground">{result.summary}</div>
                {result.chartType && result.chartType !== "single_value" && (
                  <div className="mt-2 text-xs text-gray-500">
                    Recommended chart: <span className="font-medium">{result.chartType}</span>
                  </div>
                )}
              </div>

              {/* Data Table */}
              {result.data && result.data.length > 0 && (
                <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
                  <table className="w-full text-sm">
                    <thead>
                      <tr className="bg-gray-50 dark:bg-gray-800">
                        {Object.keys(result.data[0]).map((key) => (
                          <th key={key} className="px-4 py-3 text-left font-medium text-gray-600 dark:text-gray-400">
                            {key}
                          </th>
                        ))}
                      </tr>
                    </thead>
                    <tbody>
                      {result.data.map((row, i) => (
                        <tr
                          key={i}
                          className="border-t border-gray-200 dark:border-gray-700 hover:bg-gray-50 dark:hover:bg-gray-800/50"
                        >
                          {Object.values(row).map((val, j) => (
                            <td key={j} className="px-4 py-3 text-foreground">
                              {val != null ? String(val) : "-"}
                            </td>
                          ))}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {result.data && result.data.length === 0 && (
                <div className="text-center py-8 text-gray-500">No data returned.</div>
              )}
            </div>
          )}

          {!result && !isLoading && !error && (
            <div className="text-center py-16 text-gray-400">
              <BarChart3 size={48} className="mx-auto mb-4 opacity-50" />
              <p className="text-lg">Ask a data question to get started</p>
              <p className="text-sm mt-2">e.g. &quot;华东区销售额多少&quot;</p>
            </div>
          )}
        </div>
      </div>

      {/* Input Area */}
      <div className="w-full px-4 pb-6 pt-2 bg-gradient-to-t from-background via-background to-transparent">
        <ChatInput onSend={handleQuery} isLoading={isLoading} />
        <div className="text-[11px] text-center text-gray-400 dark:text-gray-500 mt-3 font-light">
          Ask questions about your data. BI Agent will query and analyze.
        </div>
      </div>
    </div>
  );
}
