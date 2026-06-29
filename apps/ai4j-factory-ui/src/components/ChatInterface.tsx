"use client";

import { useState } from "react";
import Sidebar from "./Sidebar";
import ChatArea from "./ChatArea";
import BiArea from "./BiArea";

export type AppMode = "chat" | "bi";

export default function ChatInterface() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);
  const [activeMode, setActiveMode] = useState<AppMode>("chat");

  return (
    <div className="flex h-full w-full">
      <Sidebar
        isOpen={isSidebarOpen}
        setIsOpen={setIsSidebarOpen}
        activeMode={activeMode}
        onModeChange={setActiveMode}
      />
      {activeMode === "chat" ? (
        <ChatArea isSidebarOpen={isSidebarOpen} toggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)} />
      ) : (
        <BiArea isSidebarOpen={isSidebarOpen} toggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)} />
      )}
    </div>
  );
}
