run # TDD Agent - Project Tickets (Revised)

## Philosophy

**Ship for yourself first.** Add features when you feel the pain.

## Milestones

| Milestone | Goal | Timeframe |
|-----------|------|-----------|
| M1 | CLI that generates test + impl | 3 days |
| M2 | IntelliJ plugin with tool window | 1 week |
| M3 | Auto-insert + auto-run | 1 week |
| M4 | BDD scenario generation | 1 week |
| M5 | Coverage integration | 1 week |
| M6 | Polish & release | 1 week |

**Total to usable: 2 weeks. Total to polished: 5 weeks.**

---

## M1: CLI Walking Skeleton (3 days) ✅ COMPLETE

Goal: Validate the TDD loop works before building plugin infrastructure.

### M1-001: Project Setup

**Estimate:** 1 hour

**Tasks:**
- [x] Create Gradle Kotlin project
- [x] Add dependencies: kotlinx-coroutines, kotlinx-serialization
- [x] Configure Kotlin 1.9+

**Structure:**
```
tdd-agent/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/kotlin/
    └── dev/agent/
        ├── Main.kt
        └── OpenCodeAdapter.kt
```

---

### M1-002: OpenCode Adapter

**Estimate:** 2 hours

**Tasks:**
- [x] Subprocess wrapper for `opencode run`
- [x] Pass prompt, get response
- [x] Handle timeout (5 min default)
- [x] Parse output

```kotlin
class OpenCodeAdapter(
    private val model: String? = null,
    private val timeout: Duration = 5.minutes
) {
    suspend fun chat(prompt: String): String
}
```

**Acceptance:**
- [x] Can send prompt, get response
- [x] Timeout works

---

### M1-003: Test Generation Prompt

**Estimate:** 2 hours

**Tasks:**
- [x] Prompt template for Kotest BehaviorSpec
- [x] Accepts: BDD step, context (optional)
- [x] Returns: just the `given/when/then` block

```kotlin
fun buildTestPrompt(step: String, context: String? = null): String
```

**Acceptance:**
- [x] Generates valid Kotest BehaviorSpec blocks
- [x] No imports, no class wrapper
- [x] Uses Kotest matchers

---

### M1-004: Implementation Generation Prompt

**Estimate:** 2 hours

**Tasks:**
- [x] Prompt template for minimal implementation
- [x] Accepts: failing test, error message
- [x] Returns: just the function/method body

```kotlin
fun buildImplPrompt(test: String, error: String? = null): String
```

**Acceptance:**
- [x] Generates minimal Kotlin code
- [x] No over-engineering
- [x] Compiles

---

### M1-005: CLI Loop

**Estimate:** 3 hours

**Tasks:**
- [x] Read BDD step from user
- [x] Generate test, display
- [x] Prompt: "Did test fail? (y/n)"
- [x] Generate implementation, display
- [x] Prompt: "Do tests pass? (y/n)"
- [x] Loop or exit

```kotlin
fun main() = runBlocking {
    // TDD loop
}
```

**Acceptance:**
- [x] Complete TDD cycle works manually
- [x] Can do multiple steps in sequence
- [x] Clean exit with "done"

---

## M2: IntelliJ Plugin Foundation (1 week)

Goal: Basic plugin with tool window that does what CLI does.

### M2-001: Plugin Scaffold

**Estimate:** 2 hours

**Tasks:**
- [x] Create IntelliJ Platform Plugin project (Gradle)
- [x] Configure plugin.xml
- [x] Minimum IntelliJ version: 2023.3
- [x] Target: IntelliJ IDEA (works in Rider too)

**Structure:**
```
tdd-agent-plugin/
├── build.gradle.kts
├── settings.gradle.kts
└── src/main/
    ├── kotlin/dev/agent/plugin/
    │   ├── TddToolWindowFactory.kt
    │   └── TddPanel.kt
    └── resources/
        └── META-INF/
            └── plugin.xml
```

---

### M2-002: Tool Window Registration

**Estimate:** 1 hour

**Tasks:**
- [x] Register tool window in plugin.xml
- [x] Factory creates panel
- [x] Icon for tool window
- [x] Anchor: right side

```xml
<toolWindow id="TDD Agent" 
            anchor="right" 
            factoryClass="dev.agent.plugin.TddToolWindowFactory"/>
```

**Acceptance:**
- [x] Tool window appears in IDE
- [x] Opens/closes correctly

---

### M2-003: Basic Panel UI

**Estimate:** 3 hours

**Tasks:**
- [x] Input field for BDD step
- [x] Output area (scrollable) for generated code
- [x] "Generate Test" button
- [x] "Generate Implementation" button
- [x] Status label

```
┌─────────────────────────────────┐
│ TDD Agent                       │
├─────────────────────────────────┤
│ Step: [________________] [Gen]  │
├─────────────────────────────────┤
│ @Test                           │
│ fun User_with_expired...() {    │
│   ...                           │
│ }                               │
│                                 │
│                                 │
├─────────────────────────────────┤
│ [Generate Impl]                 │
├─────────────────────────────────┤
│ Status: Ready                   │
└─────────────────────────────────┘
```

**Acceptance:**
- [x] Can enter text
- [x] Buttons trigger actions
- [x] Code displays with monospace font

---

### M2-004: Wire Up OpenCode

**Estimate:** 2 hours

**Tasks:**
- [x] Bundle OpenCodeAdapter in plugin
- [x] Call from button click (background thread)
- [x] Display result in output area
- [x] Handle errors gracefully

**Acceptance:**
- [x] Generate Test button calls LLM
- [x] Result appears in output area
- [x] UI doesn't freeze

---

### M2-005: Syntax Highlighting in Output

**Estimate:** 2 hours

**Tasks:**
- [x] Use EditorTextField instead of JTextArea
- [x] Configure for Kotlin syntax
- [x] Read-only mode

**Acceptance:**
- [x] Generated code is syntax highlighted
- [x] Looks native to IDE

---

### M2-006: Copy to Clipboard

**Estimate:** 30 min

**Tasks:**
- [x] "Copy" button next to output
- [x] Copies generated code to clipboard

**Acceptance:**
- [x] One click copy
- [x] Works on all platforms

---

## M3: Auto-Insert & Auto-Run (1 week)

Goal: No more copy-paste. Plugin inserts code and runs tests.

### M3-001: Detect Test File

**Estimate:** 3 hours

**Tasks:**
- [x] Given open editor, find corresponding test file
- [x] Convention: `Foo.kt` → `FooTest.kt` or `FooSpec.kt`
- [x] Search in test source roots
- [x] Create if not exists (prompt user)

```kotlin
fun findTestFile(productionFile: PsiFile): PsiFile?
fun createTestFile(productionFile: PsiFile): PsiFile
```

**Acceptance:**
- [x] Finds existing test files
- [x] Handles Kotest naming conventions

---

### M3-002: Find Insertion Point

**Estimate:** 3 hours

**Tasks:**
- [x] Parse test file PSI
- [x] Find BehaviorSpec class body
- [x] Identify last `given` block
- [x] Return insertion position

```kotlin
fun findInsertionPoint(testFile: KtFile): PsiElement?
```

**Acceptance:**
- [x] Correctly identifies Kotest BehaviorSpec
- [x] Returns valid insertion point

---

### M3-003: Insert Test Code

**Estimate:** 3 hours

**Tasks:**
- [x] Insert generated code at insertion point
- [x] Use WriteCommandAction for undo support
- [x] Auto-format inserted code
- [x] Navigate to inserted code

```kotlin
fun insertTest(testFile: KtFile, code: String)
```

**Acceptance:**
- [x] Code inserted in correct location
- [x] Properly formatted
- [x] Undo works
- [x] Cursor moves to new code

---

### M3-004: Run Tests

**Estimate:** 3 hours

**Tasks:**
- [x] Find or create run configuration for test file
- [x] Execute tests programmatically
- [x] Capture results

```kotlin
fun runTests(testFile: PsiFile): TestResult
```

**Acceptance:**
- [x] Tests run in IDE test runner
- [x] Results captured (pass/fail count)

---

### M3-005: Verify Red/Green

**Estimate:** 2 hours

**Tasks:**
- [x] After insert test: run tests, check NEW test failed
- [x] After insert impl: run tests, check ALL tests pass
- [x] Show status in panel

```kotlin
sealed class TddStatus {
    object Red : TddStatus()      // New test failed (good)
    object Green : TddStatus()    // All pass (good)
    object BadRed : TddStatus()   // Other tests failed (bad)
    object BadGreen : TddStatus() // New test passed (bad - test is wrong)
}
```

**Acceptance:**
- [x] Correctly identifies TDD state
- [x] Clear feedback to user

---

### M3-006: "Insert & Run" Button

**Estimate:** 2 hours

**Tasks:**
- [x] Single button replaces manual workflow
- [x] Insert code → Run tests → Show status
- [x] Disable during execution

**Acceptance:**
- [x] One click to insert and verify
- [x] Progress indication
- [x] Clear pass/fail feedback

---

### M3-007: Implementation Insertion

**Estimate:** 3 hours

**Tasks:**
- [x] Find production file from test file
- [x] Find class/object to modify
- [x] Insert new method or modify existing
- [x] Auto-format

```kotlin
fun insertImplementation(productionFile: KtFile, code: String)
```

**Acceptance:**
- [x] Implementation inserted in production code
- [x] Correct location (inside class)
- [x] Formatted properly

---

### M3-008: Code quality cleanup ✅ COMPLETE

**Estimate:** 3 hours

**Tasks:**
- [x] Add unit tests for PSI helpers (TestFileLocator/TestInsertionLocator/ProductionFileLocator)
- [x] Guard UI/PSI threading (avoid invokeAndWait on EDT)
- [x] Add Kotlin import optimization after insert
- [x] Improve error reporting for test run results

**Acceptance:**
- [x] PSI helper tests cover common cases
- [x] Insertions are safe on EDT
- [x] Inserted code compiles with correct imports
- [x] Runner failures surface actionable details

---

### M3-009: Fix PSI/Threading race conditions and robustness issues ✅ COMPLETE

**Estimate:** 4 hours

**Priority:** Medium (impacts reliability)

**Issues Addressed:**

1. **PSI tree staleness in `ensureBody()`** ✅
   - After `document.insertString()` and `commitDocument()`, the PSI tree may not be updated immediately
   - Fixed by refetching class from file after document modification
   - **Status:** Resolved with explicit PSI reparse

2. **Missing null checks in `TddPanel.onInsertAndRun()`** ✅
   - Added comprehensive null checks for orchestrator and result
   - Proper error messages for each failure case
   - **Status:** Resolved with defensive programming

3. **No timeout on Gradle test execution** ✅
   - Added configurable 60-second timeout with kotlinx.coroutines.withTimeoutOrNull
   - Process cleanup on timeout or cancellation
   - Clear timeout error message to user
   - **Status:** Resolved with timeout wrapper and process destruction

4. **Simplistic test file naming in `ProductionFileLocator`** ✅
   - Improved logic: try exact match first, then progressively remove suffixes
   - Handles edge cases like classes named `Test` or `Spec`
   - **Status:** Resolved with smarter heuristics

5. **Supports inner/nested test classes** ✅
   - Gradle `--tests` parameter supports `$` separator for inner classes
   - Documented FQN format support
   - **Status:** Resolved (Gradle already handles this correctly)

**Tasks Completed:**
- [x] Fix `ensureBody()` PSI staleness with explicit reparse
- [x] Add null safety to `onInsertAndRun()` result handling
- [x] Implement 60s timeout on Gradle execution with user feedback
- [x] Improve test file naming heuristics to handle edge cases
- [x] Document Gradle inner class support

**Acceptance:**
- [x] No stale PSI references after insertion
- [x] UI doesn't hang on long test runs
- [x] Graceful handling of edge-case class names
- [x] Inner test classes execute correctly
- [x] Clear timeout feedback to user

---

## M4: BDD Scenario Phase (1 week)

Goal: Start with feature description, generate scenarios, then TDD.

### M4-001: Feature Input UI

**Estimate:** 2 hours

**Tasks:**
- [x] Multi-line input for feature description
- [x] "Generate Scenarios" button
- [x] Switches panel to scenario mode

**Acceptance:**
- [x] Can enter feature description
- [x] Triggers generation

---

### M4-002: Scenario Generation Prompt

**Estimate:** 2 hours

**Tasks:**
- [x] Prompt template for Gherkin scenarios
- [x] Returns structured scenarios (not just text)

```kotlin
data class Scenario(
    val name: String,
    val steps: List<Step>
)

data class Step(
    val type: StepType,  // GIVEN, WHEN, THEN, AND
    val text: String
)
```

**Acceptance:**
- [x] Generates valid BDD scenarios
- [x] Parses into structured format

---

### M4-003: Scenario Display

**Estimate:** 2 hours

**Tasks:**
- [x] Display scenarios as cards/list
- [x] Checkboxes or approval buttons
- [x] Edit capability (inline or dialog)

**Acceptance:**
- [x] Scenarios clearly displayed
- [x] Can approve/reject/edit each

---

### M4-004: Scenario Approval

**Estimate:** 1 hour

**Tasks:**
- [x] "Approve" locks scenarios
- [x] Transitions to TDD mode
- [x] First step pre-populated

**Acceptance:**
- [x] Clear transition from planning to doing
- [x] Steps queue ready

---

### M4-005: Step Queue

**Estimate:** 2 hours

**Tasks:**
- [x] Show all steps as checklist
- [x] Current step highlighted
- [x] Progress indication
- [x] Auto-advance after green

```
✓ Given a registered user
✓ When they request password reset
● Then they receive an email      ← current
○ And the link expires in 24h
```

**Acceptance:**
- [x] Visual progress through steps
- [x] Clear what's done/current/remaining

---

### M4-006: Auto-Advance

**Estimate:** 1 hour

**Tasks:**
- [x] After green, prompt for next step
- [x] Or auto-advance with confirmation
- [x] "Done" when all steps complete

**Acceptance:**
- [x] Smooth flow through steps
- [x] Clear completion state

---

## M5: Coverage Integration (1 week)

Goal: Coverage check after each green, detect hallucinated code.

### M5-001: Kover Integration

**Estimate:** 3 hours

**Tasks:**
- [x] Detect Kover in project
- [x] Run coverage via Gradle task
- [x] Parse Kover report (XML)

```kotlin
fun runCoverage(): CoverageReport
fun parseCoverageReport(reportFile: File): CoverageReport

data class CoverageReport(
    val percentage: Double,
    val coveredLines: Map<String, Set<Int>>,
    val uncoveredLines: Map<String, Set<Int>>
)
```

**Acceptance:**
- [x] Coverage runs successfully
- [x] Report parsed correctly

---

### M5-002: Coverage Baseline

**Estimate:** 2 hours

**Tasks:**
- [x] Store coverage before implementation
- [x] Compare after implementation
- [x] Identify NEW uncovered lines

```kotlin
fun diffCoverage(before: CoverageReport, after: CoverageReport): List<UncoveredCode>

data class UncoveredCode(
    val file: String,
    val lines: IntRange,
    val code: String
)
```

**Acceptance:**
- [x] Correctly identifies new uncovered lines
- [x] Ignores pre-existing uncovered code

---

### M5-003: Coverage Analysis Prompt

**Estimate:** 2 hours

**Tasks:**
- [x] Prompt template for classifying uncovered code
- [x] Returns: EDGE_CASE, HALLUCINATED, or DEAD_CODE

```kotlin
enum class CoverageClassification {
    EDGE_CASE,    // Queue for later
    HALLUCINATED, // Remove
    DEAD_CODE     // Remove
}
```

**Acceptance:**
- [x] Reasonable classifications
- [x] Clear reasoning

---

### M5-004: Coverage UI

**Estimate:** 2 hours

**Tasks:**
- [x] Show coverage percentage in panel
- [x] Highlight uncovered code findings
- [x] Action buttons: "Queue Edge Case" / "Remove Code"

**Acceptance:**
- [x] Clear coverage feedback
- [x] Actionable findings

---

### M5-005: Edge Case Queue

**Estimate:** 2 hours

**Tasks:**
- [x] Store queued edge cases
- [x] Display as pending items
- [x] Process after main scenarios done

**Acceptance:**
- [x] Edge cases tracked
- [x] Can address later in flow

---

## M6: Polish & Release (1 week)

### M6-001: Settings/Configuration

**Estimate:** 3 hours

**Tasks:**
- [x] Settings page in IDE preferences
- [x] OpenCode model selection
- [x] Timeout configuration
- [x] Test framework selection (Kotest/JUnit)

**Acceptance:**
- [x] Settings persist
- [x] Applied correctly

---

### M6-002: Progress Indication

**Estimate:** 2 hours

**Tasks:**
- [x] Loading spinner during LLM calls
- [x] Progress bar for multi-step operations
- [x] Cancel button for long operations

**Acceptance:**
- [x] Clear feedback during waits
- [x] Can cancel stuck operations

---

### M6-003: Error Handling

**Estimate:** 3 hours

**Tasks:**
- [x] Graceful handling of LLM failures
- [x] Retry logic with backoff
- [x] Clear error messages
- [x] Recovery suggestions

**Acceptance:**
- [x] No crashes on errors
- [x] User knows what went wrong

---

### M6-004: Keyboard Shortcuts

**Estimate:** 2 hours

**Tasks:**
- [x] Register actions with shortcuts
- [x] Ctrl+Shift+T: Generate test
- [x] Ctrl+Shift+I: Generate implementation
- [x] Ctrl+Shift+R: Run tests

**Acceptance:**
- [x] Shortcuts work
- [x] Configurable in keymap

---

### M6-005: Plugin Icon & Branding

**Estimate:** 1 hour

**Tasks:**
- [x] Tool window icon (13x13)
- [x] Plugin marketplace icon (40x40)
- [x] Consistent visual style

**Acceptance:**
- [x] Looks professional
- [x] Recognizable

---

### M6-006: Plugin Description & Docs

**Estimate:** 2 hours

**Tasks:**
- [x] Plugin description for marketplace
- [x] Screenshots
- [x] Basic README
- [x] Changelog

**Acceptance:**
- [x] Ready for marketplace submission

---

### M6-007: Marketplace Submission

**Estimate:** 2 hours

**Tasks:**
- [x] Sign plugin
- [x] Submit to JetBrains Marketplace
- [x] Respond to review feedback

**Acceptance:**
- [x] Plugin published and installable

---

### M6-008: IDE test runner integration

**Estimate:** 3 hours

**Tasks:**
- [x] Determine which IntelliJ platform edition/bundled plugins expose JUnit/Execution UI APIs
- [x] Decide whether to require Java/Gradle plugins (compatibility trade-offs)
- [x] Implement IDE-native run configuration execution (fallback to Gradle CLI if missing)

**Acceptance:**
- [x] Tests run via IDE runner when available
- [x] Clear fallback path when runner APIs are unavailable

---

### M6-009: Document test runner compatibility

**Estimate:** 1 hour

**Tasks:**
- [x] Document which IDEs/editions support IDE test runner integration
- [x] Note fallback behavior and limitations

**Acceptance:**
- [x] Users understand runner behavior and requirements

---

## Deferred (Not in V1)

| Feature | Why Deferred |
|---------|--------------|
| Mutation testing | Nice to have, not essential for TDD loop |
| Code review phase | Can use IDE inspections manually |
| PR generation | Can do manually, low friction |
| Multi-language support | Start with Kotlin only |
| State persistence | Sessions are short, restart is fine |
| Research phase | Manual codebase exploration works |
| Web UI | Plugin is better for target audience |
| Refactoring commands | IDE refactoring works fine manually |

These become tickets when you feel the pain.

---

## Ticket Summary

| Milestone | Tickets | Estimate |
|-----------|---------|----------|
| M1: CLI | 5 | 3 days |
| M2: Plugin Foundation | 6 | 1 week |
| M3: Auto-Insert & Run | 8 | 1 week |
| M4: BDD Scenarios | 6 | 1 week |
| M5: Coverage | 5 | 1 week |
| M6: Polish | 7 | 1 week |
| **Total** | **37** | **~5 weeks** |

---

## Suggested Order

### Week 1
- M1-001 through M1-005 (CLI working)
- M2-001, M2-002 (Plugin scaffold)

### Week 2
- M2-003 through M2-006 (Basic plugin UI)
- Start M3-001 (Test file detection)

### Week 3
- M3-002 through M3-007 (Full auto-insert)

### Week 4
- M4-001 through M4-006 (BDD scenarios)

### Week 5
- M5-001 through M5-005 (Coverage)
- Start M6 polish tasks

### Week 6
- M6-001 through M6-007 (Polish & release)

---

## Definition of Done

### For Each Ticket
- [x] Code complete
- [x] Works in your daily workflow
- [x] No obvious bugs

### For Each Milestone
- [x] All tickets complete
- [x] Used on real feature (dogfooding)
- [x] Pain points noted for backlog

### For V1 Release
- [x] M1-M3 complete (minimum usable)
- [x] Works reliably on your projects
- [x] Someone else can install and use it


### FX-001: IDE test runner integration

**Estimate:** 3 hours

**Tasks:**
- [x] Determine which IntelliJ platform edition/bundled plugins expose JUnit/Execution UI APIs
- [x] Decide whether to require Java/Gradle plugins (compatibility trade-offs)
- [x] Implement IDE-native run configuration execution (fallback to Gradle CLI if missing)

**Acceptance:**
- [x] Tests run via IDE runner when available
- [x] Clear fallback path when runner APIs are unavailable

---

### FX-002: Document test runner compatibility

**Estimate:** 1 hour

**Tasks:**
- [x] Document which IDEs/editions support IDE test runner integration
- [x] Note fallback behavior and limitations

**Acceptance:**
- [x] Users understand runner behavior and requirements

