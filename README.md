# TDD Agent

An IntelliJ plugin that enforces one-test-at-a-time TDD with LLM assistance.

## What It Does

```
You: "expired password blocks login"

Agent: Here's the test:

    given("a user with expired password") {
        val user = createUser(passwordExpired = true)
        
        `when`("they attempt to login") {
            val result = authService.login(user.email, "pass")
            
            then("login is blocked") {
                result.shouldBeInstanceOf<LoginResult.PasswordExpired>()
            }
        }
    }

[Insert & Run]

● Test failed ✓

Agent: Here's the implementation:

    fun login(email: String, password: String): LoginResult {
        val user = userRepo.findByEmail(email) ?: return NotFound
        if (user.passwordExpired) return PasswordExpired
        // ...
    }

[Insert & Run]

✓ All tests pass

Next step?
```

## Why?

LLMs write tests and implementation together. That's not TDD.

This plugin enforces:
- **One test at a time** - no batch generation
- **Red first** - test must fail before implementation
- **Green** - implementation must pass all tests
- **Minimal** - only code needed to pass the test

## Installation

### Prerequisites
- IntelliJ IDEA 2023.3+
- OpenCode CLI installed

### Install Plugin

Marketplace install is planned for M6. For now, build and install from disk.

```
Settings → Plugins → ⚙️ → Install Plugin from Disk → tdd-agent.zip
```

## Usage

1. Open tool window: `View → Tool Windows → TDD Agent`
2. Enter a BDD step: "user with expired password cannot login"
3. Click "Generate Test"
4. Review generated test
5. Click "Insert & Run"
6. Verify test fails (red)
7. Click "Generate Implementation"
8. Review generated implementation
9. Click "Insert & Run"
10. Verify all tests pass (green)
11. Next step

## Test Framework

Generates [Kotest](https://kotest.io/) BehaviorSpec tests:

```kotlin
class UserServiceTest : BehaviorSpec({
    given("a user with expired password") {
        `when`("they attempt to login") {
            then("login is blocked") {
                // ...
            }
        }
    }
})
```

JUnit 5 support is planned (M6).

## Keyboard Shortcuts

Planned for M6.

## Configuration

Planned for M6.

## Test Runner

Current implementation runs Gradle CLI tests for the generated test class. IDE-native test runner integration is tracked in M6-008.

## Project Setup

### Kotest (recommended)

```kotlin
// build.gradle.kts
dependencies {
    testImplementation("io.kotest:kotest-runner-junit5:5.8.0")
    testImplementation("io.kotest:kotest-assertions-core:5.8.0")
    testImplementation("io.mockk:mockk:1.13.9")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
```

### Coverage (optional)

```kotlin
// build.gradle.kts
plugins {
    id("org.jetbrains.kotlinx.kover") version "0.9.5"
}
```

## How It Works

```
┌──────────────────────────────────────────────────────────┐
│                        Plugin                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │   UI Panel  │  │  Insertion  │  │ Test Runner │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
│         │                │                │             │
│         ▼                ▼                ▼             │
│  ┌──────────────────────────────────────────────────┐  │
│  │              IntelliJ Platform APIs              │
│  │  • PSI (code structure)                          │
│  │  • Editor operations                             │
│  │  • Notifications                                 │
│  └──────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────┘
                          │
                          ▼
┌──────────────────────────────────────────────────────────┐
│                     OpenCode CLI                         │
│  • Sends prompts                                         │
│  • Receives generated code                               │
└──────────────────────────────────────────────────────────┘
```

**LLM generates:** Test body, implementation body (small snippets)

**IDE handles:** File detection, insertion, formatting, test execution

## Limitations

- Kotlin only (for now)
- Requires OpenCode CLI
- Single project focus
- IDE test runner integration pending

## Roadmap

- [x] Basic TDD loop
- [x] Kotest BehaviorSpec generation
- [x] Auto-insert and run
- [ ] BDD scenario generation
- [ ] Coverage integration
- [ ] JUnit 5 support
- [ ] Multi-language support

## Development

### Prerequisites

1. **Java 21** (required for builds)
   ```bash
   winget install EclipseAdoptium.Temurin.21.JDK
   ```
   Verify: `java -version`

2. **OpenCode CLI** (required for LLM integration)
   ```bash
   pip install opencode
   ```
   Or: https://github.com/anthropics/opencode

3. **JetBrains IDE** (IntelliJ IDEA, Rider, etc.)
   - Open `C:\code\flo2` as a project
   - IDE auto-detects Gradle and downloads dependencies

### Build & Test

```bash
cd C:\code\flo2

# Build project
gradlew.bat build

# Run tests
gradlew.bat test

# Run single test
gradlew.bat test --tests "dev.agent.PromptsTest"

# Build plugin (later, after IDE integration)
gradlew.bat buildPlugin

# Run in sandbox IDE (later, after IDE integration)
gradlew.bat runIde
```

### Git Setup

```bash
cd C:\code\flo2

# Configure committer info
git config user.email "you@example.com"
git config user.name "Your Name"

# Make initial commit
git add .
git commit -m "M1-001: Initial project scaffold"
```

### Project Structure

```
tdd-agent/
├── src/main/kotlin/dev/agent/        # Core logic
│   ├── Main.kt                        # CLI entry point
│   └── OpenCodeAdapter.kt             # LLM integration
├── src/main/kotlin/dev/agent/plugin/  # Plugin code
├── src/test/kotlin/dev/agent/         # Tests
├── build.gradle.kts                   # Gradle config
├── gradlew / gradlew.bat              # Gradle wrapper
├── gradle/wrapper/                    # Gradle wrapper files
└── .git/                              # Git repository
```

### Next Steps

1. Run build: `gradlew.bat build`
2. Open in JetBrains IDE
3. See `tickets.md` for milestone breakdown

## License

MIT
