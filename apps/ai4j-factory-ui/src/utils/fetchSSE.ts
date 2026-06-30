export type IntentFilter = {
  dimension: string;
  operator: string;
  value: string;
};

export type IntentPayload = {
  subject: string;
  metrics: string[];
  dimensions: string[];
  filters: IntentFilter[];
};

export type ResultPayload = {
  chartType: string;
  data: Record<string, unknown>[];
  rowCount: number;
};

export type SseEvent =
  | { type: "status"; stage: string; message: string }
  | { type: "intent"; subject: string; metrics: string[]; dimensions: string[]; filters: IntentFilter[] }
  | { type: "chunk"; content: string }
  | { type: "result"; chartType: string; data: Record<string, unknown>[]; rowCount: number }
  | { type: "error"; message: string }
  | { type: "done" };

export interface SSECallbacks {
  onStatus?: (stage: string, message: string) => void;
  onIntent?: (intent: IntentPayload) => void;
  onChunk?: (content: string) => void;
  onResult?: (result: ResultPayload) => void;
  onError?: (message: string) => void;
  onDone?: () => void;
}

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
    case "error":
      callbacks.onError?.(event.message);
      break;
    case "done":
      callbacks.onDone?.();
      break;
  }
}

export async function fetchSSE(
  url: string,
  body: unknown,
  callbacks: SSECallbacks
): Promise<void> {
  const response = await fetch(url, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });

  if (!response.ok) {
    callbacks.onError?.(`HTTP ${response.status}: ${response.statusText}`);
    callbacks.onDone?.();
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    callbacks.onError?.("Response body is not readable");
    callbacks.onDone?.();
    return;
  }

  const decoder = new TextDecoder();
  let buffer = "";

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });
      const lines = buffer.split("\n");
      buffer = lines.pop() ?? "";

      for (const line of lines) {
        const trimmed = line.trim();
        if (!trimmed) continue;

        const parsed = parseSSELine(trimmed);
        if (!parsed) continue;

        dispatch(parsed, callbacks);

        // Yield to the browser so React can paint each chunk individually,
        // producing a character-by-character streaming effect.
        await new Promise((r) => setTimeout(r, 0));
      }
    }
  } catch (err) {
    callbacks.onError?.(err instanceof Error ? err.message : "Stream read failed");
  } finally {
    reader.releaseLock();
    callbacks.onDone?.();
  }
}
