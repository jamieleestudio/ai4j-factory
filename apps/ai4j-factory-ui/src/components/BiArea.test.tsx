import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import BiArea from "./BiArea";

vi.mock("./EChart", () => ({
  default: () => <div data-testid="echart-mock" />,
}));

vi.mock("./ChartRenderer", () => ({
  default: ({ chartType }: { chartType: string }) => (
    <div data-testid="chart-renderer" data-chart-type={chartType} />
  ),
}));

vi.mock("./Markdown", () => ({
  default: ({ content }: { content: string }) => <div data-testid="markdown">{content}</div>,
}));

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

type MockEventSource = {
  url: string;
  onmessage: ((e: { data: string }) => void) | null;
  onerror: (() => void) | null;
  close: ReturnType<typeof vi.fn>;
};

function installMockEventSource(): {
  instances: MockEventSource[];
} {
  const instances: MockEventSource[] = [];
  const ctor = vi.fn((url: string) => {
    const inst: MockEventSource = {
      url,
      onmessage: null,
      onerror: null,
      close: vi.fn(),
    };
    instances.push(inst);
    return inst as unknown as EventSource;
  }) as unknown as typeof EventSource;
  global.EventSource = ctor;
  return { instances };
}

function emit(instance: MockEventSource, event: object): void {
  instance.onmessage?.({ data: JSON.stringify(event) });
}

function biEvents(
  summary: string,
  data: Record<string, unknown>[],
  chartType: string,
  dimensions: { name: string; type: string }[] = [{ name: "region", type: "STRING" }]
) {
  return [
    { type: "status", stage: "analyzing", message: "正在分析你的问题..." },
    { type: "intent", subject: "orders", metrics: ["sales_amount"], dimensions, filters: [{ dimension: "region", operator: "=", value: "华东" }] },
    { type: "status", stage: "querying", message: "正在查询数据库..." },
    { type: "status", stage: "insight", message: `查询到 ${data.length} 条记录，正在生成洞察...` },
    { type: "chunk", content: summary },
    { type: "result", chartType, data, rowCount: data.length },
    { type: "done" },
  ];
}

function biEventsWithTrace(
  summary: string,
  data: Record<string, unknown>[],
  chartType: string
) {
  return [
    { type: "status", stage: "analyzing", message: "正在分析你的问题..." },
    { type: "trace", spanId: "semantic-context", name: "semantic-context", status: "END", attributes: { subjects: [{ name: "orders" }] } },
    { type: "trace", spanId: "intent-extraction", name: "intent-extraction", status: "START" },
    { type: "trace", spanId: "llm-call-1", parentId: "intent-extraction", name: "llm-call", status: "START", attributes: { attempt: 1 } },
    { type: "trace", spanId: "llm-call-1", parentId: "intent-extraction", name: "llm-call", status: "END", attributes: { attempt: 1, rawOutput: "{}" } },
    { type: "trace", spanId: "intent-extraction", name: "intent-extraction", status: "END" },
    { type: "trace", spanId: "sql-build", name: "sql-build", status: "END" },
    { type: "intent", subject: "orders", metrics: ["sales_amount"], dimensions: [{ name: "region", type: "STRING" }], filters: [] },
    { type: "status", stage: "querying", message: "正在查询数据库..." },
    { type: "trace", spanId: "query-execute", name: "query-execute", status: "START" },
    { type: "trace", spanId: "query-execute", name: "query-execute", status: "END", attributes: { rowCount: 1 } },
    { type: "status", stage: "insight", message: `查询到 ${data.length} 条记录，正在生成洞察...` },
    { type: "trace", spanId: "insight-generation", name: "insight-generation", status: "START" },
    { type: "chunk", content: summary },
    { type: "trace", spanId: "insight-generation", name: "insight-generation", status: "END", attributes: { chartType } },
    { type: "result", chartType, data, rowCount: data.length },
    { type: "done" },
  ];
}

function clarificationEvents(sessionId: string, message: string, options: { label: string; value: string; description: string }[]) {
  return [
    { type: "status", stage: "analyzing", message: "正在分析你的问题..." },
    { type: "clarification", sessionId, message, options },
    { type: "done" },
  ];
}

function feedEvents(instance: MockEventSource, events: object[]): void {
  for (const e of events) emit(instance, e);
}

describe("BiArea", () => {
  let originalEventSource: typeof EventSource;

  beforeEach(() => {
    originalEventSource = global.EventSource;
    Element.prototype.scrollIntoView = vi.fn();
  });

  afterEach(() => {
    global.EventSource = originalEventSource;
    vi.clearAllMocks();
  });

  test("keeps both questions and results after two BI queries", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");

    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    feedEvents(instances[0], biEvents("华东区销售额为 100。", [{ region: "华东", amount: 100 }], "single_value"));

    await waitFor(() => {
      const markdowns = screen.getAllByTestId("markdown");
      expect(markdowns[0].textContent).toContain("华东区销售额为 100。");
    });

    await user.type(input, "华南区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(2);
    });
    feedEvents(instances[1], biEvents("华南区销售额为 200。", [{ region: "华南", amount: 200 }], "bar"));

    await waitFor(() => {
      const markdowns = screen.getAllByTestId("markdown");
      expect(markdowns[markdowns.length - 1].textContent).toContain("华南区销售额为 200。");
    });
  });

  test("renders intent thinking block and does not leak chart marker", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    const { container } = render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");

    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    feedEvents(instances[0], biEvents("华东区销售额为 100。", [{ region: "华东", amount: 100 }], "single_value"));

    await waitFor(() => {
      expect(screen.getByTestId("markdown").textContent).toContain("华东区销售额为 100。");
    });

    expect(container.textContent).toContain("orders");
    expect(container.textContent).toContain("sales_amount");
    expect(container.textContent).toContain("region");
    expect(container.textContent).toContain("思考过程");

    expect(container.textContent).not.toContain("<<CHART:");
  });

  test("renders clarification chips and sends sessionId on chip click", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");

    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "1");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    feedEvents(instances[0], clarificationEvents("session-abc", "请选择您想分析的数据主题", [
      { label: "订单分析", value: "订单分析", description: "订单数据" },
      { label: "用户分析", value: "用户分析", description: "用户数据" },
    ]));

    const chip = await screen.findByRole("button", { name: /订单分析/ });
    expect(chip).toBeInTheDocument();

    await user.click(chip);

    await waitFor(() => {
      expect(instances.length).toBe(2);
    });
    // Second EventSource URL includes sessionId and chip value as question
    expect(instances[1].url).toContain("sessionId=session-abc");
    expect(instances[1].url).toContain("question=" + encodeURIComponent("订单分析"));

    feedEvents(instances[1], biEvents("订单销售额为 100。", [{ 销售额: 100 }], "single_value"));

    await waitFor(() => {
      expect(screen.getByTestId("markdown").textContent).toContain("订单销售额为 100。");
    });
  });

  test("clicking chart chip switches active chart without fetching", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");
    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "各区销售额");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    feedEvents(instances[0], biEvents("各区销售额对比。", [
      { region: "华东", sales_amount: 100 },
      { region: "华北", sales_amount: 200 },
    ], "bar"));

    await waitFor(() => {
      expect(screen.getByTestId("chart-renderer").dataset.chartType).toBe("bar");
    });

    const pieChip = screen.getByText("饼图");
    await user.click(pieChip);

    await waitFor(() => {
      expect(screen.getByTestId("chart-renderer").dataset.chartType).toBe("pie");
    });

    expect(instances.length).toBe(1);
  });

  test("falls back when LLM recommends chartType not in candidate pool", async () => {
    const user = userEvent.setup();
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {});
    const { instances } = installMockEventSource();

    render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");
    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "各区域各产品线销售额");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    feedEvents(instances[0], biEvents(
      "各区域各产品线销售额。",
      [
        { region: "华东", product: "A", sales_amount: 100 },
        { region: "华北", product: "A", sales_amount: 200 },
      ],
      "pie",
      [
        { name: "region", type: "STRING" },
        { name: "product", type: "STRING" },
      ]
    ));

    await waitFor(() => {
      expect(screen.getByTestId("chart-renderer").dataset.chartType).toBe("grouped_bar");
    });

    expect(warnSpy).toHaveBeenCalledWith(
      expect.stringContaining("not in candidate pool")
    );

    warnSpy.mockRestore();
  });

  test("hides data table for single_value (0 dimension) scenario", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    const { container } = render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");
    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "总销售额");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    feedEvents(instances[0], biEvents(
      "总销售额为 1000。",
      [{ sales_amount: 1000 }],
      "single_value",
      []
    ));

    await waitFor(() => {
      expect(screen.getByTestId("chart-renderer").dataset.chartType).toBe("single_value");
    });

    expect(container.querySelector("table")).not.toBeInTheDocument();
  });

  test("URL-encodes question in query string", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");
    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    expect(instances[0].url).toContain("question=" + encodeURIComponent("华东区销售额多少"));
    expect(instances[0].url).toContain("credentialId=1");
    expect(instances[0].url).toContain("modelName=gpt-test");
  });

  test("accumulates trace events and renders timeline during streaming", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    const { container } = render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");
    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });

    // Emit just the first few events including semantic-context trace
    emit(instances[0], { type: "status", stage: "analyzing", message: "正在分析你的问题..." });
    emit(instances[0], { type: "trace", spanId: "semantic-context", name: "semantic-context", status: "END", attributes: { subjects: [{ name: "orders" }] } });
    emit(instances[0], { type: "trace", spanId: "intent-extraction", name: "intent-extraction", status: "START" });

    await waitFor(() => {
      expect(container.textContent).toContain("语义层加载");
      expect(container.textContent).toContain("意图提取");
    });
  });

  test("renders nested llm-call span indented under intent-extraction", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    const { container } = render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");
    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    feedEvents(instances[0], biEventsWithTrace("华东区销售额为 100。", [{ region: "华东", amount: 100 }], "single_value"));

    await waitFor(() => {
      expect(screen.getByTestId("markdown").textContent).toContain("华东区销售额为 100。");
    });

    // After done, timeline is collapsed — expand it
    const expandButton = screen.getByRole("button", { name: /思考过程/ });
    await user.click(expandButton);

    // Verify all spans render
    expect(container.textContent).toContain("语义层加载");
    expect(container.textContent).toContain("意图提取");
    expect(container.textContent).toContain("LLM 调用");
    expect(container.textContent).toContain("SQL 构建");
    expect(container.textContent).toContain("查询执行");
    expect(container.textContent).toContain("洞察生成");

    // Verify llm-call is nested under intent-extraction (rendered after, indented)
    const allText = container.textContent ?? "";
    const intentIdx = allText.indexOf("意图提取");
    const llmCallIdx = allText.indexOf("LLM 调用");
    expect(intentIdx).toBeGreaterThanOrEqual(0);
    expect(llmCallIdx).toBeGreaterThan(intentIdx);
  });

  test("auto-collapses timeline on done and allows re-expand", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    const { container } = render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");
    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    feedEvents(instances[0], biEventsWithTrace("华东区销售额为 100。", [{ region: "华东", amount: 100 }], "single_value"));

    // After done, the timeline is collapsed — span labels should NOT be visible
    await waitFor(() => {
      expect(screen.getByTestId("markdown").textContent).toContain("华东区销售额为 100。");
    });

    // Collapsed: spans not visible
    expect(container.textContent).not.toContain("语义层加载");
    expect(container.textContent).toContain("思考过程");

    // Click to expand
    const expandButton = screen.getByRole("button", { name: /思考过程/ });
    await user.click(expandButton);

    // Now spans should be visible
    expect(container.textContent).toContain("语义层加载");
    expect(container.textContent).toContain("意图提取");
  });

  test("clicking a span expands its attributes", async () => {
    const user = userEvent.setup();
    const { instances } = installMockEventSource();

    const { container } = render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    await screen.findByText("gpt-test");
    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    await waitFor(() => {
      expect(instances.length).toBe(1);
    });
    feedEvents(instances[0], biEventsWithTrace("华东区销售额为 100。", [{ region: "华东", amount: 100 }], "single_value"));

    await waitFor(() => {
      expect(screen.getByTestId("markdown").textContent).toContain("华东区销售额为 100。");
    });

    // Expand timeline
    const expandButton = screen.getByRole("button", { name: /思考过程/ });
    await user.click(expandButton);

    // Click on the query-execute span label (which has rowCount attribute)
    const queryExecuteLabel = screen.getByText("查询执行");
    await user.click(queryExecuteLabel);

    // The attributes JSON should now be visible
    expect(container.textContent).toContain("rowCount");
  });
});
