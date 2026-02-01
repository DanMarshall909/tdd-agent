# TDD Agent - Project Overview

## Purpose
Convert a working CLI TDD Agent into an IntelliJ plugin while preserving all core logic. The plugin enforces strict TDD workflow: generate test → run (fail) → generate implementation → run (pass) → repeat.

## Tech Stack
- **Language:** Kotlin 1.9.24 (JVM 21)
- **Build:** Gradle with Kotlin DSL
- **Current (CLI):** Spring Boot 3.2.0 (being removed)
- **Target (Plugin):** IntelliJ Platform SDK 2023.3+
- **Test Framework:** Kotest (BehaviorSpec)
- **Mocking:** MockK
- **LLM:** OpenCode CLI via subprocess
- **Code Style:** Kotlin with ktlint

## Current Architecture
- **TddOrchestrator:** Core orchestration (preserved)
- **LlmAdapter (interface):** Code generation abstraction
  - **OpenCodeAdapter:** Implements LlmAdapter, wraps OpenCode CLI
- **CodeRunner (interface):** Test execution abstraction
  - **CliCodeRunner:** CLI implementation (being removed)
- **CodeInserter (interface):** Code insertion abstraction
  - **CliCodeInserter:** Direct file manipulation (being removed)
- **Prompts.kt:** Test and implementation prompt builders
- **Main.kt:** Spring Boot CLI entry point (being modified)
- **TddConfig.kt:** Spring DI config (being deleted)

## M2 Goal: Plugin Foundation
1. Remove Spring Boot → use IntelliJ Services (project-level)
2. Create plugin.xml with tool window registration
3. Build basic UI panel with:
   - BDD step input field
   - Generated code output (syntax-highlighted)
   - Generate Test / Generate Implementation buttons
   - Copy to Clipboard button
4. Keep core logic (TddOrchestrator, OpenCodeAdapter) unchanged
5. Create stub implementations of CodeRunner and CodeInserter (real impl in M3)

## Key Files Structure
```
src/main/kotlin/dev/agent/
├── TddOrchestrator.kt       (UNCHANGED)
├── OpenCodeAdapter.kt        (UNCHANGED)
├── LlmAdapter.kt            (UNCHANGED)
├── CodeInserter.kt          (UNCHANGED interface)
├── CodeRunner.kt            (UNCHANGED interface)
├── Prompts.kt               (UNCHANGED)
├── Main.kt                  (MODIFIED - remove Spring)
├── CliCodeInserter.kt       (REMOVED from Spring)
├── CliCodeRunner.kt         (REMOVED from Spring)
├── TddConfig.kt             (DELETED - replaced by TddService)
└── plugin/                  (NEW)
    ├── TddService.kt        (IntelliJ project service)
    ├── TddToolWindowFactory.kt
    ├── TddPanel.kt          (Main UI)
    ├── IdeCodeInserter.kt   (Stub for M2)
    └── IdeCodeRunner.kt     (Stub for M2)

src/main/resources/META-INF/
└── plugin.xml              (NEW - plugin descriptor)

src/main/resources/icons/
└── toolwindow.svg          (NEW - tool window icon)
```

## Build Commands
- `./gradlew build` - Full build
- `./gradlew runIde` - Run in IntelliJ sandbox
- `./gradlew test` - Run unit tests
- `./gradlew spotless` - Check/fix code formatting

## Threading Model
- UI updates: EDT (Event Dispatch Thread)
- LLM calls: Background coroutine (Dispatchers.Default)
- PSI writes: WriteCommandAction (on EDT)
