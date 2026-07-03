"use client";

import { useState, useCallback, useEffect } from "react";

export interface RecentSession {
  id: string;
  title: string;
  mode: "chat" | "bi";
  timestamp: number;
}

const STORAGE_KEY = "ai4j-recent-sessions";
const MAX_SESSIONS = 20;

function loadSessions(): RecentSession[] {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(
      (s: unknown) =>
        s &&
        typeof s === "object" &&
        typeof (s as RecentSession).id === "string" &&
        typeof (s as RecentSession).title === "string" &&
        ((s as RecentSession).mode === "chat" || (s as RecentSession).mode === "bi") &&
        typeof (s as RecentSession).timestamp === "number"
    );
  } catch {
    return [];
  }
}

function saveSessions(sessions: RecentSession[]) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(sessions));
  } catch {
    // localStorage full or unavailable — silently ignore
  }
}

export function formatRelativeTime(timestamp: number): string {
  const diff = Date.now() - timestamp;
  const seconds = Math.floor(diff / 1000);
  const minutes = Math.floor(seconds / 60);
  const hours = Math.floor(minutes / 60);
  const days = Math.floor(hours / 24);

  if (seconds < 60) return "刚刚";
  if (minutes < 60) return `${minutes}分钟前`;
  if (hours < 24) return `${hours}小时前`;
  if (days < 7) return `${days}天前`;
  if (days < 30) return `${Math.floor(days / 7)}周前`;
  return `${Math.floor(days / 30)}个月前`;
}

export function useRecentSessions() {
  const [sessions, setSessions] = useState<RecentSession[]>([]);

  useEffect(() => {
    setSessions(loadSessions());
  }, []);

  const addSession = useCallback((session: RecentSession) => {
    setSessions((prev) => {
      const filtered = prev.filter((s) => s.id !== session.id);
      const next = [session, ...filtered].slice(0, MAX_SESSIONS);
      saveSessions(next);
      return next;
    });
  }, []);

  const removeSession = useCallback((id: string) => {
    setSessions((prev) => {
      const next = prev.filter((s) => s.id !== id);
      saveSessions(next);
      return next;
    });
  }, []);

  return [sessions, addSession, removeSession] as const;
}
