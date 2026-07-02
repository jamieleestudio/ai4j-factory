"use client";

import { useEffect, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { PanelLeft, BarChart3, Loader2, Brain, ChevronRight } from "lucide-react";
import ChatInput from "./ChatInput";
import ChartRenderer from "./ChartRenderer";
import ChartSwitcher from "./ChartSwitcher";
import Markdown from "./Markdown";
import { credentialService } from "../services/credentialService";
import { SelectableModelOption } from "../types/credential";
import { buildSelectableModelOptions } from "../utils/modelOptions";
import { subscribeSSE, type SSESubscription, IntentPayload, ClarificationOption } from "../utils/sse";
import { inferChartPool } from "../lib/chartPool";
import { isChartType, type ChartType } from "../lib/chartTypes";

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

type BiMessage =
  | { id: string; role: "user"; content: string }
  | {
      id: string;
      role: "assistant";
      status: "loading" | "streaming" | "clarification" | "success" | "error";
      progressText?: string;
      intent?: IntentPayload;
      streamingText?: string;
      result?: InsightResult;
      activeChart?: ChartType;
      clarification?: { sessionId: string; message: string; options: ClarificationOption[] };
      error?: string;
    };

const generateId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;

function ThinkingBlock({ intent, progressText }: { intent?: IntentPayload; progressText?: string }) {
  if (!intent && !progressText) return null;
  return (
    <div className="p-3 bg-gray-50 dark:bg-gray-900/40 rounded-lg border border-gray-200 dark:border-gray-700 text-sm">
      <div className="flex items-center gap-1.5 text-xs font-medium text-gray-500 dark:text-gray-400 mb-2">
        <Brain size={14} />
        <span>Thinking</span>
      </div>
      {progressText && (
        <div className="text-gray-600 dark:text-gray-300 mb-2">{progressText}</div>
      )}
      {intent && (
        <div className="space-y-1 text-xs text-gray-600 dark:text-gray-400">
          <div>
            <span className="text-gray-400">Subject: </span>
            <span className="font-medium text-foreground">{intent.subject}</span>
          </div>
          {intent.metrics.length > 0 && (
            <div>
              <span className="text-gray-400">Metrics: </span>
              <span className="font-medium text-foreground">{intent.metrics.join(", ")}</span>
            </div>
          )}
          {intent.dimensions.length > 0 && (
            <div>
              <span className="text-gray-400">Dimensions: </span>
              <span className="font-medium text-foreground">
                {intent.dimensions.map((d) => d.name).join(", ")}
              </span>
            </div>
          )}
          {intent.filters.length > 0 && (
            <div>
              <span className="text-gray-400">Filters: </span>
              <span className="font-medium text-foreground">
                {intent.filters.map((f) => `${f.dimension} ${f.operator} ${f.value}`).join("; ")}
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

function ClarificationBlock({
  message,
  options,
  onSelect,
}: {
  message: string;
  options: ClarificationOption[];
  onSelect: (value: string) => void;
}) {
  return (
    <div className="p-4 bg-amber-50 dark:bg-amber-900/20 rounded-lg border border-amber-200 dark:border-amber-800">
      <div className="text-sm text-foreground mb-3">{message}</div>
      <div className="flex flex-wrap gap-2">
        {options.map((opt) => (
          <button
            key={opt.value}
            onClick={() => onSelect(opt.value)}
            className="flex items-center gap-1.5 px-3 py-1.5 text-sm bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 rounded-full hover:border-foreground hover:bg-gray-50 dark:hover:bg-gray-700/50 transition-colors text-foreground"
          >
            <span className="font-medium">{opt.label}</span>
            {opt.description && (
              <span className="text-xs text-gray-500 dark:text-gray-400">— {opt.description}</span>
            )}
            <ChevronRight size={14} className="text-gray-400" />
          </button>
        ))}
      </div>
    </div>
  );
}

export default function BiArea({ isSidebarOpen, toggleSidebar }: BiAreaProps) {
  const [messages, setMessages] = useState<BiMessage[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [modelOptions, setModelOptions] = useState<SelectableModelOption[]>([]);
  const [selectedModel, setSelectedModel] = useState<SelectableModelOption | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const conversationSessionIdRef = useRef<string>(generateId());
  const pendingClarificationSessionIdRef = useRef<string | null>(null);
  const suppressScrollRef = useRef(false);
  const subscriptionRef = useRef<SSESubscription | null>(null);

  useEffect(() => {
    return () => {
      subscriptionRef.current?.close();
      subscriptionRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (suppressScrollRef.current) {
      suppressScrollRef.current = false;
      return;
    }
    if (bottomRef.current) {
      bottomRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, isLoading]);

  useEffect(() => {
    const loadCredentials = async () => {
      try {
        const [creds, configs] = await Promise.all([
          credentialService.getCredentials(),
          credentialService.getConfigs(),
        ]);
        const options = buildSelectableModelOptions(creds, configs);
        setModelOptions(options);
        if (options.length > 0) {
          setSelectedModel(options[0]);
        }
      } catch (error) {
        console.error("Failed to load model options", error);
      }
    };
    loadCredentials();
  }, []);

  const handleQuery = async (content: string, clarificationSessionId?: string) => {
    if (isLoading) return;

    if (!selectedModel) {
      setMessages((prev) => [
        ...prev,
        { id: generateId(), role: "user", content },
        { id: generateId(), role: "assistant", status: "error", error: "Please select a model first." },
      ]);
      return;
    }

    const userMsgId = generateId();
    const assistantMsgId = generateId();
    let fullText = "";

    setMessages((prev) => [
      ...prev,
      { id: userMsgId, role: "user", content },
      { id: assistantMsgId, role: "assistant", status: "loading" },
    ]);
    setIsLoading(true);

    const baseUrl = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";
    const params = new URLSearchParams();
    params.set("question", content);
    params.set("credentialId", String(selectedModel.credentialId));
    if (selectedModel.modelName) params.set("modelName", selectedModel.modelName);
    params.set("sessionId", clarificationSessionId ?? conversationSessionIdRef.current);
    const url = `${baseUrl}/api/bi/query?${params.toString()}`;

    subscriptionRef.current?.close();
    const sub = subscribeSSE(url, {
      onStatus: (_stage, message) => {
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMsgId
              ? { ...msg, status: "loading" as const, progressText: message }
              : msg
          )
        );
      },
      onIntent: (intent) => {
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMsgId ? { ...msg, intent } : msg
          )
        );
      },
      onChunk: (text) => {
        fullText += text;
        flushSync(() => {
          setMessages((prev) =>
            prev.map((msg) =>
              msg.id === assistantMsgId
                ? { ...msg, status: "streaming" as const, streamingText: fullText }
                : msg
            )
          );
        });
      },
      onResult: (result) => {
        pendingClarificationSessionIdRef.current = null;
        setMessages((prev) =>
          prev.map((msg) => {
            if (msg.id !== assistantMsgId || msg.role !== "assistant") return msg;
            const pool = msg.intent ? inferChartPool(msg.intent) : [];
            const recommended = isChartType(result.chartType) ? result.chartType : null;
            const valid = recommended && pool.includes(recommended) ? recommended : null;
            const fallback = pool[0];
            if (recommended && !valid) {
              console.warn(
                `LLM recommended chartType "${recommended}" not in candidate pool [${pool.join(", ")}], falling back to "${fallback ?? "none"}"`
              );
            }
            return {
              ...msg,
              status: "success" as const,
              activeChart: (valid ?? fallback) as ChartType | undefined,
              result: {
                question: content,
                summary: fullText,
                data: result.data,
                chartType: result.chartType,
              },
            };
          })
        );
      },
      onClarification: (clarification) => {
        pendingClarificationSessionIdRef.current = clarification.sessionId;
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMsgId
              ? {
                  ...msg,
                  status: "clarification" as const,
                  clarification: {
                    sessionId: clarification.sessionId,
                    message: clarification.message,
                    options: clarification.options,
                  },
                }
              : msg
          )
        );
      },
      onError: (error) => {
        pendingClarificationSessionIdRef.current = null;
        setMessages((prev) =>
          prev.map((msg) =>
            msg.id === assistantMsgId
              ? { ...msg, status: "error" as const, error }
              : msg
          )
        );
      },
      onDone: () => {
        setIsLoading(false);
        subscriptionRef.current?.close();
        subscriptionRef.current = null;
      },
    });
    subscriptionRef.current = sub;
  };

  const handleChipSelect = (assistantMsgId: string, value: string) => {
    handleQuery(value, pendingClarificationSessionIdRef.current ?? undefined);
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
            <span className="text-lg font-medium text-foreground">Data Warehouse BI</span>
          </div>
        </div>
      </div>

      {/* Content Area */}
      <div className="flex-1 overflow-y-auto px-4 py-6 no-scrollbar">
        <div className="max-w-4xl mx-auto space-y-6">
          {messages.length === 0 && (
            <div className="text-center py-16 text-gray-400">
              <BarChart3 size={48} className="mx-auto mb-4 opacity-50" />
              <p className="text-lg">Ask a data question to get started</p>
              <p className="text-sm mt-2">e.g. &quot;华东区销售额多少&quot;</p>
            </div>
          )}

          {messages.map((msg) => {
            if (msg.role === "user") {
              return (
                <div key={msg.id} className="flex justify-end">
                  <div className="max-w-[85%] text-right">
                    <div className="text-foreground text-base leading-7 font-light tracking-wide inline-block whitespace-pre-wrap bg-gray-100 dark:bg-[#1E1F20] px-4 py-2 rounded-[20px] rounded-tr-sm">
                      {msg.content}
                    </div>
                  </div>
                </div>
              );
            }

            if (msg.role === "assistant") {
              if (msg.status === "loading") {
                return (
                  <div key={msg.id} className="space-y-3">
                    <ThinkingBlock intent={msg.intent} progressText={msg.progressText} />
                    <div className="flex items-center justify-center py-6">
                      <Loader2 size={28} className="animate-spin text-gray-400" />
                      <span className="ml-3 text-gray-500">
                        {msg.progressText ?? "Analyzing..."}
                      </span>
                    </div>
                  </div>
                );
              }

              if (msg.status === "clarification" && msg.clarification) {
                return (
                  <div key={msg.id} className="space-y-3">
                    <ThinkingBlock intent={msg.intent} progressText={msg.progressText} />
                    <ClarificationBlock
                      message={msg.clarification.message}
                      options={msg.clarification.options}
                      onSelect={(value) => handleChipSelect(msg.id, value)}
                    />
                  </div>
                );
              }

              if (msg.status === "streaming") {
                return (
                  <div key={msg.id} className="space-y-3">
                    <ThinkingBlock intent={msg.intent} progressText={msg.progressText} />
                    <div className="text-foreground leading-7">
                      <Markdown content={msg.streamingText ?? ""} />
                    </div>
                  </div>
                );
              }

              if (msg.status === "error") {
                return (
                  <div key={msg.id} className="space-y-3">
                    <ThinkingBlock intent={msg.intent} progressText={msg.progressText} />
                    <div className="p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-red-700 dark:text-red-400">
                      {msg.error}
                    </div>
                  </div>
                );
              }

              if (msg.status === "success" && msg.result) {
                const result = msg.result;
                const pool = msg.intent ? inferChartPool(msg.intent) : [];
                const showChart = pool.length > 0 && msg.activeChart !== undefined && msg.intent;
                return (
                  <div key={msg.id} className="space-y-3">
                    <ThinkingBlock intent={msg.intent} progressText={msg.progressText} />
                    {/* Summary */}
                    <div className="text-foreground leading-7">
                      <Markdown content={result.summary} />
                    </div>

                    {/* Chart Area */}
                    {showChart && msg.intent && (
                      <div className="p-4 rounded-lg border border-gray-200 dark:border-gray-700">
                        <ChartRenderer
                          chartType={msg.activeChart!}
                          data={result.data}
                          intent={msg.intent}
                        />
                        {pool.length > 1 && (
                          <ChartSwitcher
                            candidateCharts={pool}
                            activeChart={msg.activeChart!}
                            onChange={(type) => {
                              suppressScrollRef.current = true;
                              setMessages((prev) =>
                                prev.map((m) =>
                                  m.id === msg.id && m.role === "assistant"
                                    ? { ...m, activeChart: type }
                                    : m
                                )
                              );
                            }}
                          />
                        )}
                      </div>
                    )}

                    {!showChart && pool.length === 0 && (
                      <div className="p-4 text-center text-sm text-gray-500 rounded-lg border border-gray-200 dark:border-gray-700">
                        维度过多，暂不支持自动可视化
                      </div>
                    )}

                    {/* Data Table */}
                    {result.data && result.data.length > 0 && msg.activeChart !== "single_value" && (
                      <div className="overflow-x-auto overflow-y-auto max-h-80 rounded-lg border border-gray-200 dark:border-gray-700">
                        <table className="w-full text-sm">
                          <thead>
                            <tr className="bg-gray-50 dark:bg-gray-800 sticky top-0">
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
                );
              }
            }
            return null;
          })}

          {(messages.length > 0 || isLoading) && <div ref={bottomRef} className="h-4" />}
        </div>
      </div>

      {/* Input Area */}
      <div className="w-full px-4 pb-6 pt-2 bg-gradient-to-t from-background via-background to-transparent">
        <ChatInput
          onSend={handleQuery}
          isLoading={isLoading}
          modelOptions={modelOptions}
          selectedModel={selectedModel}
          onModelChange={setSelectedModel}
        />
        <div className="text-[11px] text-center text-gray-400 dark:text-gray-500 mt-3 font-light">
          Ask questions about your data. BI Agent will query and analyze.
        </div>
      </div>
    </div>
  );
}
