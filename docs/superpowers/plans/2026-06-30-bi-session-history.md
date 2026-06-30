# BI Session History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the BI page keep multiple BI question/result turns in memory until the page refreshes.

**Architecture:** `BiArea` changes from a single `question/result/error` view to an in-memory `messages` array owned by the component. Each user query appends a user message plus a BI assistant placeholder; the existing `/api/bi/query` response updates only that placeholder. No backend persistence, routing, or sidebar history is added.

**Tech Stack:** Next.js, React 19, TypeScript, Vitest, React Testing Library, jsdom

---

## File Structure

- `apps/ai4j-factory-ui/package.json` — add a `test` script and the minimal test dependencies.
- `apps/ai4j-factory-ui/vitest.config.ts` — configure Vitest for React component tests in jsdom.
- `apps/ai4j-factory-ui/src/test/setup.ts` — load Testing Library DOM matchers.
- `apps/ai4j-factory-ui/src/components/BiArea.test.tsx` — component test proving multiple BI query turns remain visible.
- `apps/ai4j-factory-ui/src/components/BiArea.tsx` — replace single-result state with in-memory message state and render each turn.

---

### Task 1: Add React component test tooling

**Files:**
- Modify: `apps/ai4j-factory-ui/package.json`
- Create: `apps/ai4j-factory-ui/vitest.config.ts`
- Create: `apps/ai4j-factory-ui/src/test/setup.ts`

- [ ] **Step 1: Install test dependencies**

Run from `apps/ai4j-factory-ui`:

```bash
npm install --save-dev vitest @vitejs/plugin-react jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

Expected: `package.json` and `package-lock.json` include the new dev dependencies.

- [ ] **Step 2: Add the test script**

In `apps/ai4j-factory-ui/package.json`, change the `scripts` block from:

```json
"scripts": {
  "dev": "next dev",
  "build": "next build",
  "start": "next start",
  "lint": "next lint"
}
```

To:

```json
"scripts": {
  "dev": "next dev",
  "build": "next build",
  "start": "next start",
  "lint": "next lint",
  "test": "vitest run"
}
```

- [ ] **Step 3: Create Vitest config**

Create `apps/ai4j-factory-ui/vitest.config.ts`:

```ts
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  test: {
    environment: "jsdom",
    globals: true,
    setupFiles: ["./src/test/setup.ts"],
  },
});
```

- [ ] **Step 4: Create Testing Library setup**

Create `apps/ai4j-factory-ui/src/test/setup.ts`:

```ts
import "@testing-library/jest-dom/vitest";
```

- [ ] **Step 5: Verify test runner starts**

Run from `apps/ai4j-factory-ui`:

```bash
npm test -- --runInBand
```

Expected: Vitest exits successfully with no test files found, or reports no tests found without TypeScript/configuration errors. If `--runInBand` is not supported by this Vitest version, run `npm test` instead and expect the same no-test-file result.

---

### Task 2: Write the failing BI history test

**Files:**
- Create: `apps/ai4j-factory-ui/src/components/BiArea.test.tsx`

- [ ] **Step 1: Add a component test for two BI turns**

Create `apps/ai4j-factory-ui/src/components/BiArea.test.tsx`:

```tsx
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, test, vi } from "vitest";
import BiArea from "./BiArea";

vi.mock("../services/credentialService", () => ({
  credentialService: {
    getCredentials: vi.fn().mockResolvedValue([
      {
        id: 1,
        name: "Local OpenAI",
        provider: { id: 1, name: "OpenAI", type: "openai" },
        apiKey: "test-key",
        baseUrl: "http://localhost:11434",
      },
    ]),
    getConfigs: vi.fn().mockResolvedValue([
      {
        id: 10,
        credentialId: 1,
        modelName: "gpt-test",
        enabled: true,
      },
    ]),
  },
}));

describe("BiArea", () => {
  beforeEach(() => {
    const responses = [
      {
        question: "华东区销售额多少",
        summary: "华东区销售额为 100。",
        data: [{ region: "华东", amount: 100 }],
        chartType: "single_value",
      },
      {
        question: "华南区销售额多少",
        summary: "华南区销售额为 200。",
        data: [{ region: "华南", amount: 200 }],
        chartType: "bar",
      },
    ];

    vi.stubGlobal(
      "fetch",
      vi.fn().mockImplementation(() =>
        Promise.resolve({
          ok: true,
          json: () => Promise.resolve(responses.shift()),
        }),
      ),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  test("keeps previous BI query results when a new question is asked", async () => {
    const user = userEvent.setup();
    render(<BiArea isSidebarOpen={true} toggleSidebar={vi.fn()} />);

    const input = screen.getByPlaceholderText("Ask anything...");

    await user.type(input, "华东区销售额多少");
    await user.keyboard("{Enter}");

    expect(await screen.findByText("华东区销售额为 100。")).toBeInTheDocument();

    await user.type(input, "华南区销售额多少");
    await user.keyboard("{Enter}");

    expect(await screen.findByText("华南区销售额为 200。")).toBeInTheDocument();
    expect(screen.getByText("华东区销售额为 100。")).toBeInTheDocument();
    expect(screen.getByText("华东区销售额多少")).toBeInTheDocument();
    expect(screen.getByText("华南区销售额多少")).toBeInTheDocument();

    const rows = screen.getAllByRole("row");
    expect(within(rows[1]).getByText("华东")).toBeInTheDocument();
    expect(within(rows[3]).getByText("华南")).toBeInTheDocument();

    await waitFor(() => {
      expect(fetch).toHaveBeenCalledTimes(2);
    });
  });
});
```

- [ ] **Step 2: Run test to verify RED**

Run from `apps/ai4j-factory-ui`:

```bash
npm test -- src/components/BiArea.test.tsx
```

Expected: FAIL because after the second query the first summary/question is no longer in the document. If it fails because the selected model is missing, wait for credential loading in the test by adding this before typing:

```tsx
await screen.findByText("gpt-test");
```

Then re-run until the failure is specifically that previous BI result content disappears.

---

### Task 3: Implement in-memory BI message history

**Files:**
- Modify: `apps/ai4j-factory-ui/src/components/BiArea.tsx`

- [ ] **Step 1: Add message types**

In `BiArea.tsx`, keep `InsightResult` and add these types below it:

```tsx
type BiMessage =
  | {
      id: string;
      role: "user";
      content: string;
    }
  | {
      id: string;
      role: "assistant";
      status: "loading" | "success" | "error";
      result?: InsightResult;
      error?: string;
    };
```

- [ ] **Step 2: Replace single-result state with message state**

Replace these state lines:

```tsx
const [question, setQuestion] = useState("");
const [isLoading, setIsLoading] = useState(false);
const [result, setResult] = useState<InsightResult | null>(null);
const [error, setError] = useState<string | null>(null);
```

With:

```tsx
const [messages, setMessages] = useState<BiMessage[]>([]);
const [isLoading, setIsLoading] = useState(false);
```

- [ ] **Step 3: Update `handleQuery` to append and update messages**

Replace the entire `handleQuery` function with:

```tsx
const handleQuery = async (content: string) => {
  if (isLoading) return;

  if (!selectedModel) {
    const id = crypto.randomUUID();
    setMessages((prev) => [
      ...prev,
      { id: `${id}-user`, role: "user", content },
      {
        id: `${id}-assistant`,
        role: "assistant",
        status: "error",
        error: "Please select a model first.",
      },
    ]);
    return;
  }

  const id = crypto.randomUUID();
  const assistantId = `${id}-assistant`;

  setMessages((prev) => [
    ...prev,
    { id: `${id}-user`, role: "user", content },
    { id: assistantId, role: "assistant", status: "loading" },
  ]);
  setIsLoading(true);

  try {
    const baseUrl = process.env.NEXT_PUBLIC_API_BASE || "http://localhost:8080";
    const response = await fetch(`${baseUrl}/api/bi/query`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        question: content,
        credentialId: selectedModel.credentialId,
        modelName: selectedModel.modelName,
      }),
    });

    if (!response.ok) {
      throw new Error(`Query failed: ${response.statusText}`);
    }

    const data: InsightResult = await response.json();
    setMessages((prev) =>
      prev.map((message) =>
        message.id === assistantId
          ? { id: assistantId, role: "assistant", status: "success", result: data }
          : message,
      ),
    );
  } catch (err) {
    setMessages((prev) =>
      prev.map((message) =>
        message.id === assistantId
          ? {
              id: assistantId,
              role: "assistant",
              status: "error",
              error: err instanceof Error ? err.message : "Query failed",
            }
          : message,
      ),
    );
  } finally {
    setIsLoading(false);
  }
};
```

- [ ] **Step 4: Replace content rendering with message rendering**

Replace the current content inside:

```tsx
<div className="max-w-4xl mx-auto space-y-6">
```

with:

```tsx
{messages.length === 0 && (
  <div className="text-center py-16 text-gray-400">
    <BarChart3 size={48} className="mx-auto mb-4 opacity-50" />
    <p className="text-lg">Ask a data question to get started</p>
    <p className="text-sm mt-2">e.g. &quot;华东区销售额多少&quot;</p>
  </div>
)}

{messages.map((message) => {
  if (message.role === "user") {
    return (
      <div key={message.id} className="flex justify-end">
        <div className="max-w-[85%] whitespace-pre-wrap bg-gray-100 dark:bg-[#1E1F20] px-4 py-2 rounded-[20px] rounded-tr-sm text-foreground text-base leading-7 font-light tracking-wide">
          {message.content}
        </div>
      </div>
    );
  }

  if (message.status === "loading") {
    return (
      <div key={message.id} className="flex items-center py-4 text-gray-500">
        <Loader2 size={24} className="animate-spin text-gray-400" />
        <span className="ml-3">Analyzing...</span>
      </div>
    );
  }

  if (message.status === "error") {
    return (
      <div
        key={message.id}
        className="p-4 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg text-red-700 dark:text-red-400"
      >
        {message.error}
      </div>
    );
  }

  const result = message.result;

  if (!result) {
    return null;
  }

  return (
    <div key={message.id} className="space-y-6">
      <div className="p-4 bg-blue-50 dark:bg-blue-900/20 rounded-lg border border-blue-200 dark:border-blue-800">
        <div className="text-xs font-medium text-blue-600 dark:text-blue-400 mb-1">Insight</div>
        <div className="text-foreground">{result.summary}</div>
        {result.chartType && result.chartType !== "single_value" && (
          <div className="mt-2 text-xs text-gray-500">
            Recommended chart: <span className="font-medium">{result.chartType}</span>
          </div>
        )}
      </div>

      {result.data && result.data.length > 0 && (
        <div className="overflow-x-auto rounded-lg border border-gray-200 dark:border-gray-700">
          <table className="w-full text-sm">
            <thead>
              <tr className="bg-gray-50 dark:bg-gray-800">
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
})}
```

This intentionally removes the separate `Question` card because the user question is now rendered as the chat turn, matching the New Chat style.

- [ ] **Step 5: Run test to verify GREEN**

Run from `apps/ai4j-factory-ui`:

```bash
npm test -- src/components/BiArea.test.tsx
```

Expected: PASS. The test confirms both BI result summaries and both user questions remain visible after the second query.

---

### Task 4: Verify UI behavior and build health

**Files:**
- No additional file changes expected.

- [ ] **Step 1: Run the focused test**

Run from `apps/ai4j-factory-ui`:

```bash
npm test -- src/components/BiArea.test.tsx
```

Expected: PASS with no React act warnings or unhandled promise warnings.

- [ ] **Step 2: Run a production build**

Run from `apps/ai4j-factory-ui`:

```bash
npm run build
```

Expected: PASS. If the build reports TypeScript errors from the test file, update `tsconfig.json` `exclude` to include `**/*.test.ts` and `**/*.test.tsx`, then re-run build.

- [ ] **Step 3: Manually verify in the browser**

Run from `apps/ai4j-factory-ui`:

```bash
npm run dev
```

Open the app, switch to `Data Warehouse BI`, select a configured model if needed, and ask two BI questions such as:

```text
华东区销售额多少
```

```text
华南区销售额多少
```

Expected: the page shows two user bubbles and two BI result cards in order. The first BI result remains visible after the second completes. Refreshing the browser clears the BI history.

- [ ] **Step 4: Review the diff**

Run from the repository root:

```bash
git diff -- apps/ai4j-factory-ui/package.json apps/ai4j-factory-ui/package-lock.json apps/ai4j-factory-ui/vitest.config.ts apps/ai4j-factory-ui/src/test/setup.ts apps/ai4j-factory-ui/src/components/BiArea.test.tsx apps/ai4j-factory-ui/src/components/BiArea.tsx
```

Expected: changes are limited to test setup, the new BI history test, and `BiArea` in-memory history rendering.

---

## Self-Review

- Spec coverage: The plan implements session-only BI history, preserves multiple questions/results, keeps refresh-clears behavior by using component state only, and avoids backend/sidebar persistence.
- Placeholder scan: No TBD/TODO/fill-in-later instructions remain.
- Type consistency: `InsightResult`, `BiMessage`, `messages`, `selectedModel`, and the `/api/bi/query` request fields match the current component structure.
