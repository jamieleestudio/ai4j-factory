type SSEEventType = "progress" | "chunk" | "result" | "error";

interface SSECallbacks {
  onProgress?: (message: string) => void;
  onChunk?: (text: string) => void;
  onResult?: (data: string) => void;
  onError?: (error: string) => void;
  onDone?: () => void;
}

export function parseSSELine(line: string): { type: SSEEventType; content: string } | null {
  if (!line.startsWith("data:")) return null;
  let data = line.slice(5);
  if (data.startsWith(" ")) data = data.slice(1);

  if (data.startsWith("[progress] ")) {
    return { type: "progress", content: data.slice(11) };
  }
  if (data.startsWith("[chunk] ")) {
    return { type: "chunk", content: data.slice(8) };
  }
  if (data.startsWith("[result] ")) {
    return { type: "result", content: data.slice(9) };
  }
  if (data.startsWith("[error] ")) {
    return { type: "error", content: data.slice(8) };
  }
  return null;
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
    return;
  }

  const reader = response.body?.getReader();
  if (!reader) {
    callbacks.onError?.("Response body is not readable");
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

        switch (parsed.type) {
          case "progress":
            callbacks.onProgress?.(parsed.content);
            break;
          case "chunk":
            callbacks.onChunk?.(parsed.content);
            break;
          case "result":
            callbacks.onResult?.(parsed.content);
            break;
          case "error":
            callbacks.onError?.(parsed.content);
            break;
        }

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
