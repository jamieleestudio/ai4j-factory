import { useEffect, useRef, useState } from "react";
import { PanelLeft } from "lucide-react";
import ChatInput from "./ChatInput";
import MessageList from "./MessageList";
import { credentialService } from "../services/credentialService";
import { ModelCredential } from "../types/credential";

interface ChatAreaProps {
  isSidebarOpen: boolean;
  toggleSidebar: () => void;
}

export default function ChatArea({ isSidebarOpen, toggleSidebar }: ChatAreaProps) {
  const [messages, setMessages] = useState<any[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const eventSourceRef = useRef<EventSource | null>(null);
  
  // Credential State
  const [credentials, setCredentials] = useState<ModelCredential[]>([]);
  const [selectedCredential, setSelectedCredential] = useState<ModelCredential | null>(null);

  useEffect(() => {
    loadCredentials();
  }, []);

  const loadCredentials = async () => {
    try {
      const creds = await credentialService.getCredentials();
      const activeCreds = creds.filter(c => c.enabled);
      setCredentials(activeCreds);
      if (activeCreds.length > 0) {
        setSelectedCredential(activeCreds[0]);
      }
    } catch (error) {
      console.error("Failed to load credentials", error);
    }
  };

  const handleSendMessage = (content: string) => {
    if (isLoading) return;
    if (!selectedCredential) {
      alert("Please select a model credential first.");
      return;
    }

    if (eventSourceRef.current) {
        eventSourceRef.current.close();
    }

    // Add user message immediately
    setMessages(prev => [...prev, { role: "user", content }]);
    setIsLoading(true);

    // Add initial AI message placeholder
    setMessages(prev => [...prev, { role: "ai", content: "" }]);

    // Use explicit backend URL to avoid Next.js proxy buffering in dev mode
    const baseUrl = process.env.NEXT_PUBLIC_API_BASE || 'http://localhost:8080';
    // Use the credential-specific endpoint
    const url = `${baseUrl}/api/chat/stream/${selectedCredential.id}?message=${encodeURIComponent(content)}`;
    
    const es = new EventSource(url, { withCredentials: true });
    
    es.onmessage = (e) => {
        const text = e.data;
        setMessages(prev => {
            const newMsgs = [...prev];
            const lastMsg = newMsgs[newMsgs.length - 1];
            if (lastMsg && lastMsg.role === "ai") {
                newMsgs[newMsgs.length - 1] = {
                    ...lastMsg,
                    content: lastMsg.content + text
                };
            }
            return newMsgs;
        });
    };

    es.onerror = (e) => {
       // When the stream ends (server closes connection), onerror is often called.
       // We can treat this as "done" or check if it was a real error.
       // For simplicity, and matching user snippet logic, we close and stop loading.
       es.close();
       setIsLoading(false);
       eventSourceRef.current = null;
    };

    es.onopen = () => {
        // Connection opened
    };

    eventSourceRef.current = es;
  };

  useEffect(() => {
    return () => {
      if (eventSourceRef.current) {
        eventSourceRef.current.close();
      }
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
          credentials={credentials}
          selectedCredential={selectedCredential}
          onCredentialChange={setSelectedCredential}
        />
        <div className="text-[11px] text-center text-gray-400 dark:text-gray-500 mt-3 font-light">
          Gemini may display inaccurate info, including about people, so double-check its responses.
        </div>
      </div>
    </div>
  );
}
