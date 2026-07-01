import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import { parseSSELine, parseSSEPayload, subscribeSSE } from "./sse";

describe("parseSSELine", () => {
  test("parses status event with space after data:", () => {
    const result = parseSSELine('data: {"type":"status","stage":"analyzing","message":"正在分析你的问题..."}');
    expect(result).toEqual({
      type: "status",
      stage: "analyzing",
      message: "正在分析你的问题...",
    });
  });

  test("parses status event without space after data:", () => {
    const result = parseSSELine('data:{"type":"status","stage":"querying","message":"正在查询数据库..."}');
    expect(result).toEqual({
      type: "status",
      stage: "querying",
      message: "正在查询数据库...",
    });
  });

  test("parses intent event", () => {
    const result = parseSSELine(
      'data: {"type":"intent","subject":"orders","metrics":["sales_amount"],"dimensions":[{"name":"region","type":"STRING"}],"filters":[{"dimension":"region","operator":"=","value":"华东"}]}'
    );
    expect(result).toEqual({
      type: "intent",
      subject: "orders",
      metrics: ["sales_amount"],
      dimensions: [{ name: "region", type: "STRING" }],
      filters: [{ dimension: "region", operator: "=", value: "华东" }],
    });
  });

  test("parses chunk event", () => {
    const result = parseSSELine('data: {"type":"chunk","content":"当前"}');
    expect(result).toEqual({ type: "chunk", content: "当前" });
  });

  test("parses result event", () => {
    const result = parseSSELine(
      'data: {"type":"result","chartType":"single_value","data":[{"销售额":4423860.5}],"rowCount":1}'
    );
    expect(result).toEqual({
      type: "result",
      chartType: "single_value",
      data: [{ 销售额: 4423860.5 }],
      rowCount: 1,
    });
  });

  test("parses error event", () => {
    const result = parseSSELine('data: {"type":"error","message":"Something went wrong"}');
    expect(result).toEqual({ type: "error", message: "Something went wrong" });
  });

  test("parses done event", () => {
    const result = parseSSELine('data: {"type":"done"}');
    expect(result).toEqual({ type: "done" });
  });

  test("parses clarification event", () => {
    const result = parseSSELine(
      'data: {"type":"clarification","sessionId":"abc-123","message":"请选择主题","options":[{"label":"订单分析","value":"订单分析","description":"订单数据"}]}'
    );
    expect(result).toEqual({
      type: "clarification",
      sessionId: "abc-123",
      message: "请选择主题",
      options: [{ label: "订单分析", value: "订单分析", description: "订单数据" }],
    });
  });

  test("returns null for non-data lines", () => {
    expect(parseSSELine("event: message")).toBeNull();
  });

  test("returns null for empty line", () => {
    expect(parseSSELine("")).toBeNull();
  });

  test("returns null for invalid JSON", () => {
    expect(parseSSELine("data: not-json")).toBeNull();
  });

  test("returns null when type field missing", () => {
    expect(parseSSELine('data: {"foo":"bar"}')).toBeNull();
  });
});

describe("parseSSEPayload", () => {
  test("returns null for non-object", () => {
    expect(parseSSEPayload('"string"')).toBeNull();
    expect(parseSSEPayload("42")).toBeNull();
    expect(parseSSEPayload("null")).toBeNull();
  });

  test("returns null for object without string type", () => {
    expect(parseSSEPayload('{"type":123}')).toBeNull();
  });
});

type MockEventSource = {
  onmessage: ((e: { data: string }) => void) | null;
  onerror: (() => void) | null;
  close: ReturnType<typeof vi.fn>;
};

function installMockEventSource(): { instance: MockEventSource; ctor: typeof EventSource } {
  const instance: MockEventSource = {
    onmessage: null,
    onerror: null,
    close: vi.fn(),
  };
  const ctor = vi.fn(() => instance as unknown as EventSource) as unknown as typeof EventSource;
  global.EventSource = ctor;
  return { instance, ctor };
}

describe("subscribeSSE", () => {
  let originalEventSource: typeof EventSource;

  beforeEach(() => {
    originalEventSource = global.EventSource;
  });

  afterEach(() => {
    global.EventSource = originalEventSource;
    vi.restoreAllMocks();
  });

  test("constructs EventSource with withCredentials and dispatches onmessage to callbacks", () => {
    const { ctor, instance } = installMockEventSource();

    const onStatus = vi.fn();
    const onIntent = vi.fn();
    const onChunk = vi.fn();
    const onResult = vi.fn();
    const onClarification = vi.fn();
    const onError = vi.fn();
    const onDone = vi.fn();

    subscribeSSE("http://example.com/stream", {
      onStatus, onIntent, onChunk, onResult, onClarification, onError, onDone,
    });

    expect(ctor).toHaveBeenCalledTimes(1);
    expect(ctor).toHaveBeenCalledWith("http://example.com/stream", { withCredentials: true });

    instance.onmessage!({ data: '{"type":"status","stage":"analyzing","message":"thinking"}' });
    expect(onStatus).toHaveBeenCalledWith("analyzing", "thinking");

    instance.onmessage!({ data: '{"type":"intent","subject":"s","metrics":[],"dimensions":[],"filters":[]}' });
    expect(onIntent).toHaveBeenCalledWith({ subject: "s", metrics: [], dimensions: [], filters: [] });

    instance.onmessage!({ data: '{"type":"chunk","content":"hi"}' });
    expect(onChunk).toHaveBeenCalledWith("hi");

    instance.onmessage!({ data: '{"type":"result","chartType":"bar","data":[],"rowCount":0}' });
    expect(onResult).toHaveBeenCalledWith({ chartType: "bar", data: [], rowCount: 0 });

    instance.onmessage!({ data: '{"type":"clarification","sessionId":"s1","message":"pick","options":[]}' });
    expect(onClarification).toHaveBeenCalledWith({ sessionId: "s1", message: "pick", options: [] });

    instance.onmessage!({ data: '{"type":"error","message":"boom"}' });
    expect(onError).toHaveBeenCalledWith("boom");

    instance.onmessage!({ data: '{"type":"done"}' });
    expect(onDone).toHaveBeenCalled();
  });

  test("onerror triggers onError, onDone, and close (prevents auto-reconnect)", () => {
    const { instance } = installMockEventSource();

    const onError = vi.fn();
    const onDone = vi.fn();
    const sub = subscribeSSE("http://example.com/stream", { onError, onDone });

    instance.onerror!();

    expect(onError).toHaveBeenCalledWith("SSE connection error");
    expect(onDone).toHaveBeenCalledTimes(1);
    expect(instance.close).toHaveBeenCalledTimes(1);

    sub.close();
    expect(instance.close).toHaveBeenCalledTimes(1);
  });

  test("close is idempotent and prevents further callbacks", () => {
    const { instance } = installMockEventSource();

    const onChunk = vi.fn();
    const sub = subscribeSSE("http://example.com/stream", { onChunk });

    sub.close();
    expect(instance.close).toHaveBeenCalledTimes(1);

    instance.onmessage!({ data: '{"type":"chunk","content":"after-close"}' });
    expect(onChunk).not.toHaveBeenCalled();
  });

  test("onerror after explicit close does not trigger callbacks", () => {
    const { instance } = installMockEventSource();

    const onError = vi.fn();
    const onDone = vi.fn();
    const sub = subscribeSSE("http://example.com/stream", { onError, onDone });

    sub.close();
    onError.mockClear();
    onDone.mockClear();

    instance.onerror!();
    expect(onError).not.toHaveBeenCalled();
    expect(onDone).not.toHaveBeenCalled();
  });
});
