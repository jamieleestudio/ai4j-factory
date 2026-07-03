export type IntentDimension = {
  name: string;
  type: string;
};

export type IntentFilter = {
  dimension: string;
  operator: string;
  value: string;
};

export type IntentPayload = {
  subject: string;
  metrics: string[];
  dimensions: IntentDimension[];
  filters: IntentFilter[];
};

export type ResultPayload = {
  chartType: string;
  data: Record<string, unknown>[];
  rowCount: number;
};

export type ClarificationOption = {
  label: string;
  value: string;
  description?: string;
};

export type ClarificationPayload = {
  sessionId: string;
  message: string;
  options: ClarificationOption[];
};

export type TraceEventPayload = {
  type: "trace";
  spanId: string;
  parentId?: string;
  name: string;
  status: "START" | "END";
  attributes?: Record<string, unknown>;
};

export type SseEvent =
  | { type: "status"; stage: string; message: string }
  | { type: "intent"; subject: string; metrics: string[]; dimensions: IntentDimension[]; filters: IntentFilter[] }
  | { type: "chunk"; content: string }
  | { type: "result"; chartType: string; data: Record<string, unknown>[]; rowCount: number }
  | { type: "clarification"; sessionId: string; message: string; options: ClarificationOption[] }
  | { type: "error"; message: string }
  | { type: "trace"; spanId: string; parentId?: string; name: string; status: "START" | "END"; attributes?: Record<string, unknown> }
  | { type: "done" };

export interface SSECallbacks {
  onStatus?: (stage: string, message: string) => void;
  onIntent?: (intent: IntentPayload) => void;
  onChunk?: (content: string) => void;
  onResult?: (result: ResultPayload) => void;
  onClarification?: (clarification: ClarificationPayload) => void;
  onTrace?: (event: TraceEventPayload) => void;
  onError?: (message: string) => void;
  onDone?: () => void;
}

export type SSESubscription = {
  close: () => void;
};

export function parseSSEPayload(data: string): SseEvent | null {
  let parsed: unknown;
  try {
    parsed = JSON.parse(data);
  } catch {
    return null;
  }
  if (parsed == null || typeof parsed !== "object" || typeof (parsed as { type?: unknown }).type !== "string") {
    return null;
  }
  return parsed as SseEvent;
}

export function parseSSELine(line: string): SseEvent | null {
  if (!line.startsWith("data:")) return null;
  let data = line.slice(5);
  if (data.startsWith(" ")) data = data.slice(1);
  return parseSSEPayload(data);
}

function dispatch(event: SseEvent, callbacks: SSECallbacks): void {
  switch (event.type) {
    case "status":
      callbacks.onStatus?.(event.stage, event.message);
      break;
    case "intent":
      callbacks.onIntent?.({
        subject: event.subject,
        metrics: event.metrics,
        dimensions: event.dimensions,
        filters: event.filters,
      });
      break;
    case "chunk":
      callbacks.onChunk?.(event.content);
      break;
    case "result":
      callbacks.onResult?.({
        chartType: event.chartType,
        data: event.data,
        rowCount: event.rowCount,
      });
      break;
    case "clarification":
      callbacks.onClarification?.({
        sessionId: event.sessionId,
        message: event.message,
        options: event.options,
      });
      break;
    case "trace":
      callbacks.onTrace?.({
        type: "trace",
        spanId: event.spanId,
        parentId: event.parentId,
        name: event.name,
        status: event.status,
        attributes: event.attributes,
      });
      break;
    case "error":
      callbacks.onError?.(event.message);
      break;
    case "done":
      callbacks.onDone?.();
      break;
  }
}

export function subscribeSSE(url: string, callbacks: SSECallbacks): SSESubscription {
  const es = new EventSource(url, { withCredentials: true });
  let closed = false;

  const close = () => {
    if (closed) return;
    closed = true;
    es.close();
  };

  es.onmessage = (e) => {
    if (closed) return;
    const event = parseSSEPayload(e.data);
    if (!event) return;
    dispatch(event, callbacks);
  };

  es.onerror = () => {
    if (closed) return;
    callbacks.onError?.("SSE connection error");
    callbacks.onDone?.();
    close();
  };

  return { close };
}
