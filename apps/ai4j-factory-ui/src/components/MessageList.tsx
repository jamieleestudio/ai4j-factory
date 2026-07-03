import { Sparkles, Bot } from "lucide-react";
import clsx from "clsx";
import { useEffect, useRef } from "react";
import Markdown from "./Markdown";

interface Message {
  role: string;
  content: string;
}

interface MessageListProps {
  messages: Message[];
  isLoading: boolean;
}

export default function MessageList({ messages, isLoading }: MessageListProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (bottomRef.current) {
        bottomRef.current.scrollIntoView({ behavior: "smooth" });
    }
  }, [messages, isLoading]);

  return (
    <div className="flex-1 overflow-y-auto p-4 md:p-6 space-y-8 no-scrollbar">
      {messages.length === 0 && (
          <div className="text-center py-16 text-gray-400">
              <Sparkles size={48} className="mx-auto mb-4 opacity-50" />
              <p className="text-lg">How can I help you today?</p>
          </div>
      )}

      {messages.map((msg, idx) => {
        if (msg.role === "user") {
          return (
            <div key={idx} className="flex justify-end w-full max-w-4xl mx-auto group">
              <div className="max-w-[85%] text-right">
                <div className="text-foreground text-[15px] leading-relaxed whitespace-pre-wrap bg-gray-100 dark:bg-[#1E1F20] px-4 py-2.5 rounded-2xl rounded-tr-sm inline-block text-left">
                  {msg.content}
                </div>
              </div>
            </div>
          );
        }

        return (
          <div key={idx} className="flex gap-4 w-full max-w-4xl mx-auto group">
            <div className="flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center border mt-1 bg-blue-50 dark:bg-blue-900/30 border-blue-100 dark:border-blue-800">
              <Bot size={16} className="text-blue-600 dark:text-blue-400" />
            </div>
            <div className="flex-1 space-y-1.5 overflow-hidden">
              <div className="flex items-center gap-2">
                <span className="font-medium text-sm text-foreground">AI Agent</span>
              </div>
              {msg.content ? (
                <div className="text-foreground text-[15px] leading-relaxed whitespace-pre-wrap">
                  <Markdown content={msg.content} />
                </div>
              ) : isLoading && idx === messages.length - 1 ? (
                <div className="flex items-center py-2">
                    <div className="flex items-center gap-1">
                        <span className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce"></span>
                        <span className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce delay-75"></span>
                        <span className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce delay-150"></span>
                    </div>
                </div>
              ) : null}
            </div>
          </div>
        );
      })}
      
      {/* Spacer for bottom input area */}
      {(messages.length > 0 || isLoading) && <div ref={bottomRef} className="h-4" />}
    </div>
  );
}
