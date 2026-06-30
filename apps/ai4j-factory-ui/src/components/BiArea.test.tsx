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

function createFetchResponse(body: unknown) {
  return {
    ok: true,
    json: async () => body,
  };
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
        createFetchResponse({
          question: "华东区销售额多少",
          summary: "华东区销售额为 100。",
          data: [{ region: "华东", amount: 100 }],
          chartType: "single_value",
        })
      )
      .mockResolvedValueOnce(
        createFetchResponse({
          question: "华南区销售额多少",
          summary: "华南区销售额为 200。",
          data: [{ region: "华南", amount: 200 }],
          chartType: "bar",
        })
      );

    const { container } = render(<BiArea isSidebarOpen={false} toggleSidebar={() => {}} />);

    // Wait for model selector to show "gpt-test" (credentials loaded)
    await screen.findByText("gpt-test");

    // Type first question and send via Enter
    const input = screen.getByPlaceholderText("Ask anything...");
    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    // Wait for first result to appear
    await waitFor(() => {
      expect(container.textContent).toContain("华东区销售额多少");
    });
    await waitFor(() => {
      expect(container.textContent).toContain("华东区销售额为 100。");
    });

    // Type second question and send
    await user.type(input, "华南区销售额多少");
    await user.keyboard("{Enter}");

    // Wait for second result to appear
    await waitFor(() => {
      expect(container.textContent).toContain("华南区销售额多少");
    });
    await waitFor(() => {
      expect(container.textContent).toContain("华南区销售额为 200。");
    });

    // BOTH previous questions and summaries should still be visible
    expect(container.textContent).toContain("华东区销售额多少");
    expect(container.textContent).toContain("华东区销售额为 100。");
  });
});
