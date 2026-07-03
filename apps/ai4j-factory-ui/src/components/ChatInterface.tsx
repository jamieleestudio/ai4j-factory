"use client";

import { useState, useCallback } from "react";
import Sidebar from "./Sidebar";
import ChatArea from "./ChatArea";
import BiArea from "./BiArea";
import { useRecentSessions, type RecentSession } from "../hooks/useRecentSessions";

export type AppMode = "chat" | "bi";

export default function ChatInterface() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [activeMode, setActiveMode] = useState<AppMode>("chat");
  const [recentSessions, addRecentSession, removeRecentSession] = useRecentSessions();
  const [activeSessionId, setActiveSessionId] = useState<string | null>(null);

  const handleRecentClick = useCallback((session: RecentSession) => {
    setActiveMode(session.mode);
    setActiveSessionId(session.id);
  }, []);

  const handleModeChange = useCallback((mode: AppMode) => {
    setActiveMode(mode);
    setActiveSessionId(null);
  }, []);

  const handleRecentDelete = useCallback(
    (id: string) => {
      removeRecentSession(id);
      if (activeSessionId === id) {
        setActiveSessionId(null);
      }
    },
    [removeRecentSession, activeSessionId]
  );

  const handleFirstMessage = useCallback(
    (sessionId: string, title: string, mode: AppMode) => {
      addRecentSession({
        id: sessionId,
        title: title.slice(0, 30),
        mode,
        timestamp: Date.now(),
      });
    },
    [addRecentSession]
  );

  return (
    <div className="flex h-full w-full">
      <Sidebar
        isOpen={isSidebarOpen}
        setIsOpen={setIsSidebarOpen}
        activeMode={activeMode}
        onModeChange={handleModeChange}
        recentSessions={recentSessions}
        activeSessionId={activeSessionId}
        onRecentClick={handleRecentClick}
        onRecentDelete={handleRecentDelete}
      />
      {activeMode === "chat" ? (
        <ChatArea
          isSidebarOpen={isSidebarOpen}
          toggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
          initialSessionId={activeSessionId}
          onFirstMessage={handleFirstMessage}
        />
      ) : (
        <BiArea
          isSidebarOpen={isSidebarOpen}
          toggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)}
          initialSessionId={activeSessionId}
          onFirstMessage={handleFirstMessage}
        />
      )}
    </div>
  );
}
