# LANCord Workspace Rules & Agent Operating Protocol

This file defines the mandatory operating guidelines for all AI agents working within the **LANCord** workspace. All rules defined here are **ALWAYS-ON** and take precedence before any action is taken.

---

## 1. Mandatory Pre-Action Protocol: CodeGraph-First Intelligence

Before reading entire files, proposing architectural changes, or writing/modifying code:

1. **Symbol & Anti-Duplication Check**:
   - **MUST** query CodeGraph first using `codegraph explore <query>` or via MCP tool `codegraph_explore` (server: `codegraph`).
   - For specific classes/methods, use `codegraph node <ClassName>` or `codegraph node <MethodName>`.
   - **NEVER** duplicate existing classes, repository methods, protocol messages, or utility functions. If a method exists, **REUSE OR EXTEND IT**.

2. **Blast Radius / Impact Verification**:
   - Before modifying method signatures, models, or shared components, run `codegraph impact <method_name>` to verify all downstream callers.
   - Trace caller/callee flow using `codegraph callers <method_name>` and `codegraph callees <method_name>`.

3. **Surgical Inspection**:
   - Do not dump 500+ line files into context. Use CodeGraph to locate exact line numbers, then read only the target lines using `view_file` with `StartLine` and `EndLine`.

4. **Index Synchronization**:
   - Whenever new files, classes, or methods are added or modified, run `codegraph sync` to keep the local graph index updated.

---

## 2. Multi-Model Orchestration & Quota-Aware Task Routing

The agent operates as a **Model Orchestrator** to select or recommend the most appropriate model based on task complexity, reasoning depth, and **remaining quota / usage**.

### Model Registry & Quota Tiers

| Tier | Model | CLI ID / Setting | Quota | Best For | Avoid For |
|------|-------|-------------------|-------|----------|-----------|
| **Workhorse** | **Gemini 3.8 Flash** | `gemini-3.8-flash-high` | 🟢 **HIGH** (Primary Driver) | Boilerplate, CRUD, JavaFX UI, routine implementation, unit tests, fast iterations | Very deep concurrency / race condition analysis |
| **Workhorse** | **Gemini 3.7 Flash** | `gemini-3.7-flash-high` | 🟢 **HIGH** | Bulk code generation, backup workhorse, test suites | Tasks where 3.8 Flash is available and idle |
| **Economy** | **Gemini 3.6 Flash** | `gemini-3.6-flash-high` | 🟢 **HIGH** (Lowest Cost) | Documentation, simple file extraction, formatting, comments | Complex reasoning or nuanced logic |
| **Premium** | **Gemini 3.1 Pro** | `gemini-3.1-pro-high` | 🟡 **MEDIUM** | DB schema design, complex SQL queries, system integration, algorithm design | Simple boilerplate, routine CRUD |
| **Premium** | **Claude Sonnet 4.6 (Thinking)** | `claude-sonnet-4-6` | 🟡 **MEDIUM** | Concurrency, thread safety, network sockets, security audit, code review | Bulk code generation, trivial edits |
| **Premium** | **GPT-OSS 120B** | `gpt-oss-120b-medium` | 🟡 **MEDIUM** | Architecture review (second opinion), structured output, requirement analysis | Simple boilerplate |
| **Elite** | **Claude Opus 4.6 (Thinking)** | `claude-opus-4-6-thinking` | 🔴 **LOW** (Rationed) | Critical tie-breaking architecture decisions, mission-critical algorithms | Everyday tasks handled by cheaper models |

### Task Routing & Quota Rules
1. **Default Implementation**: Use **Gemini 3.8 Flash** for all routine features, JavaFX UI controllers, and general bug fixes to conserve high-tier quotas.
2. **Network & Concurrency Review**: When dealing with multi-threaded sockets, UDP packet streaming, or complex synchronization in `ClientHandler` / `UDPStreamSender`, recommend or engage **Claude Sonnet 4.6** for review.
3. **Database & Schema Changes**: Use **Gemini 3.1 Pro** for complex database migrations, table relations, or query optimizations.
4. **Quota Conservation Principle**:
   - Budget: ~60-70% tokens on Flash models, ~25-30% on Premium models, ~5-10% on Opus.
   - When a model's quota is running low, automatically fall back to the next available model in the tier hierarchy.

---

## 3. Mistake Prevention & Ledger Protocol

- Always consult known mistake patterns (`mistake-memory`) before writing code:
  - Java module-info (`exports` and `opens` to `javafx.fxml` and `com.fasterxml.jackson.databind`).
  - Idempotent directory creation (`Files.createDirectories` and `!Files.exists` checks).
  - Bidirectional DM message filtering (checking both sender and receiver IDs).
  - MySQL reserved keywords and safe SQL quoting.

---

## 4. Git Clean Workflow & Identity Protection

- All Git commits must be human-authored with conventional commit messages (`feat:`, `fix:`, `refactor:`).
- Author identity: `Khang <khangvq.23itb@vku.udn.vn>`.
- Zero AI watermarks, zero AI attribution tags in code or commit messages.
- Never track `.agents/` or internal scratch files in version control.
