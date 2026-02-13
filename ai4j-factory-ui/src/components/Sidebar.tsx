import { Plus, MessageSquare, Settings, Menu, X, Sun, Moon } from "lucide-react";
import clsx from "clsx";
import { useTheme } from "next-themes";
import { useEffect, useState } from "react";

interface SidebarProps {
  isOpen: boolean;
  setIsOpen: (isOpen: boolean) => void;
}

export default function Sidebar({ isOpen, setIsOpen }: SidebarProps) {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  useEffect(() => {
    setMounted(true);
  }, []);

  return (
    <>
      {/* Mobile Overlay */}
      <div
        className={clsx(
          "fixed inset-0 bg-black/50 z-20 md:hidden transition-opacity",
          isOpen ? "opacity-100" : "opacity-0 pointer-events-none"
        )}
        onClick={() => setIsOpen(false)}
      />

      <div
        className={clsx(
          "fixed md:relative z-30 flex flex-col h-full bg-sidebar transition-all duration-300 ease-in-out",
          isOpen ? "translate-x-0 w-[280px]" : "-translate-x-full md:translate-x-0 md:w-0 md:overflow-hidden",
          "md:border-r-0"
        )}
      >
        <div className="p-4">
          <button 
             onClick={() => setIsOpen(false)}
             className="md:hidden absolute top-4 right-4 text-gray-500 hover:text-foreground"
          >
            <X size={24} />
          </button>

          <button className="flex items-center gap-3 w-full px-4 py-3 rounded-full bg-input-bg hover:bg-black/5 dark:hover:bg-white/5 transition-colors text-sm text-foreground mb-6 shadow-sm border border-transparent hover:border-input-border/50">
            <Plus size={18} className="text-gray-500" />
            <span className="font-medium">New chat</span>
          </button>

          <div className="flex flex-col gap-1">
            <div className="text-xs font-medium text-gray-500 px-4 py-2 mb-1">Recent</div>
            {/* Dummy History Items */}
            {["React Server Components", "Next.js 14 Features", "Tailwind CSS Grid", "AI Model Comparison", "System Architecture"].map((item, i) => (
              <button
                key={i}
                className="group flex items-center gap-3 px-4 py-2 text-sm text-foreground hover:bg-black/5 dark:hover:bg-white/5 rounded-full transition-colors truncate w-full text-left"
              >
                <MessageSquare size={16} className="text-gray-500 group-hover:text-foreground transition-colors flex-shrink-0" />
                <span className="truncate opacity-90 group-hover:opacity-100">{item}</span>
              </button>
            ))}
          </div>
        </div>

        <div className="mt-auto p-4 flex flex-col gap-2">
           <button 
            onClick={() => setTheme(theme === "dark" ? "light" : "dark")}
            className="flex items-center gap-3 w-full px-4 py-2 text-sm text-foreground hover:bg-black/5 dark:hover:bg-white/5 rounded-full transition-colors"
          >
            {mounted && theme === "dark" ? (
                <Sun size={18} className="text-gray-500" />
            ) : (
                <Moon size={18} className="text-gray-500" />
            )}
            <span>{mounted && theme === "dark" ? "Light mode" : "Dark mode"}</span>
          </button>
          
          <button className="flex items-center gap-3 w-full px-4 py-2 text-sm text-foreground hover:bg-black/5 dark:hover:bg-white/5 rounded-full transition-colors">
            <Settings size={18} className="text-gray-500" />
            <span>Settings</span>
          </button>
        </div>
      </div>
    </>
  );
}
