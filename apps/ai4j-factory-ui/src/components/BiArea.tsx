"use client";

import { useEffect, useMemo, useRef, useState } from "react";
import { flushSync } from "react-dom";
import { PanelLeft, BarChart3, Loader2, Brain, ChevronRight, HelpCircle, ChevronDown, ChevronUp } from "lucide-react";
import ChatInput from "./ChatInput";
import ChartRenderer from "./ChartRenderer";
import ChartSwitcher from "./ChartSwitcher";
import Markdown from "./Markdown";
import { credentialService } from "../services/credentialService";
import { SelectableModelOption } from "../types/credential";
import { buildSelectableModelOptions } from "../utils/modelOptions";
import { subscribeSSE, type SSESubscription, type IntentPayload, type ClarificationOption, type TraceEventPayload } from "../utils/sse";
import { inferChartPool } from "../lib/chartPool";
import { isChartType, type ChartType } from "../lib/chartTypes";
import { AppMode } from "./ChatInterface";

interface BiAreaProps {
  isSidebarOpen: boolean;
  toggleSidebar: () => void;
  initialSessionId?: string | null;
  onFirstMessage?: (sessionId: string, title: string, mode: AppMode) => void;
}

interface InsightResult {
  question: string;
  summary: string;
  data: Record<string, unknown>[];
  chartType: string;
}

export type TraceSpan = {
  spanId: string;
  parentId?: string;
  name: string;
  status: "START" | "END";
  attributes?: Record<string, unknown>;
  children: TraceSpan[];
};

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
      traceEvents?: TraceEventPayload[];
      traceCollapsed?: boolean;
    };

const generateId = () => `${Date.now()}-${Math.random().toString(36).slice(2, 11)}`;

export function buildSpanTree(events: TraceEventPayload[]): TraceSpan[] {
  const spans = new Map<string, TraceSpan>();
  const order: string[] = [];

  for (const event of events) {
    if (event.status === "START") {
      const span: TraceSpan = {
        spanId: event.spanId,
        parentId: event.parentId,
        name: event.name,
        status: "START",
        attributes: event.attributes,
        children: [],
      };
      spans.set(event.spanId, span);
      order.push(event.spanId);
    } else {
      const existing = spans.get(event.spanId);
      if (existing) {
        existing.status = "END";
        existing.attributes = { ...existing.attributes, ...event.attributes };
      } else {
        const span: TraceSpan = {
          spanId: event.spanId,
          parentId: event.parentId,
          name: event.name,
          status: "END",
          attributes: event.attributes,
          children: [],
        };
        spans.set(event.spanId, span);
        order.push(event.spanId);
      }
    }
  }

  const roots: TraceSpan[] = [];
  for (const spanId of order) {
    const span = spans.get(spanId)!;
    if (span.parentId) {
      const parent = spans.get(span.parentId);
      if (parent) {
        parent.children.push(span);
        continue;
      }
    }
    roots.push(span);
  }
  return roots;
}

const SPAN_LABELS: Record<string, string> = {
  "semantic-context": "语义层加载",
  "intent-extraction": "意图提取",
  "llm-call": "LLM 调用",
  "sql-build": "SQL 构建",
  "query-execute": "查询执行",
  "insight-generation": "洞察生成",
};

function spanLabel(name: string): string {
  return SPAN_LABELS[name] ?? name;
}

function spanSummary(span: TraceSpan): string {
  const attrs = span.attributes ?? {};
  switch (span.name) {
    case "semantic-context": {
      const subjects = attrs.subjects;
      const subjectCount = Array.isArray(subjects) ? subjects.length : 0;
      return `${subjectCount} 个主题`;
    }
    case "intent-extraction": {
      const attemptCount = span.children.filter((c) => c.name === "llm-call").length;
      return attemptCount > 1 ? `${attemptCount} 次尝试` : "";
    }
    case "llm-call": {
      const attempt = attrs.attempt;
      const err = attrs.error as string | undefined;
      if (err) return `尝试 ${attempt} · 失败`;
      return attempt ? `尝试 ${attempt}` : "";
    }
    case "query-execute": {
      const rowCount = attrs.rowCount;
      return typeof rowCount === "number" ? `${rowCount} 行` : "";
    }
    case "insight-generation": {
      const chartType = attrs.chartType as string | undefined;
      return chartType ? `图表: ${chartType}` : "流式";
    }
    default:
      return "";
  }
}

function TraceSpanRow({ span, depth }: { span: TraceSpan; depth: number }) {
  const [expanded, setExpanded] = useState(false);
  const inProgress = span.status === "START";
  const summary = spanSummary(span);
  const hasAttributes = span.attributes != null && Object.keys(span.attributes).length > 0;
  const clickable = hasAttributes || span.children.length > 0;

  return (
    <div>
      <div
        className={`flex items-start gap-2 text-[13px] py-1 ${clickable ? "cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800/50 -mx-2 px-2 rounded" : ""}`}
        style={{ paddingLeft: `${depth * 16}px` }}
        onClick={() => clickable && setExpanded(!expanded)}
      >
        <span
          className={`mt-[3px] text-[10px] ${inProgress ? "text-blue-400 animate-pulse" : "text-gray-400 dark:text-gray-500"}`}
          aria-hidden
        >
          ●
        </span>
        <span className="font-medium text-gray-700 dark:text-gray-300 flex-1">{spanLabel(span.name)}</span>
        {summary && (
          <span className="text-gray-400 dark:text-gray-500 text-[12px]">{summary}</span>
        )}
        {clickable && (
          <span className="text-gray-300 dark:text-gray-600 mt-0.5">
            {expanded ? <ChevronUp size={12} /> : <ChevronDown size={12} />}
          </span>
        )}
      </div>
      {expanded && hasAttributes && (
        <pre
          className="text-[11px] text-gray-500 dark:text-gray-400 bg-gray-50 dark:bg-gray-800/40 rounded p-2 mt-1 mb-1 overflow-x-auto whitespace-pre-wrap break-all"
          style={{ marginLeft: `${depth * 16 + 22}px` }}
        >
          {JSON.stringify(span.attributes, null, 2)}
        </pre>
      )}
      {span.children.map((child) => (
        <TraceSpanRow key={child.spanId} span={child} depth={depth + 1} />
      ))}
    </div>
  );
}

function ThinkingBlock({
  events,
  progressText,
  intent,
  collapsed,
  onToggleCollapse,
}: {
  events?: TraceEventPayload[];
  progressText?: string;
  intent?: IntentPayload;
  collapsed: boolean;
  onToggleCollapse: () => void;
}) {
  const tree = useMemo(() => (events && events.length > 0 ? buildSpanTree(events) : []), [events]);
  const hasTrace = tree.length > 0;
  const spanCount = events?.length ?? 0;

  if (!hasTrace && !intent && !progressText) return null;

  const headerLabel = collapsed
    ? `思考过程 · ${spanCount} 事件`
    : "思考过程";

  return (
    <div className="pl-3 border-l-2 border-gray-200 dark:border-gray-800 py-1 mb-4">
      <button
        type="button"
        onClick={onToggleCollapse}
        className="flex items-center gap-2 text-xs font-medium text-gray-500 dark:text-gray-400 mb-2 hover:text-gray-700 dark:hover:text-gray-300 transition-colors"
      >
        {collapsed ? <ChevronRight size={13} /> : <ChevronDown size={13} />}
        <span>{headerLabel}</span>
      </button>
      {!collapsed && (
        <>
          {hasTrace ? (
            <div className="space-y-0.5 text-gray-500 dark:text-gray-400 mb-2">
              {tree.map((span) => (
                <TraceSpanRow key={span.spanId} span={span} depth={0} />
              ))}
            </div>
          ) : (
            <div className="flex items-center gap-2 text-xs text-gray-500 dark:text-gray-400 mb-2">
              <Brain size={13} className="animate-pulse" />
              <span>{progressText || "Analyzing intent..."}</span>
            </div>
          )}
          {intent && (
            <div className="space-y-1.5 text-[13px] text-gray-500 dark:text-gray-400">
              <div>
                <span className="text-gray-400/80">Subject: </span>
                <span className="font-medium text-gray-700 dark:text-gray-300">{intent.subject}</span>
              </div>
              {intent.metrics.length > 0 && (
                <div>
                  <span className="text-gray-400/80">Metrics: </span>
                  <span className="font-medium text-gray-700 dark:text-gray-300">{intent.metrics.join(", ")}</span>
                </div>
              )}
              {intent.dimensions.length > 0 && (
                <div>
                  <span className="text-gray-400/80">Dimensions: </span>
                  <span className="font-medium text-gray-700 dark:text-gray-300">
                    {intent.dimensions.map((d) => d.name).join(", ")}
                  </span>
                </div>
              )}
              {intent.filters.length > 0 && (
                <div>
                  <span className="text-gray-400/80">Filters: </span>
                  <span className="font-medium text-gray-700 dark:text-gray-300">
                    {intent.filters.map((f) => `${f.dimension} ${f.operator} ${f.value}`).join("; ")}
                  </span>
                </div>
              )}
            </div>
          )}
        </>
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
    <div className="p-4 bg-gray-50/50 dark:bg-gray-800/20 rounded-2xl border border-gray-100 dark:border-gray-800/50 mt-2">
      <div className="flex items-start gap-2 text-sm font-medium text-gray-700 dark:text-gray-300 mb-3">
        <HelpCircle size={18} className="text-blue-500 mt-0.5 flex-shrink-0" />
        <span className="leading-relaxed">{message}</span>
      </div>
      <div className="flex flex-col gap-2">
        {options.map((opt) => (
          <button
            key={opt.value}
            onClick={() => onSelect(opt.value)}
            className="flex items-center justify-between w-full p-3 text-left bg-white dark:bg-gray-900 border border-gray-200 dark:border-gray-800 rounded-xl hover:border-blue-400 dark:hover:border-blue-500 hover:shadow-sm transition-all group"
          >
            <div className="pr-4">
              <div className="font-medium text-sm text-gray-800 dark:text-gray-200 group-hover:text-blue-600 dark:group-hover:text-blue-400 transition-colors">
                {opt.label}
              </div>
              {opt.description && (
                <div className="text-[13px] text-gray-500 mt-1 leading-relaxed">
                  {opt.description}
                </div>
              )}
            </div>
            <div className="flex-shrink-0 w-6 h-6 rounded-full bg-gray-50 dark:bg-gray-800 flex items-center justify-center group-hover:bg-blue-50 dark:group-hover:bg-blue-900/30 transition-colors">
              <ChevronRight size={14} className="text-gray-400 group-hover:text-blue-500 transition-colors" />
            </div>
          </button>
        ))}
      </div>
    </div>
  );
}

export default function BiArea({ isSidebarOpen, toggleSidebar, initialSessionId, onFirstMessage }: BiAreaProps) {
  const [messages, setMessages] = useState<BiMessage[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [modelOptions, setModelOptions] = useState<SelectableModelOption[]>([]);
  const [selectedModel, setSelectedModel] = useState<SelectableModelOption | null>(null);
  const bottomRef = useRef<HTMLDivElement>(null);
  const conversationSessionIdRef = useRef<string>(initialSessionId || generateId());
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
    if (initialSessionId) {
      conversationSessionIdRef.current = initialSessionId;
    }
  }, [initialSessionId]);

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

    const isFirstMessage = messages.length === 0;
    if (isFirstMessage && onFirstMessage) {
      onFirstMessage(conversationSessionIdRef.current, content, "bi");
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
      onTrace: (event) => {
        setMessages((prev) =>
          prev.map((msg) => {
            if (msg.id !== assistantMsgId || msg.role !== "assistant") return msg;
            const next = [...(msg.traceEvents ?? []), event];
            return { ...msg, traceEvents: next };
          })
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
        setMessages((prev) =>
          prev.map((msg) => {
            if (msg.id !== assistantMsgId || msg.role !== "assistant") return msg;
            if ((msg.traceEvents?.length ?? 0) === 0) return msg;
            return { ...msg, traceCollapsed: true };
          })
        );
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
                <div key={msg.id} className="flex justify-end w-full group mb-2">
                  <div className="max-w-[85%] text-right">
                    <div className="text-foreground text-[15px] leading-relaxed whitespace-pre-wrap bg-gray-100 dark:bg-[#1E1F20] px-4 py-2.5 rounded-2xl rounded-tr-sm inline-block text-left">
                      {msg.content}
                    </div>
                  </div>
                </div>
              );
            }

            if (msg.role === "assistant") {
              const toggleTraceCollapse = () => {
                setMessages((prev) =>
                  prev.map((m) =>
                    m.id === msg.id && m.role === "assistant"
                      ? { ...m, traceCollapsed: !m.traceCollapsed }
                      : m
                  )
                );
              };
              const renderAssistantContent = () => {
                if (msg.status === "loading") {
                  return (
                    <div className="space-y-3">
                      <ThinkingBlock
                        events={msg.traceEvents}
                        intent={msg.intent}
                        progressText={msg.progressText}
                        collapsed={msg.traceCollapsed ?? false}
                        onToggleCollapse={toggleTraceCollapse}
                      />
                      <div className="flex items-center py-2">
                        <Loader2 size={18} className="animate-spin text-gray-400" />
                        <span className="ml-3 text-sm text-gray-500">
                          {msg.progressText ?? "Analyzing..."}
                        </span>
                      </div>
                    </div>
                  );
                }

                if (msg.status === "clarification" && msg.clarification) {
                  return (
                    <div className="space-y-3">
                      <ThinkingBlock
                        events={msg.traceEvents}
                        intent={msg.intent}
                        progressText={msg.progressText}
                        collapsed={msg.traceCollapsed ?? false}
                        onToggleCollapse={toggleTraceCollapse}
                      />
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
                    <div className="space-y-3">
                      <ThinkingBlock
                        events={msg.traceEvents}
                        intent={msg.intent}
                        progressText={msg.progressText}
                        collapsed={msg.traceCollapsed ?? false}
                        onToggleCollapse={toggleTraceCollapse}
                      />
                      <div className="text-foreground leading-7 text-[15px]">
                        <Markdown content={msg.streamingText ?? ""} />
                      </div>
                    </div>
                  );
                }

                if (msg.status === "error") {
                  return (
                    <div className="space-y-3">
                      <ThinkingBlock
                        events={msg.traceEvents}
                        intent={msg.intent}
                        progressText={msg.progressText}
                        collapsed={msg.traceCollapsed ?? false}
                        onToggleCollapse={toggleTraceCollapse}
                      />
                      <div className="px-4 py-3 bg-red-50/50 dark:bg-red-900/10 border border-red-100 dark:border-red-900/50 rounded-lg text-sm text-red-600 dark:text-red-400">
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
                    <div className="space-y-4">
                      <ThinkingBlock
                        events={msg.traceEvents}
                        intent={msg.intent}
                        progressText={msg.progressText}
                        collapsed={msg.traceCollapsed ?? false}
                        onToggleCollapse={toggleTraceCollapse}
                      />
                      {/* Summary */}
                      <div className="text-foreground leading-7 text-[15px]">
                        <Markdown content={result.summary} />
                      </div>

                      {/* Chart Area */}
                      {showChart && msg.intent && (
                        <div className="p-4 rounded-xl border border-gray-100 dark:border-gray-800 bg-white dark:bg-black/20 shadow-sm">
                          <ChartRenderer
                            chartType={msg.activeChart!}
                            data={result.data}
                            intent={msg.intent}
                          />
                          {pool.length > 1 && (
                            <div className="mt-4 pt-4 border-t border-gray-100 dark:border-gray-800">
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
                            </div>
                          )}
                        </div>
                      )}

                      {!showChart && pool.length === 0 && (
                        <div className="p-4 text-center text-sm text-gray-500 rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50/50 dark:bg-gray-900/20">
                          维度过多，暂不支持自动可视化
                        </div>
                      )}

                      {/* Data Table */}
                      {result.data && result.data.length > 0 && msg.activeChart !== "single_value" && (
                        <div className="overflow-x-auto overflow-y-auto max-h-80 rounded-xl border border-gray-100 dark:border-gray-800 bg-white dark:bg-black/20 shadow-sm">
                          <table className="w-full text-sm">
                            <thead>
                              <tr className="bg-gray-50/50 dark:bg-gray-900/50 sticky top-0 border-b border-gray-100 dark:border-gray-800">
                                {Object.keys(result.data[0]).map((key) => (
                                  <th key={key} className="px-4 py-3 text-left font-medium text-gray-500 dark:text-gray-400">
                                    {key}
                                  </th>
                                ))}
                              </tr>
                            </thead>
                            <tbody>
                              {result.data.map((row, i) => (
                                <tr
                                  key={i}
                                  className="border-b border-gray-50 dark:border-gray-800/50 last:border-0 hover:bg-gray-50/50 dark:hover:bg-gray-800/30 transition-colors"
                                >
                                  {Object.values(row).map((val, j) => (
                                    <td key={j} className="px-4 py-3 text-gray-700 dark:text-gray-300">
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
                        <div className="text-center py-8 text-gray-400 text-sm">No data returned.</div>
                      )}
                    </div>
                  );
                }
                return null;
              };

              return (
                <div key={msg.id} className="flex gap-4 w-full group">
                  <div className="flex-shrink-0 w-8 h-8 rounded-full bg-blue-50 dark:bg-blue-900/30 flex items-center justify-center border border-blue-100 dark:border-blue-800 mt-1">
                    <BarChart3 size={16} className="text-blue-600 dark:text-blue-400" />
                  </div>
                  <div className="flex-1 space-y-2 overflow-hidden">
                    <div className="flex items-center gap-2">
                      <span className="font-medium text-sm text-foreground">BI Agent</span>
                    </div>
                    {renderAssistantContent()}
                  </div>
                </div>
              );
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
