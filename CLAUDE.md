# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Task Tracking with Beads

We track all work in **Beads** (`bd`) instead of markdown files. Run `bd quickstart` to
learn the workflow.

### Starting a session

1. Run `bd ready` to find unblocked work.
2. Pick a task and run `bd update <id> --status in_progress`.
3. Do the work, run tests, commit.
4. Run `bd close <id> --reason "summary of what was done"`.
5. If you discover new work while coding, file it immediately:
   `bd create "title" -t bug|task|feature -p 0-4`
   and link it: `bd dep add <new-id> <current-id> --type discovered-from`.
6. After closing, run `bd ready` again and suggest the next task to the user.

### Rules

- **Never use TICKETS.md for new work.** All new issues go into beads.
- Always check `bd ready` at the start of a session before doing anything.
- File issues for problems you notice — do not silently skip them.
- Commit `.beads/issues.jsonl` with your other changes so state is shared via git.

## Project Overview

**TDD Agent** is an IntelliJ plugin that enforces strict TDD workflow:
1. Generate ONE test (LLM)
2. Insert and run (IDE) - must fail
3. Generate minimal implementation (LLM)
4. Insert and run (IDE) - must pass
5. Repeat

## Core Principle

**LLM generates snippets. IDE handles mechanics.**

| LLM Does | IDE Does |
|----------|----------|
| Write test body | Find test file |
| Write impl body | Insert at correct location |
| | Format code |
| | Run tests |
| | Report results |

## Tech Stack

| Component | Technology |
|-----------|------------|
| Language | Kotlin |
| Platform | IntelliJ Platform SDK |
| Test Framework | Kotest BehaviorSpec |
| Assertions | Kotest matchers |
| Mocking | MockK |
| LLM | OpenCode CLI |
| Build | Gradle Kotlin DSL |

## Project Structure

```
tdd-agent/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── src/
    ├── main/
    │   ├── kotlin/dev/agent/             # Orchestrator, prompts, adapters
    │   ├── kotlin/dev/agent/plugin/      # UI + PSI helpers
    │   └── resources/META-INF/plugin.xml
    └── test/kotlin/dev/agent/
```

## Key Classes

### TddPanel

Main UI component (Swing).

```kotlin
class TddPanel(private val project: Project) : JBPanel<TddPanel>(BorderLayout()) {
    // Input field for BDD step
    // Output area for generated code
    // Insert & Run button
    // Status display
}
```

### OpenCodeAdapter

Subprocess wrapper for OpenCode CLI.

```kotlin
class OpenCodeAdapter(
    private val model: String? = null,
    private val timeout: Duration = 5.minutes
) {
    suspend fun generate(prompt: String): String
}
```

### IdeCodeInserter

PSI manipulation for inserting code.

```kotlin
class IdeCodeInserter(private val project: Project) : CodeInserter {
    suspend fun insertTest(testCode: String): Boolean
    suspend fun insertImplementation(implCode: String): Boolean
}
```

### IdeCodeRunner

Executes tests and captures results. Current implementation runs Gradle CLI test task for the target class (IDE-native runner is tracked as M6-008).

```kotlin
class IdeCodeRunner(private val project: Project) : CodeRunner {
    suspend fun runTests(): CodeRunner.Result
}
```

## PSI Patterns

### Finding Kotest BehaviorSpec

```kotlin
fun findBehaviorSpec(file: KtFile): KtClassOrObject? {
    return file.declarations
        .filterIsInstance<KtClassOrObject>()
        .find { ktClass ->
            ktClass.superTypeListEntries.any { entry ->
                entry.text.contains("BehaviorSpec")
            }
        }
}
```

### Finding Insertion Point

```kotlin
fun findInsertionPoint(file: KtFile): PsiElement? {
    // Find last given(...) call in BehaviorSpec lambda
}
```

## Threading

- UI updates: EDT (Event Dispatch Thread)
- LLM calls: Background coroutine
- PSI writes: WriteCommandAction (on EDT)
- Test execution: Background

```kotlin
// UI action triggers background work
button.addActionListener {
    scope.launch(Dispatchers.Default) {
        val code = openCode.generate(prompt)  // Background
        ApplicationManager.getApplication().invokeLater {
            outputArea.text = code
        }
    }
}

// PSI modification
WriteCommandAction.runWriteCommandAction(project) {
    // Safe to modify PSI here
}
```

## Useful IntelliJ APIs

| Task | API |
|------|-----|
| Find file | `ProjectFileIndex` + source roots |
| Parse Kotlin | `KtPsiFactory(project)` |
| Run config | `RunManager.getInstance(project)` (planned) |
| Execute tests | `ProgramRunnerUtil.executeConfiguration()` (planned) |
| Format code | `CodeStyleManager.getInstance(project)` |
| Notifications | `NotificationGroupManager.getInstance()` |

## Common Gotchas

- PSI is read-only by default; wrap writes in `WriteCommandAction`.
- UI updates must run on the EDT.
