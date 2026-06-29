# Model Selector in ChatInput Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move model selector from ChatArea header into ChatInput bottom toolbar, and add independent model selection to BiArea.

**Architecture:** ChatInput gains optional model selector props and renders a dropdown in its bottom toolbar. ChatArea removes the header model selector and passes credentials down. BiArea adds credential management and passes it down. Each area independently manages its own selectedCredential state.

**Tech Stack:** React, TypeScript, clsx, lucide-react icons

---

### Task 1: Add model selector to ChatInput

**Files:**
- Modify: `apps/ai4j-factory-ui/src/components/ChatInput.tsx`

- [ ] **Step 1: Add imports and update interface**

At the top of ChatInput.tsx, add `ChevronDown` to the lucide-react import and add `ModelCredential` type import:

```tsx
import { SendHorizontal, Paperclip, Mic, Image as ImageIcon, ChevronDown } from "lucide-react";
import { useState, useRef, useEffect } from "react";
import clsx from "clsx";
import { ModelCredential } from "../types/credential";
```

Update the interface:

```tsx
interface ChatInputProps {
  onSend: (message: string) => void;
  isLoading: boolean;
  credentials: ModelCredential[];
  selectedCredential: ModelCredential | null;
  onCredentialChange: (cred: ModelCredential) => void;
}
```

- [ ] **Step 2: Add dropdown state and handlers**

Add inside the component, after the existing state:

```tsx
const [isDropdownOpen, setIsDropdownOpen] = useState(false);
```

- [ ] **Step 3: Add model selector button and dropdown in the bottom toolbar**

Replace the existing bottom toolbar JSX (the `<div className="flex justify-between items-center px-4 pb-3 pt-1">` block) with:

```tsx
<div className="flex justify-between items-center px-4 pb-3 pt-1">
    <div className="flex items-center gap-2">
        <button className="p-2 text-gray-500 hover:text-foreground hover:bg-black/5 dark:hover:bg-white/10 rounded-full transition-colors" title="Attach file">
            <Paperclip size={20} />
        </button>
        <button className="p-2 text-gray-500 hover:text-foreground hover:bg-black/5 dark:hover:bg-white/10 rounded-full transition-colors" title="Upload image">
            <ImageIcon size={20} />
        </button>

        {credentials.length > 0 && (
          <div className="relative">
            <button
              onClick={() => setIsDropdownOpen(!isDropdownOpen)}
              className="flex items-center gap-1 px-2 py-1.5 text-xs text-gray-500 hover:text-foreground hover:bg-black/5 dark:hover:bg-white/10 rounded-lg transition-colors"
            >
              <span className="max-w-[100px] truncate">
                {selectedCredential ? selectedCredential.provider.name : "Select Model"}
              </span>
              <ChevronDown size={14} />
            </button>

            {isDropdownOpen && (
              <>
                <div
                  className="fixed inset-0 z-10"
                  onClick={() => setIsDropdownOpen(false)}
                />
                <div className="absolute bottom-full left-0 mb-2 w-56 bg-white dark:bg-[#1E1F20] border border-gray-200 dark:border-gray-800 rounded-lg shadow-lg z-20 py-1 max-h-64 overflow-y-auto">
                  {credentials.map((cred) => (
                    <button
                      key={cred.id}
                      onClick={() => {
                        onCredentialChange(cred);
                        setIsDropdownOpen(false);
                      }}
                      className={`w-full text-left px-4 py-2 text-sm hover:bg-gray-100 dark:hover:bg-white/5 transition-colors ${
                        selectedCredential?.id === cred.id ? "bg-gray-50 dark:bg-white/5 font-medium" : ""
                      }`}
                    >
                      <div className="text-foreground">{cred.provider.name}</div>
                      <div className="text-xs text-gray-500 truncate">
                        {cred.apiKey.substring(0, 8)}...
                      </div>
                    </button>
                  ))}
                </div>
              </>
            )}
          </div>
        )}
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
```

- [ ] **Step 4: Commit**

```bash
git add apps/ai4j-factory-ui/src/components/ChatInput.tsx
git commit -m "feat: add model selector dropdown to ChatInput bottom toolbar"
```

---

### Task 2: Remove model selector from ChatArea header and pass props to ChatInput

**Files:**
- Modify: `apps/ai4j-factory-ui/src/components/ChatArea.tsx`

- [ ] **Step 1: Remove `ChevronDown` from lucide import**

```tsx
import { PanelLeft, ChevronDown } from "lucide-react";
```

Change to:

```tsx
import { PanelLeft } from "lucide-react";
```

- [ ] **Step 2: Remove `isDropdownOpen` state**

Remove this line:

```tsx
const [isDropdownOpen, setIsDropdownOpen] = useState(false);
```

- [ ] **Step 3: Replace header JSX**

Replace the header (lines 106-166, the `<div className="sticky top-0...">` block) with:

```tsx
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
```

- [ ] **Step 4: Pass credentials props to ChatInput**

Replace the ChatInput usage at the bottom:

```tsx
<ChatInput onSend={handleSendMessage} isLoading={isLoading} />
```

With:

```tsx
<ChatInput
  onSend={handleSendMessage}
  isLoading={isLoading}
  credentials={credentials}
  selectedCredential={selectedCredential}
  onCredentialChange={setSelectedCredential}
/>
```

- [ ] **Step 5: Commit**

```bash
git add apps/ai4j-factory-ui/src/components/ChatArea.tsx
git commit -m "refactor: move model selector from ChatArea header to ChatInput"
```

---

### Task 3: Add credential management to BiArea

**Files:**
- Modify: `apps/ai4j-factory-ui/src/components/BiArea.tsx`

- [ ] **Step 1: Add imports**

Add to existing imports:

```tsx
import { credentialService } from "../services/credentialService";
import { ModelCredential } from "../types/credential";
```

- [ ] **Step 2: Add credential state and useEffect**

Add after the existing `useState` declarations in the component:

```tsx
const [credentials, setCredentials] = useState<ModelCredential[]>([]);
const [selectedCredential, setSelectedCredential] = useState<ModelCredential | null>(null);

useEffect(() => {
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
  loadCredentials();
}, []);
```

- [ ] **Step 3: Pass credentials props to ChatInput**

Replace the ChatInput usage at the bottom:

```tsx
<ChatInput onSend={handleQuery} isLoading={isLoading} />
```

With:

```tsx
<ChatInput
  onSend={handleQuery}
  isLoading={isLoading}
  credentials={credentials}
  selectedCredential={selectedCredential}
  onCredentialChange={setSelectedCredential}
/>
```

- [ ] **Step 4: Commit**

```bash
git add apps/ai4j-factory-ui/src/components/BiArea.tsx
git commit -m "feat: add credential management and model selector to BiArea"
```
