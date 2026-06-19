# Monorepo Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize the repository into an `apps` and `services` monorepo layout with Maven and pnpm workspace entry points at the root.

**Architecture:** The root remains the authoritative workspace entry point. Maven aggregates backend modules under `services`, while pnpm manages frontend apps under `apps`. Concrete modules keep the `ai4j-` prefix; backend domain grouping directories under `services` use short names such as `agent` and `mcp`.

**Tech Stack:** Maven, Spring Boot, Spring AI, pnpm workspaces, Next.js.

---

## File Structure

Create:

- `apps/`
- `services/`
- `package.json`
- `pnpm-workspace.yaml`

Move:

- `ai4j-factory-ui/` -> `apps/ai4j-factory-ui/`
- `ai4j-chatbot/` -> `services/ai4j-chatbot/`
- `ai4j-agent/` -> `services/agent/`
- `ai4j-mcp/` -> `services/mcp/`

Modify:

- `pom.xml`: update groupId and module paths.
- `services/ai4j-chatbot/pom.xml`: update parent path, remove redundant coordinates where appropriate, normalize groupId inheritance.
- `services/agent/pom.xml`: update parent path and remove obsolete test dependency.
- `services/agent/ai4j-agent-assistbot/pom.xml`: update parent path.
- `services/mcp/pom.xml`: update parent path.
- `services/mcp/ai4j-mcp-spec/pom.xml`: update parent path.
- `apps/ai4j-factory-ui/package.json`: keep package name and scripts; no package manager change inside the file is required.
- `.gitignore`: add root-safe generated output ignores if missing.
- `README.md`: document monorepo layout and commands.

Delete:

- `apps/ai4j-factory-ui/package-lock.json`
- `apps/ai4j-factory-ui/pnpm-lock.yaml` after generating or moving lockfile to root.

Do not modify:

- Java package names under `src/main/java`.
- API endpoints.
- Business logic.
- Existing uncommitted `.idea` files.

---

### Task 1: Move Project Directories

**Files:**
- Move: `ai4j-factory-ui/` -> `apps/ai4j-factory-ui/`
- Move: `ai4j-chatbot/` -> `services/ai4j-chatbot/`
- Move: `ai4j-agent/` -> `services/agent/`
- Move: `ai4j-mcp/` -> `services/mcp/`

- [ ] **Step 1: Confirm only expected pre-existing changes are present**

Run:

```bash
git status --short
```

Expected: `.idea/compiler.xml` and `.idea/encodings.xml` may be modified. No implementation files should already be staged.

- [ ] **Step 2: Create target directories**

Run:

```bash
mkdir -p apps services
```

Expected: command exits with status 0.

- [ ] **Step 3: Move frontend app**

Run:

```bash
git mv ai4j-factory-ui apps/ai4j-factory-ui
```

Expected: `apps/ai4j-factory-ui/package.json` exists.

- [ ] **Step 4: Move chatbot service**

Run:

```bash
git mv ai4j-chatbot services/ai4j-chatbot
```

Expected: `services/ai4j-chatbot/pom.xml` exists.

- [ ] **Step 5: Move agent domain**

Run:

```bash
git mv ai4j-agent services/agent
```

Expected: `services/agent/pom.xml` and `services/agent/ai4j-agent-assistbot/pom.xml` exist.

- [ ] **Step 6: Move MCP domain**

Run:

```bash
git mv ai4j-mcp services/mcp
```

Expected: `services/mcp/pom.xml` and `services/mcp/ai4j-mcp-spec/pom.xml` exist.

- [ ] **Step 7: Inspect moved files**

Run:

```bash
find apps services -maxdepth 3 -name pom.xml -o -name package.json
```

Expected output includes:

```text
apps/ai4j-factory-ui/package.json
services/ai4j-chatbot/pom.xml
services/agent/pom.xml
services/agent/ai4j-agent-assistbot/pom.xml
services/mcp/pom.xml
services/mcp/ai4j-mcp-spec/pom.xml
```

- [ ] **Step 8: Commit directory moves**

Run:

```bash
git add apps services
git commit -m "chore: move projects into monorepo layout"
```

Expected: commit succeeds and does not include `.idea` files.

---

### Task 2: Update Maven Aggregation and Coordinates

**Files:**
- Modify: `pom.xml`
- Modify: `services/ai4j-chatbot/pom.xml`
- Modify: `services/agent/pom.xml`
- Modify: `services/agent/ai4j-agent-assistbot/pom.xml`
- Modify: `services/mcp/pom.xml`
- Modify: `services/mcp/ai4j-mcp-spec/pom.xml`

- [ ] **Step 1: Update root Maven coordinates and modules**

Edit `pom.xml` so the relevant sections are:

```xml
    <groupId>org.ai4j</groupId>
    <artifactId>ai4j-factory</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <packaging>pom</packaging>
```

and:

```xml
    <modules>
        <module>services/ai4j-chatbot</module>
        <module>services/mcp</module>
        <module>services/agent</module>
    </modules>
```

- [ ] **Step 2: Update chatbot parent path and inherited coordinates**

Edit `services/ai4j-chatbot/pom.xml` so the parent is:

```xml
  <parent>
    <groupId>org.ai4j</groupId>
    <artifactId>ai4j-factory</artifactId>
    <version>0.0.1-SNAPSHOT</version>
    <relativePath>../../pom.xml</relativePath>
  </parent>
```

Remove these child-level elements if present:

```xml
  <groupId>org.example</groupId>
  <version>1.0-SNAPSHOT</version>
```

Keep:

```xml
  <artifactId>ai4j-chatbot</artifactId>
  <packaging>jar</packaging>
```

- [ ] **Step 3: Update agent aggregator parent path**

Edit `services/agent/pom.xml` so the parent is:

```xml
    <parent>
        <groupId>org.ai4j</groupId>
        <artifactId>ai4j-factory</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
```

Keep:

```xml
    <artifactId>ai4j-agent</artifactId>
    <packaging>pom</packaging>
```

Remove the obsolete JUnit 3 dependency block:

```xml
    <dependencies>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <version>3.8.1</version>
            <scope>test</scope>
        </dependency>
    </dependencies>
```

- [ ] **Step 4: Update assistbot parent path**

Edit `services/agent/ai4j-agent-assistbot/pom.xml` so the parent is:

```xml
    <parent>
        <groupId>org.ai4j</groupId>
        <artifactId>ai4j-agent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
```

Keep:

```xml
    <artifactId>ai4j-agent-assistbot</artifactId>
    <packaging>jar</packaging>
```

- [ ] **Step 5: Update MCP aggregator parent path**

Edit `services/mcp/pom.xml` so the parent is:

```xml
    <parent>
        <groupId>org.ai4j</groupId>
        <artifactId>ai4j-factory</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
```

Keep:

```xml
    <artifactId>ai4j-mcp</artifactId>
    <packaging>pom</packaging>
```

- [ ] **Step 6: Update MCP spec parent path**

Edit `services/mcp/ai4j-mcp-spec/pom.xml` so the parent is:

```xml
    <parent>
        <groupId>org.ai4j</groupId>
        <artifactId>ai4j-mcp</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../pom.xml</relativePath>
    </parent>
```

Keep:

```xml
    <artifactId>ai4j-mcp-spec</artifactId>
    <packaging>jar</packaging>
```

- [ ] **Step 7: Check Maven sees the expected modules**

Run:

```bash
mvn -q -DskipTests validate
```

Expected: command exits with status 0. If dependency resolution fails because of network restrictions, rerun the command with approved network access before changing code.

- [ ] **Step 8: Commit Maven updates**

Run:

```bash
git add pom.xml services
git commit -m "build: update maven modules for services layout"
```

Expected: commit succeeds and does not include `.idea` files.

---

### Task 3: Add Root pnpm Workspace

**Files:**
- Create: `package.json`
- Create: `pnpm-workspace.yaml`
- Modify: `apps/ai4j-factory-ui/package.json`
- Delete: `apps/ai4j-factory-ui/package-lock.json`
- Delete or move: `apps/ai4j-factory-ui/pnpm-lock.yaml`

- [ ] **Step 1: Create root package.json**

Create `package.json` with exactly:

```json
{
  "name": "ai4j-factory",
  "version": "0.0.1",
  "private": true,
  "scripts": {
    "dev:web": "pnpm --filter ai4j-factory-ui dev",
    "build:web": "pnpm --filter ai4j-factory-ui build",
    "lint:web": "pnpm --filter ai4j-factory-ui lint"
  },
  "packageManager": "pnpm@10.17.1"
}
```

- [ ] **Step 2: Create pnpm workspace file**

Create `pnpm-workspace.yaml` with exactly:

```yaml
packages:
  - apps/*
```

- [ ] **Step 3: Confirm frontend package identity**

Check `apps/ai4j-factory-ui/package.json` contains:

```json
"name": "ai4j-factory-ui"
```

If the name differs, set it to `ai4j-factory-ui`.

- [ ] **Step 4: Remove npm lockfile**

Run:

```bash
git rm apps/ai4j-factory-ui/package-lock.json
```

Expected: file is staged for deletion.

- [ ] **Step 5: Move existing pnpm lockfile to root**

Run:

```bash
git mv apps/ai4j-factory-ui/pnpm-lock.yaml pnpm-lock.yaml
```

Expected: root `pnpm-lock.yaml` exists.

- [ ] **Step 6: Refresh lockfile for workspace paths**

Run:

```bash
pnpm install --lockfile-only
```

Expected: root `pnpm-lock.yaml` is updated for the workspace and command exits with status 0. If dependency resolution fails because of network restrictions, rerun the command with approved network access before changing package versions.

- [ ] **Step 7: Check workspace package discovery**

Run:

```bash
pnpm list --depth -1
```

Expected output includes both workspace packages:

```text
ai4j-factory
ai4j-factory-ui
```

- [ ] **Step 8: Commit pnpm workspace updates**

Run:

```bash
git add package.json pnpm-workspace.yaml pnpm-lock.yaml apps/ai4j-factory-ui/package.json
git commit -m "build: add pnpm workspace for apps"
```

Expected: commit succeeds and includes removal of `apps/ai4j-factory-ui/package-lock.json`.

---

### Task 4: Update Ignore Rules and Path References

**Files:**
- Modify: `.gitignore`
- Inspect: `docker-compose.yml`
- Inspect: `apps/ai4j-factory-ui/**/*.ts`
- Inspect: `apps/ai4j-factory-ui/**/*.tsx`
- Inspect: `apps/ai4j-factory-ui/*.mjs`
- Inspect: `apps/ai4j-factory-ui/*.ts`

- [ ] **Step 1: Update .gitignore for root and nested outputs**

Ensure `.gitignore` contains these lines:

```gitignore
target/
**/target/
node_modules/
**/node_modules/
.next/
**/.next/
out/
**/out/
coverage/
**/coverage/
*.tsbuildinfo
```

Keep existing IDE and OS ignore rules. Do not add negation rules for `.idea`.

- [ ] **Step 2: Search for stale old project paths**

Run:

```bash
rg -n "ai4j-factory-ui|ai4j-chatbot|ai4j-agent|ai4j-mcp" README.md docker-compose.yml pom.xml services apps
```

Expected: references either point to the new paths or are package/module names that intentionally keep the old `ai4j-` identity.

- [ ] **Step 3: Check docker-compose path references**

Open `docker-compose.yml`. If it contains no build context or volume path referencing moved directories, do not edit it. If it references old paths, update only those paths. Example replacement:

```yaml
build:
  context: services/ai4j-chatbot
```

- [ ] **Step 4: Check frontend local path references**

Run:

```bash
rg -n "\\.\\./\\.\\.|ai4j-chatbot|ai4j-agent|ai4j-mcp" apps/ai4j-factory-ui
```

Expected: no path reference requires changes. API URL strings may remain unchanged if they are runtime URLs rather than filesystem paths.

- [ ] **Step 5: Commit ignore and path-reference updates**

Run:

```bash
git add .gitignore docker-compose.yml apps services README.md
git commit -m "chore: update generated file ignores and path references"
```

Expected: commit succeeds if there are changes. If no files changed, skip this commit.

---

### Task 5: Update README

**Files:**
- Modify: `README.md`

- [ ] **Step 1: Replace README with monorepo overview**

Update `README.md` to include:

````markdown
# ai4j-factory

AI-Factory is an enterprise AI service framework for building and deploying AI-enabled applications and backend services.

## Repository Layout

```text
.
|-- apps/
|   `-- ai4j-factory-ui/
|-- services/
|   |-- ai4j-chatbot/
|   |-- agent/
|   |   `-- ai4j-agent-assistbot/
|   `-- mcp/
|       `-- ai4j-mcp-spec/
|-- pom.xml
|-- package.json
`-- pnpm-workspace.yaml
```

`apps` contains frontend applications. `services` contains backend services and backend domain groups. Concrete modules keep the `ai4j-` prefix, while grouping directories such as `agent` and `mcp` stay short.

## Java Commands

```bash
mvn test
mvn -pl services/ai4j-chatbot spring-boot:run
mvn -pl services/agent/ai4j-agent-assistbot spring-boot:run
mvn -pl services/mcp/ai4j-mcp-spec spring-boot:run
```

## Frontend Commands

```bash
pnpm install
pnpm dev:web
pnpm build:web
pnpm lint:web
```

## Package Management

Use pnpm for frontend dependencies. The pnpm workspace is defined in `pnpm-workspace.yaml`, and the lockfile lives at the repository root.
````

- [ ] **Step 2: Confirm README references valid paths**

Run:

```bash
rg -n "services/ai4j-chatbot|services/agent/ai4j-agent-assistbot|services/mcp/ai4j-mcp-spec|apps/ai4j-factory-ui" README.md
```

Expected: all four target paths are present.

- [ ] **Step 3: Commit README update**

Run:

```bash
git add README.md
git commit -m "docs: document monorepo workspace"
```

Expected: commit succeeds.

---

### Task 6: Final Verification

**Files:**
- Inspect only unless verification reveals a configuration error.

- [ ] **Step 1: Confirm no stale old top-level project directories remain**

Run:

```bash
find . -maxdepth 1 -type d -name 'ai4j-*' -print
```

Expected: no output.

- [ ] **Step 2: Confirm Maven project files are in target layout**

Run:

```bash
find services -name pom.xml -print
```

Expected output:

```text
services/ai4j-chatbot/pom.xml
services/agent/pom.xml
services/agent/ai4j-agent-assistbot/pom.xml
services/mcp/pom.xml
services/mcp/ai4j-mcp-spec/pom.xml
```

- [ ] **Step 3: Run Java validation**

Run:

```bash
mvn test
```

Expected: all Maven modules build and tests pass. If dependency resolution fails because of network restrictions, rerun with approved network access. If tests fail because of real code or configuration errors introduced by the migration, fix the migration and rerun.

- [ ] **Step 4: Run frontend workspace install check**

Run:

```bash
pnpm install --frozen-lockfile
```

Expected: install succeeds from the repository root. If the lockfile needed regeneration in Task 3, this should pass without changing `pnpm-lock.yaml`.

- [ ] **Step 5: Run frontend build**

Run:

```bash
pnpm --filter ai4j-factory-ui build
```

Expected: Next.js build completes successfully.

- [ ] **Step 6: Run frontend lint**

Run:

```bash
pnpm --filter ai4j-factory-ui lint
```

Expected: lint command completes successfully. If the existing Next.js version no longer supports `next lint`, update the script to a supported lint command or document the pre-existing incompatibility before finalizing.

- [ ] **Step 7: Search for stale filesystem references**

Run:

```bash
rg -n "(^|[\"' ./])ai4j-factory-ui/|(^|[\"' ./])ai4j-chatbot/|(^|[\"' ./])ai4j-agent/|(^|[\"' ./])ai4j-mcp/" .
```

Expected: no references to old top-level directory paths remain. Module names without trailing slashes are acceptable.

- [ ] **Step 8: Confirm user changes remain unstaged**

Run:

```bash
git status --short
```

Expected: implementation changes are committed or ready for final commit. Pre-existing `.idea/compiler.xml` and `.idea/encodings.xml` may remain modified and must not be included unless the user explicitly requests it.

- [ ] **Step 9: Final commit for verification fixes**

If verification required small configuration fixes, commit them:

```bash
git add pom.xml package.json pnpm-workspace.yaml pnpm-lock.yaml apps services .gitignore README.md docker-compose.yml
git commit -m "chore: finalize monorepo workspace migration"
```

Expected: commit succeeds if there are changes. If no changes remain after earlier commits, skip this step.

---

## Self-Review

- Spec coverage: The plan covers the target layout, Maven workspace, pnpm workspace, migration exclusions, README, ignore rules, stale path checks, and verification commands from the design.
- Placeholder scan: No unresolved marker patterns or placeholder steps are present.
- Scope check: The plan stays focused on repository structure and build/workspace configuration. It does not include business logic changes, Java package renames, shared library extraction, Nx, or Turborepo.
