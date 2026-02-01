# Repository Guidelines

## Project Structure & Module Organization
- `src/main/kotlin/dev/agent/` contains core logic (LLM adapter, prompts, orchestrator).
- `src/main/kotlin/dev/agent/plugin/` contains the IntelliJ plugin source (UI, PSI helpers, IDE runner/inserter).
- `src/main/resources/META-INF/plugin.xml` declares plugin metadata and extensions.
- `src/test/kotlin/dev/agent/` holds unit tests.
- `test-integration/` is a separate Gradle project for local-only integration tests and coverage; it is gitignored.
- Gradle configuration lives in `build.gradle.kts` with the wrapper scripts `gradlew` and `gradlew.bat`.

## Build, Test, and Development Commands
Run from the repo root (Windows examples shown; `./gradlew` works on macOS/Linux):
- `gradlew.bat build` — compile, run tests, and assemble the plugin.
- `gradlew.bat test` — run unit tests (Kotest on JUnit Platform).
- `gradlew.bat test --tests "dev.agent.PromptsTest"` — run a single test class.
- `gradlew.bat runIde` — launch a sandbox IDE with the plugin loaded.
- `gradlew.bat buildPlugin` — package a distributable plugin ZIP.
- `gradlew.bat spotlessApply` / `spotlessCheck` — format or verify Kotlin and Gradle files.

## Coding Style & Naming Conventions
- Kotlin uses 4-space indentation and 120-char max line length per `.editorconfig`.
- Formatting is enforced via Spotless + ktlint (`spotlessApply` before committing).
- Keep class and file names aligned (e.g., `TddPanel.kt` contains `TddPanel`).
- Prefer clear, action-oriented method names (e.g., `runTests`, `insertTest`).

## Testing Guidelines
- Primary framework: Kotest (BehaviorSpec) with MockK for mocking.
- Unit tests live in `src/test/kotlin/dev/agent/`.
- Integration tests (OpenCode CLI, coverage, end-to-end) are in `test-integration/` and are not committed; run with:
  - `cd test-integration`
  - `gradlew.bat test`

## Commit & Pull Request Guidelines
- Commit messages in this repo are imperative and often include a milestone prefix, e.g. `M1-003: Test and implementation generation prompts` or `Fix editor resource disposal and threading issues`.
- For PRs, include:
  - A short summary of behavior changes.
  - Relevant test commands run and results.
  - Screenshots/GIFs for UI changes in the tool window.

## Configuration & Prerequisites
- Java toolchain is 21 (see `build.gradle.kts`); ensure JDK 21 is installed.
- OpenCode CLI must be available in `PATH` for LLM integration and integration tests.
