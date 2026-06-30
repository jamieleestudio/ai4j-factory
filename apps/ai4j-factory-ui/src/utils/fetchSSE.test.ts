import { describe, expect, test } from "vitest";
import { parseSSELine } from "./fetchSSE";

describe("parseSSELine", () => {
  test("parses progress with space after data:", () => {
    const result = parseSSELine("data: [progress] 正在分析你的问题...");
    expect(result).toEqual({ type: "progress", content: "正在分析你的问题..." });
  });

  test("parses progress without space after data:", () => {
    const result = parseSSELine("data:[progress] 正在分析你的问题...");
    expect(result).toEqual({ type: "progress", content: "正在分析你的问题..." });
  });

  test("parses chunk without space after data:", () => {
    const result = parseSSELine("data:[chunk] 当前");
    expect(result).toEqual({ type: "chunk", content: "当前" });
  });

  test("parses result without space after data:", () => {
    const result = parseSSELine(
      'data:[result] {"chartType":"single_value","data":[{"销售额":4423860.50}]}'
    );
    expect(result).toEqual({
      type: "result",
      content: '{"chartType":"single_value","data":[{"销售额":4423860.50}]}',
    });
  });

  test("parses error without space after data:", () => {
    const result = parseSSELine("data:[error] Something went wrong");
    expect(result).toEqual({ type: "error", content: "Something went wrong" });
  });

  test("returns null for non-data lines", () => {
    expect(parseSSELine("event: message")).toBeNull();
  });

  test("returns null for empty line", () => {
    expect(parseSSELine("")).toBeNull();
  });
});
