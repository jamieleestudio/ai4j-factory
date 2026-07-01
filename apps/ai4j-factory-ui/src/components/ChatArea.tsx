import { useEffect, useRef, useState } from "react";
import { PanelLeft } from "lucide-react";
import ChatInput from "./ChatInput";
import MessageList from "./MessageList";
import { credentialService } from "../services/credentialService";
import { SelectableModelOption } from "../types/credential";
import { buildSelectableModelOptions } from "../utils/modelOptions";
import { subscribeSSE, type SSESubscription } from "../utils/sse";

interface ChatAreaProps {
  isSidebarOpen: boolean;
  toggleSidebar: () => void;
}

export default function ChatArea({ isSidebarOpen, toggleSidebar }: ChatAreaProps) {
  const [messages, setMessages] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const subscriptionRef = useRef<SSESubscription | null>(null);
  
  const [modelOptions, setModelOptions] = useState<SelectableModelOption[]>([]);
  const [selectedModel, setSelectedModel] = useState<SelectableModelOption | null>(null);

  useEffect(() => {
    loadCredentials();
  }, []);

  const loadCredentials = async () => {
    try {
      const [creds, configs] = await Promise.all([
        credentialService.getCredentials(),
        credentialService.getConfigs(),
      ]);
      const options = buildSelectableModelOptions(creds, configs);
      setModelOptions(options);
      if (options.length > 0) {
        setSelectedModel(options[0]);
      }
    } catch (error) {
      console.error("Failed to load model options", error);
    }
  };

  const handleSendMessage = (content: string) => {
    if (isLoading) return;
    if (!selectedModel) {
      alert("Please select a model first.");
      return;
    }

    if (subscriptionRef.current) {
        subscriptionRef.current.close();
        subscriptionRef.current = null;
    }

    // Add user message immediately
    setMessages(prev => [...prev, { role: "user", content }]);
    setIsLoading(true);

    // Add initial AI message placeholder
    setMessages(prev => [...prev, { role: "ai", content: "" }]);

    // Use explicit backend URL to avoid Next.js proxy buffering in dev mode
    const baseUrl = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080';
    // Use the credential-specific endpoint
    const url = `${baseUrl}/api/chat/stream/${selectedModel.credentialId}?message=${encodeURIComponent(content)}&model=${encodeURIComponent(selectedModel.modelName)}`;

    const sub = subscribeSSE(url, {
        onChunk: (chunk) => {
            setMessages(prev => {
                const newMsgs = [...prev];
                const lastMsg = newMsgs[newMsgs.length - 1];
                if (lastMsg && lastMsg.role === "ai") {
                    newMsgs[newMsgs.length - 1] = {
                        ...lastMsg,
                        content: lastMsg.content + chunk
                    };
                }
                return newMsgs;
            });
        },
        onDone: () => {
            setIsLoading(false);
            subscriptionRef.current?.close();
            subscriptionRef.current = null;
        },
        onError: (message) => {
            setMessages(prev => {
                const newMsgs = [...prev];
                const lastMsg = newMsgs[newMsgs.length - 1];
                if (lastMsg && lastMsg.role === "ai") {
                    newMsgs[newMsgs.length - 1] = {
                        ...lastMsg,
                        content: lastMsg.content + `\n[error] ${message}`
                    };
                }
                return newMsgs;
            });
            setIsLoading(false);
            subscriptionRef.current?.close();
            subscriptionRef.current = null;
        },
    });
    subscriptionRef.current = sub;
  };

  useEffect(() => {
    return () => {
      subscriptionRef.current?.close();
      subscriptionRef.current = null;
    };
  }, []);

  return (
    <div className="flex-1 flex flex-col h-full bg-background relative transition-all duration-300">
      {/* Header */}
      <div className="sticky top-0 z-10 flex items-center justify-between p-4 bg-background/80 backdrop-blur-md">
        <div className="flex items-center gap-2">
          {!isSidebarOpen && (
            <button
              onClick={toggleSidebar}
              className="p-2 text-gray-500 hover:text-foreground hover:bg-black/5 dark:hover:bg-white/10 rounded-full transition-colors"
            >
              <PanelLeft size={20} />
            </button>
          )}
        </div>

        <div className="flex items-center gap-2">
          {/* Right side actions if needed */}
        </div>
      </div>

      {/* Messages */}
      <MessageList messages={messages} isLoading={isLoading} />

      {/* Input Area - Fixed at bottom */}
      <div className="w-full px-4 pb-6 pt-2 bg-gradient-to-t from-background via-background to-transparent">
        <ChatInput
          onSend={handleSendMessage}
          isLoading={isLoading}
          modelOptions={modelOptions}
          selectedModel={selectedModel}
          onModelChange={setSelectedModel}
        />
        <div className="text-[11px] text-center text-gray-400 dark:text-gray-500 mt-3 font-light">
          Gemini may display inaccurate info, including about people, so double-check its responses.
        </div>
      </div>
    </div>
  );
}
