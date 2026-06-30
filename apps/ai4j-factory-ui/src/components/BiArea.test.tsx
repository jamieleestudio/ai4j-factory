import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import BiArea from "./BiArea";

vi.mock("../services/credentialService", () => ({
  credentialService: {
    getCredentials: vi.fn().mockResolvedValue([
      {
        id: 1,
        userId: "test-user",
        provider: { id: 1, name: "OpenAI", code: "openai", description: "OpenAI" },
        apiKey: "test-key",
        status: "VALID",
        enabled: true,
        createdAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      },
    ]),
    getConfigs: vi.fn().mockResolvedValue([
      {
        id: 10,
        name: "GPT Test",
        provider: { id: 1, name: "OpenAI", code: "openai", description: "OpenAI" },
        modelName: "gpt-test",
        parameters: null,
        version: null,
        createdAt: "2024-01-01T00:00:00Z",
        updatedAt: "2024-01-01T00:00:00Z",
      },
    ]),
  },
}));

function createSSEResponse(events: object[]): Response {
  const encoder = new TextEncoder();
  const payload = events.map((e) => `data: ${JSON.stringify(e)}\n`).join("");
  const stream = new ReadableStream({
    start(controller) {
      controller.enqueue(encoder.encode(payload));
      controller.close();
    },
  });
  return {
    ok: true,
    status: 200,
    statusText: "OK",
    body: stream,
  } as unknown as Response;
}

function biEvents(summary: string, data: Record<string, unknown>[], chartType: string) {
  return [
    { type: "status", stage: "analyzing", message: "正在分析你的问题..." },
    {
      type: "intent",
      subject: "orders",
      metrics: ["sales_amount"],
      dimensions: ["region"],
      filters: [{ dimension: "region", operator: "=", value: "华东" }],
    },
    { type: "status", stage: "querying", message: "正在查询数据库..." },
    { type: "status", stage: "insight", message: `查询到 ${data.length} 条记录，正在生成洞察...` },
    { type: "chunk", content: summary },
    { type: "result", chartType, data, rowCount: data.length },
    { type: "done" },
  ];
}

describe("BiArea", () => {
  let mockFetch: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    mockFetch = vi.fn();
    vi.stubGlobal("fetch", mockFetch);
    Element.prototype.scrollIntoView = vi.fn();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  test("keeps both questions and results after two BI queries", async () => {
    const user = userEvent.setup();

    mockFetch
      .mockResolvedValueOnce(
        createSSEResponse(
          biEvents("华东区销售额为 100。", [{ region: "华东", amount: 100 }], "single_value")
        )
      )
      .mockResolvedValueOnce(
        createSSEResponse(
          biEvents("华南区销售额为 200。", [{ region: "华南", amount: 200 }], "bar")
        )
      );

    const { container } = render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");

    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(container.textContent).toContain("华东区销售额多少");
    });
    await waitFor(() => {
      expect(container.textContent).toContain("华东区销售额为 100。");
    });

    await user.type(input, "华南区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(container.textContent).toContain("华南区销售额多少");
    });
    await waitFor(() => {
      expect(container.textContent).toContain("华南区销售额为 200。");
    });

    expect(container.textContent).toContain("华东区销售额多少");
    expect(container.textContent).toContain("华东区销售额为 100。");
  });

  test("renders intent thinking block and does not leak chart marker", async () => {
    const user = userEvent.setup();

    mockFetch.mockResolvedValueOnce(
      createSSEResponse(
        biEvents("华东区销售额为 100。", [{ region: "华东", amount: 100 }], "single_value")
      )
    );

    const { container } = render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");

    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(container.textContent).toContain("华东区销售额为 100。");
    });

    // Intent thinking block renders semantic layer
    expect(container.textContent).toContain("orders");
    expect(container.textContent).toContain("sales_amount");
    expect(container.textContent).toContain("region");
    expect(container.textContent).toContain("Thinking");

    // No chart marker leaks into the rendered output
    expect(container.textContent).not.toContain("<<CHART:");
  });
});
