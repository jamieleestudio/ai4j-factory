import { Plus, Settings, X, Sun, Moon, BarChart3, MessageSquare, MoreHorizontal } from "lucide-react";
import clsx from "clsx";
import { useTheme } from "next-themes";
import { useEffect, useState } from "react";
import { Modal } from "./Modal";
import CredentialManager from "./CredentialManager";
import { AppMode } from "./ChatInterface";
import { type RecentSession, formatRelativeTime } from "../hooks/useRecentSessions";

interface SidebarProps {
  isOpen: boolean;
  setIsOpen: (isOpen: boolean) => void;
  activeMode: AppMode;
  onModeChange: (mode: AppMode) => void;
  recentSessions: RecentSession[];
  activeSessionId: string | null;
  onRecentClick: (session: RecentSession) => void;
  onRecentDelete: (id: string) => void;
}

export default function Sidebar({ isOpen, setIsOpen, activeMode, onModeChange, recentSessions, activeSessionId, onRecentClick, onRecentDelete }: SidebarProps) {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = useState(false);
  const [menuOpenId, setMenuOpenId] = useState<string | null>(null);

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

          <button
            onClick={() => onModeChange("chat")}
            className={clsx(
              "flex items-center gap-3 w-full px-4 py-3 rounded-lg transition-colors text-sm mb-2 shadow-sm border",
              activeMode === "chat"
                ? "bg-black/5 dark:bg-white/5 border-input-border/50"
                : "bg-input-bg hover:bg-black/5 dark:hover:bg-white/5 text-foreground border-transparent hover:border-input-border/50"
            )}
          >
            <Plus size={18} className="text-gray-500" />
            <span className="font-medium">New Chat</span>
          </button>

          <button
            onClick={() => onModeChange("bi")}
            className={clsx(
              "flex items-center gap-3 w-full px-4 py-3 rounded-lg transition-colors text-sm mb-2 shadow-sm border",
              activeMode === "bi"
                ? "bg-black/5 dark:bg-white/5 border-input-border/50"
                : "bg-input-bg hover:bg-black/5 dark:hover:bg-white/5 text-foreground border-transparent hover:border-input-border/50"
            )}
          >
            <BarChart3 size={18} className="text-gray-500" />
            <span className="font-medium">Data Warehouse BI</span>
          </button>

          {recentSessions.length > 0 && (
            <div className="flex flex-col gap-1 mt-4">
              <div className="text-xs font-medium text-gray-500 px-4 py-2 mb-1">Recent</div>
              {recentSessions.map((session) => (
                <div
                  key={session.id}
                  className="group relative"
                >
                  <button
                    onClick={() => onRecentClick(session)}
                    className={clsx(
                      "flex items-center gap-3 w-full px-4 py-2 rounded-lg text-left text-sm transition-colors pr-8",
                      activeSessionId === session.id
                        ? "bg-black/5 dark:bg-white/5"
                        : "hover:bg-black/5 dark:hover:bg-white/5"
                    )}
                  >
                    {session.mode === "chat" ? (
                      <MessageSquare size={16} className="text-gray-400 flex-shrink-0" />
                    ) : (
                      <BarChart3 size={16} className="text-gray-400 flex-shrink-0" />
                    )}
                    <div className="flex-1 min-w-0">
                      <div className="truncate text-foreground">{session.title}</div>
                      <div className="text-xs text-gray-500">{formatRelativeTime(session.timestamp)}</div>
                    </div>
                  </button>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setMenuOpenId(menuOpenId === session.id ? null : session.id);
                    }}
                    className="absolute right-1 top-1/2 -translate-y-1/2 p-1 rounded opacity-0 group-hover:opacity-100 hover:bg-gray-200 dark:hover:bg-gray-700 transition-opacity z-10"
                    aria-label="更多"
                  >
                    <MoreHorizontal size={16} className="text-gray-400" />
                  </button>
                  {menuOpenId === session.id && (
                    <>
                      <div
                        className="fixed inset-0 z-20"
                        onClick={(e) => {
                          e.stopPropagation();
                          setMenuOpenId(null);
                        }}
                      />
                      <div className="absolute right-2 top-full mt-1 z-30 bg-white dark:bg-[#2d2d2d] border border-gray-200 dark:border-gray-700 rounded-lg shadow-lg py-1 min-w-[100px]">
                        <button
                          onClick={(e) => {
                            e.stopPropagation();
                            onRecentDelete(session.id);
                            setMenuOpenId(null);
                          }}
                          className="flex items-center gap-2 w-full px-3 py-2 text-sm text-red-500 hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                        >
                          <X size={14} />
                          <span>删除</span>
                        </button>
                      </div>
                    </>
                  )}
                </div>
              ))}
            </div>
          )}
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

          <button
            onClick={() => setIsSettingsOpen(true)}
            className="flex items-center gap-3 w-full px-4 py-2 text-sm text-foreground hover:bg-black/5 dark:hover:bg-white/5 rounded-full transition-colors"
          >
            <Settings size={18} className="text-gray-500" />
            <span>Settings</span>
          </button>
        </div>
      </div>

      <Modal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        title="Settings"
      >
        <CredentialManager />
      </Modal>
    </>
  );
}
