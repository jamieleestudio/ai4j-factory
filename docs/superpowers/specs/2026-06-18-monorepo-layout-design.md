# Monorepo Layout Design

Date: 2026-06-18

## Goal

Reorganize the repository into a clearer monorepo layout with separate `apps` and `services` areas while preserving existing module identity and avoiding business-code changes.

The migration should make the repository easier to browse from the root and from individual modules. It should also provide one root entry point for Java and frontend build commands.

## Current State

The repository currently has these top-level project directories:

- `ai4j-chatbot`
- `ai4j-agent`
- `ai4j-mcp`
- `ai4j-factory-ui`

Java already uses a root Maven aggregator `pom.xml`. The frontend is a standalone Next.js project with both `pnpm-lock.yaml` and `package-lock.json` inside `ai4j-factory-ui`.

The Java Maven coordinates are inconsistent: the root uses `com.example`, while at least one child module uses `org.example`, even though Java packages already use `org.ai4j`.

## Target Layout

```text
.
├── apps/
│   └── ai4j-factory-ui/
├── services/
│   ├── ai4j-chatbot/
│   ├── agent/
│   │   └── ai4j-agent-assistbot/
│   └── mcp/
│       └── ai4j-mcp-spec/
├── pom.xml
├── package.json
├── pnpm-workspace.yaml
├── docker-compose.yml
└── README.md
```

Directory naming rules:

- `apps` contains user-facing or independently runnable frontend applications.
- `services` contains backend services and backend domain groups.
- Concrete modules keep the `ai4j-` prefix, such as `ai4j-chatbot`, `ai4j-agent-assistbot`, and `ai4j-mcp-spec`.
- Domain grouping directories under `services` do not use the `ai4j-` prefix, such as `agent` and `mcp`.

This avoids repeated names such as `services/ai4j-agent/ai4j-agent-assistbot` while keeping concrete modules recognizable when opened directly in an IDE.

## Java Workspace Design

The root `pom.xml` remains the authoritative Maven entry point. Its module list should become:

- `services/ai4j-chatbot`
- `services/mcp`
- `services/agent`

`services/agent/pom.xml` remains a Maven aggregator for the agent domain and includes:

- `ai4j-agent-assistbot`

`services/mcp/pom.xml` remains a Maven aggregator for the MCP domain and includes:

- `ai4j-mcp-spec`

Each child module should update its `parent.relativePath` for the new location.

Maven coordinates should be normalized to `org.ai4j` for the root and child modules. Versions should be inherited from the root wherever possible. This keeps dependency and plugin versions centralized and avoids copying parent configuration into each service.

Individual service directories may be opened in an IDE for browsing and local runs, but root Maven remains the supported build authority.

## Frontend Workspace Design

Move the frontend project to:

```text
apps/ai4j-factory-ui
```

Add a private root `package.json` for workspace-level scripts. Add `pnpm-workspace.yaml` with:

```yaml
packages:
  - apps/*
```

The frontend package should keep its package name:

```json
"name": "ai4j-factory-ui"
```

Use pnpm as the single frontend package manager. The existing `package-lock.json` should be removed from the frontend project or otherwise excluded from the final workspace state. The pnpm lockfile should be generated at the repository root.

Recommended root scripts:

- `dev:web`
- `build:web`
- `lint:web`

These scripts should delegate to the `ai4j-factory-ui` workspace package.

## Migration Scope

The migration should include:

- Move `ai4j-factory-ui` to `apps/ai4j-factory-ui`.
- Move `ai4j-chatbot` to `services/ai4j-chatbot`.
- Move `ai4j-agent` to `services/agent`.
- Move `ai4j-mcp` to `services/mcp`.
- Update Maven module paths and parent relative paths.
- Normalize Maven `groupId` values to `org.ai4j`.
- Add root pnpm workspace configuration.
- Update README with the new layout and common commands.
- Review `.gitignore` for `node_modules`, `.next`, `target`, and other generated files.

The migration should not include:

- Java package renames.
- API route changes.
- Shared library extraction.
- Nx, Turborepo, or another monorepo task runner.
- Business logic changes.
- Docker deployment rewrites beyond necessary path reference updates.

## Compatibility Notes

The root remains the main build entry point:

- Java: root Maven aggregator.
- Frontend: root pnpm workspace.

Modules should still be easy to open individually in an IDE. Full independent Maven builds from every nested service directory are not required because that would either duplicate root configuration or require publishing a parent POM.

## Verification

After implementation, verify the migration with the strongest commands available in the local environment:

- `mvn test`
- `pnpm install`
- `pnpm --filter ai4j-factory-ui build`

If dependency downloads are restricted, use lighter checks that still validate the configuration shape, such as:

- Maven effective POM checks from the root.
- pnpm workspace/package resolution checks.
- Frontend package command discovery through pnpm.

The implementation is complete only when the root workspace can identify all Java and frontend modules and no stale paths remain in build files or documentation.
