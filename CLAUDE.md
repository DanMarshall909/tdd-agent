# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

## Build Commands

```bash
# Build the project
./gradlew build

# Run plugin in sandbox IDE
./gradlew runIde

# Run tests
./gradlew test

# Run a single test class
./gradlew test --tests "dev.agent.PromptsTest"

# Package plugin for distribution
./gradlew buildPlugin
```

## Project Structure

```
tdd-agent/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── src/
    ├── main/
    │   ├── kotlin/dev/agent/
    │   │   ├── TddToolWindowFactory.kt   # Tool window entry
    │   │   ├── TddPanel.kt               # Main UI panel
    │   │   ├── OpenCodeAdapter.kt        # LLM integration
    │   │   ├── TestInserter.kt           # PSI manipulation
    │   │   ├── TestRunner.kt             # Test execution
    │   │   └── Prompts.kt                # Prompt templates
    │   └── resources/
    │       └── META-INF/
    │           └── plugin.xml
    └── test/
        └── kotlin/dev/agent/
            └── ...
```

## Key Classes

### TddPanel

Main UI component. Kotlin UI DSL.

```kotlin
class TddPanel(private val project: Project) : JPanel(BorderLayout()) {
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
    suspend fun chat(prompt: String): String {
        // Execute: opencode run --format json "<prompt>"
        // Parse response
        // Return generated code
    }
}
```

### TestInserter

PSI manipulation for inserting code.

```kotlin
class TestInserter(private val project: Project) {
    fun findTestFile(productionFile: PsiFile): KtFile?
    fun findInsertionPoint(testFile: KtFile): PsiElement?
    fun insertTest(testFile: KtFile, code: String)
    fun insertImplementation(productionFile: KtFile, code: String)
}
```

### TestRunner

Executes tests and captures results.

```kotlin
class TestRunner(private val project: Project) {
    suspend fun runTests(testFile: PsiFile): TestResult
    
    data class TestResult(
        val passed: Boolean,
        val totalTests: Int,
        val failedTests: List<String>,
        val output: String
    )
}
```

## Prompt Templates

### Test Generation

```kotlin
fun buildTestPrompt(step: String, context: TestContext): String = """
    Generate a Kotest BehaviorSpec test block.
    
    BDD Step: $step
    
    Class under test: ${context.className}
    Existing methods: ${context.methods.joinToString()}
    
    Return ONLY the given/when/then block. No imports, no class wrapper.
    Use Kotest matchers: shouldBe, shouldBeInstanceOf, shouldThrow
    Use MockK for mocks: every { }, verify { }
""".trimIndent()
```

### Implementation Generation

```kotlin
fun buildImplPrompt(test: String, error: String?): String = """
    Make this test pass with MINIMAL code.
    
    Test:
    $test
    
    ${error?.let { "Error: $it" } ?: ""}
    
    Return ONLY the function body. No class wrapper.
    Write the simplest code that passes. No over-engineering.
""".trimIndent()
```

## PSI Patterns

### Finding Kotest BehaviorSpec

```kotlin
fun findBehaviorSpec(file: KtFile): KtClass? {
    return file.declarations
        .filterIsInstance<KtClass>()
        .find { ktClass ->
            ktClass.superTypeListEntries.any { entry ->
                entry.text.contains("BehaviorSpec")
            }
        }
}
```

### Finding Insertion Point

```kotlin
fun findInsertionPoint(specClass: KtClass): PsiElement? {
    val body = specClass.body ?: return null
    
    // Find the lambda passed to BehaviorSpec constructor
    val initBlock = specClass.primaryConstructor
        ?.valueParameterList
        ?.parameters
        ?.firstOrNull()
    
    // Or find last given block
    val lastGiven = body.children
        .filterIsInstance<KtCallExpression>()
        .lastOrNull { it.calleeExpression?.text == "given" }
    
    return lastGiven ?: body.lBrace
}
```

### Inserting Code

```kotlin
fun insertTest(file: KtFile, code: String, insertionPoint: PsiElement) {
    WriteCommandAction.runWriteCommandAction(project) {
        val factory = KtPsiFactory(project)
        val element = factory.createExpression(code)
        
        insertionPoint.parent.addAfter(element, insertionPoint)
        
        // Format
        CodeStyleManager.getInstance(project).reformat(element)
        
        // Optimize imports
        KotlinImportOptimizer().processFile(file)
    }
}
```

## Running Tests Programmatically

```kotlin
suspend fun runTests(testFile: PsiFile): TestResult {
    val runManager = RunManager.getInstance(project)
    
    // Find or create run configuration
    val config = runManager.findConfigurationByName(testFile.name)
        ?: createTestConfiguration(testFile)
    
    // Execute
    val executor = DefaultRunExecutor.getRunExecutorInstance()
    val environment = ExecutionEnvironmentBuilder
        .create(executor, config)
        .build()
    
    return withContext(Dispatchers.IO) {
        val result = CompletableDeferred<TestResult>()
        
        ProgramRunnerUtil.executeConfiguration(environment, object : ProgramRunner.Callback {
            override fun processTerminated(exitCode: Int) {
                result.complete(parseTestResults(exitCode))
            }
        })
        
        result.await()
    }
}
```

## Error Handling

```kotlin
sealed class TddError {
    data class LlmError(val message: String) : TddError()
    data class InsertionError(val message: String) : TddError()
    data class TestRunError(val message: String) : TddError()
}

// Use Result type
suspend fun generateTest(step: String): Result<String>
fun insertTest(code: String): Result<Unit>
suspend fun runTests(): Result<TestResult>
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
        val code = openCode.chat(prompt)  // Background
        
        withContext(Dispatchers.EDT) {
            outputArea.text = code  // EDT
        }
    }
}

// PSI modification
WriteCommandAction.runWriteCommandAction(project) {
    // Safe to modify PSI here
}
```

## Testing the Plugin

### Unit Tests

```kotlin
class PromptsTest {
    @Test
    fun `test prompt includes class context`() {
        val context = TestContext(className = "UserService", methods = listOf("login"))
        val prompt = buildTestPrompt("user can login", context)
        
        prompt shouldContain "UserService"
        prompt shouldContain "login"
    }
}
```

### Integration Tests

Use IntelliJ test framework with fixtures:

```kotlin
class TestInserterTest : BasePlatformTestCase() {
    fun `test finds BehaviorSpec class`() {
        val file = myFixture.configureByText("Test.kt", """
            class UserServiceTest : BehaviorSpec({
                given("something") { }
            })
        """.trimIndent())
        
        val inserter = TestInserter(project)
        val spec = inserter.findBehaviorSpec(file as KtFile)
        
        assertNotNull(spec)
        assertEquals("UserServiceTest", spec?.name)
    }
}
```

## Common Gotchas

### PSI is Read-Only by Default

```kotlin
// ❌ Wrong - will throw
element.delete()

// ✅ Right - wrap in write action
WriteCommandAction.runWriteCommandAction(project) {
    element.delete()
}
```

### EDT for UI Updates

```kotlin
// ❌ Wrong - might not be on EDT
textField.text = "value"

// ✅ Right - ensure EDT
ApplicationManager.getApplication().invokeLater {
    textField.text = "value"
}

// Or with coroutines
withContext(Dispatchers.EDT) {
    textField.text = "value"
}
```

### Service Lifecycle

```kotlin
// ❌ Wrong - might outlive project
object Singleton { 
    lateinit var project: Project 
}

// ✅ Right - use project service
@Service(Service.Level.PROJECT)
class TddService(private val project: Project) {
    companion object {
        fun getInstance(project: Project): TddService =
            project.service()
    }
}
```

## Useful IntelliJ APIs

| Task | API |
|------|-----|
| Find file | `FilenameIndex.getFilesByName()` |
| Parse Kotlin | `KtPsiFactory(project)` |
| Run config | `RunManager.getInstance(project)` |
| Execute tests | `ProgramRunnerUtil.executeConfiguration()` |
| Format code | `CodeStyleManager.getInstance(project)` |
| Notifications | `NotificationGroupManager.getInstance()` |
| Progress | `ProgressManager.getInstance()` |
| Settings | `PersistentStateComponent` |

## Resources

- [IntelliJ Platform SDK Docs](https://plugins.jetbrains.com/docs/intellij/)
- [Kotlin PSI](https://plugins.jetbrains.com/docs/intellij/kotlin.html)
- [Kotest Docs](https://kotest.io/docs/framework/framework.html)
- [OpenCode CLI](https://github.com/anthropics/opencode)
