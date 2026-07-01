import { Sparkles } from "lucide-react";
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
          <div className="flex flex-col items-center justify-center h-full text-center opacity-50">
              <Sparkles size={48} className="mb-4 text-gray-400" />
              <h2 className="text-2xl font-semibold mb-2 text-foreground">How can I help you today?</h2>
          </div>
      )}

      {messages.map((msg, idx) => (
        <div
          key={idx}
          className={clsx(
            "flex w-full max-w-4xl mx-auto",
            msg.role === "user" ? "justify-end" : "justify-start"
          )}
        >
          {/* Content Column */}
          <div className={clsx(
              "max-w-[85%]",
              msg.role === "user" ? "text-right" : "text-left"
          )}>
             <div className={clsx(
               "text-foreground text-base leading-7 font-light tracking-wide inline-block",
               msg.role === "user"
                 ? "whitespace-pre-wrap bg-gray-100 dark:bg-[#1E1F20] px-4 py-2 rounded-[20px] rounded-tr-sm"
                 : "w-full"
             )}>
                {msg.role === "user" ? (
                    msg.content
                ) : (
                    <Markdown content={msg.content} />
                )}
             </div>
          </div>
        </div>
      ))}
      
      {isLoading && (
        <div className="flex w-full max-w-4xl mx-auto justify-start">
           <div className="flex-1 min-w-0 py-2">
                <div className="flex items-center gap-1">
                    <span className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce"></span>
                    <span className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce delay-75"></span>
                    <span className="w-2 h-2 bg-gray-400 dark:bg-gray-500 rounded-full animate-bounce delay-150"></span>
                </div>
           </div>
        </div>
      )}
      
      {/* Spacer for bottom input area */}
      {(messages.length > 0 || isLoading) && <div ref={bottomRef} className="h-4" />}
    </div>
  );
}
