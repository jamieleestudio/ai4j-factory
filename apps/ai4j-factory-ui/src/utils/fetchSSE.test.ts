import { describe, expect, test } from "vitest";
import { parseSSELine, parseSSEPayload } from "./fetchSSE";

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
      'data: {"type":"intent","subject":"orders","metrics":["sales_amount"],"dimensions":["region"],"filters":[{"dimension":"region","operator":"=","value":"华东"}]}'
    );
    expect(result).toEqual({
      type: "intent",
      subject: "orders",
      metrics: ["sales_amount"],
      dimensions: ["region"],
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
