"use client";

import { useState } from "react";
import Sidebar from "./Sidebar";
import ChatArea from "./ChatArea";

export default function ChatInterface() {
  const [isSidebarOpen, setIsSidebarOpen] = useState(true);

  return (
    <div className="flex h-full w-full">
      <Sidebar isOpen={isSidebarOpen} setIsOpen={setIsSidebarOpen} />
      <ChatArea isSidebarOpen={isSidebarOpen} toggleSidebar={() => setIsSidebarOpen(!isSidebarOpen)} />
    </div>
  );
}
