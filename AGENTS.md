# Repository Guidelines

## Project Overview

**TDD Agent** (`tdd-agent`) is a Kotlin IntelliJ Platform plugin that enforces strict
TDD workflow using LLM-generated test and implementation code. The LLM generates
snippets; the IDE handles file location, insertion, formatting, and test execution.

## Project Structure

```
src/main/kotlin/dev/agent/           # Core: LlmAdapter, Prompts, TddOrchestrator
src/main/kotlin/dev/agent/plugin/    # IntelliJ plugin: UI (TddPanel), PSI helpers,
                                     #   IDE runner/inserter, services
src/main/kotlin/dev/agent/workflow/  # Workflow state machine, models, scenario parser
src/main/resources/META-INF/         # plugin.xml (extensions, tool window, notifications)
src/test/kotlin/dev/agent/           # Unit tests (Kotest BehaviorSpec)
test-integration/                    # Separate Gradle project for local-only integration
                                     #   tests (gitignored build artifacts)
```

## Build, Test, and Lint Commands

Run from the repo root. Use `./gradlew` on Linux/macOS or `gradlew.bat` on Windows.

| Command | Purpose |
|---------|---------|
| `./gradlew build` | Compile, run tests, assemble plugin |
| `./gradlew test` | Run all unit tests |
| `./gradlew test --tests "dev.agent.PromptsTest"` | Run a single test class |
| `./gradlew test --tests "dev.agent.workflow.WorkflowStateMachineTest"` | Run a test in a subpackage |
| `./gradlew spotlessApply` | Auto-format all Kotlin and Gradle files |
| `./gradlew spotlessCheck` | Verify formatting (fails build if violations) |
| `./gradlew runIde` | Launch sandbox IDE with the plugin loaded |
| `./gradlew buildPlugin` | Package distributable plugin ZIP |
| `./gradlew koverHtmlReport` | Generate code coverage report (excludes `plugin.*`) |

**Always run `spotlessApply` before committing.** The build will fail if formatting
is wrong.

### Integration Tests (local only)

```
cd test-integration && ../gradlew test
```

Requires OpenCode CLI in `PATH`. These tests are not committed to the repo.

## Prerequisites

- **JDK 21** (configured via `jvmToolchain(21)` in `build.gradle.kts`)
- **Gradle 8.13** (wrapper included; do not upgrade without updating wrapper)
- **OpenCode CLI** in `PATH` for LLM integration and integration tests

## Code Style

### Formatting Rules (enforced by Spotless + ktlint 0.50.0)

- **Indentation:** 4 spaces (no tabs)
- **Max line length:** 120 characters
- **Charset:** UTF-8
- **Line endings:** LF everywhere (enforced by `.gitattributes`; only `gradlew.bat` uses CRLF)
- **Trailing whitespace:** trimmed
- **Final newline:** required
- **Trailing commas:** not allowed (`ij_kotlin_allow_trailing_comma = false`)

### Import Ordering

Configured in `.editorconfig` as `*,java.**,javax.**,kotlin.**,^`:

1. Everything else (alphabetical) — `com.intellij.*`, `dev.agent.*`, `io.kotest.*`, etc.
2. `java.**`
3. `javax.**`
4. `kotlin.**`
5. Alias imports (`^`)

**No wildcard imports.** Each import is explicit and on its own line.

### Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Packages | lowercase dotted | `dev.agent.plugin` |
| Classes | PascalCase, matches filename | `TddPanel` in `TddPanel.kt` |
| Interfaces | PascalCase, no `I` prefix | `LlmAdapter`, `CodeRunner`, `CodeInserter` |
| Singleton objects | PascalCase | `ScenarioParser`, `WorkflowStateMachine` |
| Methods | camelCase, action-oriented | `runTests()`, `insertTest()`, `generateTestCode()` |
| Data classes | PascalCase, descriptive | `StepResult`, `WorkflowState`, `RequirementsData` |
| Enums | PascalCase class, UPPER_SNAKE values | `StepType.GIVEN`, `WorkflowPhase.RESEARCH` |
| Constants/loggers | `private val LOG` pattern | `Logger.getInstance(Foo::class.java)` |
| Test classes | PascalCase + `Test` suffix | `ScenarioParserTest`, `WorkflowStateMachineTest` |

### Class Structure Patterns

- **Interfaces** are minimal — method signatures with KDoc, no implementation.
- **Companion objects** hold `LOG` and `getInstance()` factory methods.
- **Data classes** use `copy()` for immutable state transitions.
- **Sealed classes** for event/result hierarchies (`WorkflowEvent`, `TransitionResult`).
- **Internal visibility** for PSI helpers (`internal object TestFileLocator`).
- **`@Service(Service.Level.PROJECT)`** for IntelliJ DI services.
- **`@Serializable`** for kotlinx.serialization data classes.

### Error Handling

- Wrap risky operations in `try/catch`; log with `LOG.error()` and surface via status UI.
- Throw `RuntimeException` for unrecoverable parse failures.
- Use null-safe returns with early bail: `?: return false`.
- IntelliJ notifications via `NotificationGroupManager` for user-facing errors.
- Background coroutine errors caught and forwarded to EDT via `invokeLater`.

### Documentation

- **KDoc (`/** ... */`)** on all interfaces and their methods.
- **KDoc** on key public classes with brief description.
- No documentation required on private methods or obvious implementations.

### Logging

Uses IntelliJ `com.intellij.openapi.diagnostic.Logger`:
```kotlin
companion object {
    private val LOG = Logger.getInstance(MyClass::class.java)
}
```
- `debug` for internal state, `info` for user-visible actions, `warn` for recoverable
  issues, `error` for failures.

## Testing Guidelines

### Framework

- **Kotest BehaviorSpec** (`io.kotest:kotest-runner-junit5:5.8.0`) on JUnit Platform
- **Kotest assertions** (`shouldBe`, `shouldContain`, `shouldBeInstanceOf`)
- **MockK** (`io.mockk:mockk:1.13.9`) for mocking (available but not yet heavily used)

### Test Structure

All tests use BehaviorSpec with Given/When/Then BDD structure:

```kotlin
class FooTest : BehaviorSpec({
    given("a context description") {
        `when`("an action occurs") {
            then("expected outcome") {
                result shouldBe expected
            }
        }
    }
})
```

### Test Naming

Follow [enterprise naming convention](https://enterprisecraftsmanship.com/posts/you-naming-tests-wrong/):
describe the scenario and expected behavior, not the method under test. Use natural
language in given/when/then strings.

### Test Organization

- Unit tests: `src/test/kotlin/dev/agent/` mirroring main source structure.
- Multiple test classes may exist in one file if closely related.
- Integration tests: `test-integration/src/test/kotlin/dev/agent/test/`.
- Kover coverage excludes `dev.agent.plugin.*` (IntelliJ UI code).

### Test Best Practices

- **Do not assert on magic strings** — reference error messages as constants.
- **Use parameterized tests** when testing multiple inputs with the same logic.
- **Run tests after every major change.**
- Keep tests focused and minimal; one logical assertion per `then` block.

## Threading Model (IntelliJ Plugin)

| Context | Thread | API |
|---------|--------|-----|
| UI updates | EDT | `invokeLater { ... }` |
| LLM calls | Background coroutine | `scope.launch(Dispatchers.Default)` |
| PSI writes | EDT via WriteCommandAction | `WriteCommandAction.runWriteCommandAction(project)` |
| Test execution | Background | Gradle CLI subprocess |

## Task Tracking with Beads

This project uses [Beads](https://github.com/steveyegge/beads) (`bd`) for issue
tracking instead of markdown files. The database lives in `.beads/` (prefix: `tdd`).

### Agent Workflow

```bash
bd ready                              # 1. Find unblocked work
bd update tdd-N --status in_progress  # 2. Claim a task
# ... do the work, run tests ...
bd close tdd-N --reason "summary"     # 3. Complete it
bd ready                              # 4. Suggest next task to user
```

### Filing New Work

When you discover bugs, TODOs, or follow-up work while coding, file them immediately:

```bash
bd create "title" -t bug -p 1
bd dep add <new-id> <current-id> --type discovered-from
```

### Key Commands

| Command | Purpose |
|---------|---------|
| `bd ready` | Show unblocked tasks (start here every session) |
| `bd list` | List all issues |
| `bd show tdd-N` | Show details of an issue |
| `bd create "title" -t type -p priority` | Create issue (types: bug/feature/task/epic/chore) |
| `bd update tdd-N --status in_progress` | Claim work |
| `bd close tdd-N --reason "why"` | Complete work |
| `bd dep add tdd-X tdd-Y --type blocks` | Add dependency (blocks, related, parent-child, discovered-from) |
| `bd dep tree tdd-N` | Visualize dependency graph |
| `bd stats` | Project statistics |

### Rules

- **Never create markdown files for task tracking.** Use beads for all new work.
- Always commit `.beads/issues.jsonl` alongside code changes.
- The `.beads/*.db` files are gitignored (local SQLite cache only).

## Commit & PR Guidelines

- **Commit messages:** imperative mood, often with milestone prefix:
  `M1-003: Test and implementation generation prompts` or
  `Fix editor resource disposal and threading issues`.
- **Branching:** trunk-based development on `master`. Create a short-lived branch per
  iteration; do not maintain multiple long-lived branches.
- **PRs must include:** summary of behavior changes, test commands run and results,
  screenshots/GIFs for UI changes.
- **Before committing:** run `spotlessApply`, clean up temporary files (build logs, etc.),
  ensure tests pass, and `git add .beads/issues.jsonl`.
