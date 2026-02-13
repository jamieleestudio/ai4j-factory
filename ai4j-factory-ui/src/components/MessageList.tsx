import { Bot, User, Sparkles } from "lucide-react";
import clsx from "clsx";
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/cjs/styles/prism';
import { useEffect, useRef } from "react";

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
    <div className="flex-1 overflow-y-auto p-4 md:p-6 space-y-8 scrollbar-thin scrollbar-thumb-accent scrollbar-track-transparent">
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
                 : "markdown-body w-full" 
             )}>
                {msg.role === "user" ? (
                    msg.content
                ) : (
                    <ReactMarkdown
                        remarkPlugins={[remarkGfm]}
                        components={{
                            code({node, inline, className, children, ...props}: any) {
                                const match = /language-(\w+)/.exec(className || '')
                                return !inline && match ? (
                                    <SyntaxHighlighter
                                        {...props}
                                        style={vscDarkPlus}
                                        language={match[1]}
                                        PreTag="div"
                                        className="rounded-md my-4"
                                    >
                                        {String(children).replace(/\n$/, '')}
                                    </SyntaxHighlighter>
                                ) : (
                                    <code {...props} className={clsx("bg-gray-200 dark:bg-gray-800 rounded px-1 py-0.5 font-mono text-sm", className)}>
                                        {children}
                                    </code>
                                )
                            },
                            // Custom styling for other markdown elements
                            p: ({children}) => <p className="mb-2 last:mb-0">{children}</p>,
                            ul: ({children}) => <ul className="list-disc pl-4 mb-2 space-y-1">{children}</ul>,
                            ol: ({children}) => <ol className="list-decimal pl-4 mb-2 space-y-1">{children}</ol>,
                            li: ({children}) => <li className="mb-1">{children}</li>,
                            a: ({children, href}) => <a href={href} className="text-blue-500 hover:underline" target="_blank" rel="noopener noreferrer">{children}</a>,
                            h1: ({children}) => <h1 className="text-2xl font-bold mb-2 mt-4 first:mt-0">{children}</h1>,
                            h2: ({children}) => <h2 className="text-xl font-bold mb-2 mt-3 first:mt-0">{children}</h2>,
                            h3: ({children}) => <h3 className="text-lg font-bold mb-2 mt-2 first:mt-0">{children}</h3>,
                            blockquote: ({children}) => <blockquote className="border-l-4 border-gray-300 pl-4 italic my-2">{children}</blockquote>,
                            table: ({children}) => <div className="overflow-x-auto my-4"><table className="min-w-full divide-y divide-gray-300 dark:divide-gray-700 border border-gray-300 dark:border-gray-700">{children}</table></div>,
                            thead: ({children}) => <thead className="bg-gray-50 dark:bg-gray-800">{children}</thead>,
                            tbody: ({children}) => <tbody className="divide-y divide-gray-200 dark:divide-gray-800">{children}</tbody>,
                            tr: ({children}) => <tr>{children}</tr>,
                            th: ({children}) => <th className="px-3 py-2 text-left text-sm font-semibold text-gray-900 dark:text-gray-100">{children}</th>,
                            td: ({children}) => <td className="px-3 py-2 text-sm text-gray-500 dark:text-gray-300">{children}</td>,
                        }}
                    >
                        {/* Pre-process content to ensure headers have spaces */}
                        {msg.content.replace(/(^|\n)(#{1,6})([^\s#])/g, '$1$2 $3')}
                    </ReactMarkdown>
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
      <div ref={bottomRef} className="h-4" />
    </div>
  );
}
