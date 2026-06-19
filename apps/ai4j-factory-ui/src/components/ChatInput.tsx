import { SendHorizontal, Paperclip, Mic, Image as ImageIcon } from "lucide-react";
import { useState, useRef, useEffect } from "react";
import clsx from "clsx";

interface ChatInputProps {
  onSend: (message: string) => void;
  isLoading: boolean;
}

export default function ChatInput({ onSend, isLoading }: ChatInputProps) {
  const [input, setInput] = useState("");
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const handleSend = () => {
    if (!input.trim() || isLoading) return;
    onSend(input);
    setInput("");
    if (textareaRef.current) {
        textareaRef.current.style.height = 'auto';
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };
  
  const handleInput = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      setInput(e.target.value);
  }

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.style.height = 'auto';
      textareaRef.current.style.height = `${Math.min(textareaRef.current.scrollHeight, 200)}px`;
    }
  }, [input]);

  return (
    <div className="relative w-full max-w-4xl mx-auto">
      <div className={clsx(
        "relative flex flex-col w-full bg-input-bg rounded-[24px] border border-transparent transition-all duration-200",
        "focus-within:bg-white dark:focus-within:bg-[#28292A] focus-within:shadow-md dark:focus-within:shadow-lg focus-within:border-gray-200 dark:focus-within:border-gray-600/50"
      )}>
        <textarea
          ref={textareaRef}
          value={input}
          onChange={handleInput}
          onKeyDown={handleKeyDown}
          placeholder="Ask anything..."
          className="w-full max-h-[200px] py-4 px-6 bg-transparent border-0 focus:ring-0 resize-none outline-none text-foreground text-base placeholder-gray-500 overflow-y-auto"
          rows={1}
          style={{ minHeight: '56px' }}
        />
        
        <div className="flex justify-between items-center px-4 pb-3 pt-1">
            <div className="flex items-center gap-2">
                <button className="p-2 text-gray-500 hover:text-foreground hover:bg-black/5 dark:hover:bg-white/10 rounded-full transition-colors" title="Attach file">
                    <Paperclip size={20} />
                </button>
                <button className="p-2 text-gray-500 hover:text-foreground hover:bg-black/5 dark:hover:bg-white/10 rounded-full transition-colors" title="Upload image">
                    <ImageIcon size={20} />
                </button>
            </div>
            
            <div className="flex items-center gap-2">
                 <button 
                    onClick={handleSend}
                    disabled={!input.trim() || isLoading}
                    className={clsx(
                        "p-2 rounded-full transition-all duration-200 flex items-center justify-center",
                        input.trim() && !isLoading 
                            ? "bg-foreground text-background hover:opacity-90" 
                            : "bg-transparent text-gray-400 cursor-not-allowed"
                    )}
                 >
                    {input.trim() ? <SendHorizontal size={20} /> : <Mic size={20} />}
                </button>
            </div>
        </div>
      </div>
    </div>
  );
}
