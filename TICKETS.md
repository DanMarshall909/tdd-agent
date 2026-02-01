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
- [ ] Given open editor, find corresponding test file
- [ ] Convention: `Foo.kt` → `FooTest.kt` or `FooSpec.kt`
- [ ] Search in test source roots
- [ ] Create if not exists (prompt user)

```kotlin
fun findTestFile(productionFile: PsiFile): PsiFile?
fun createTestFile(productionFile: PsiFile): PsiFile
```

**Acceptance:**
- [ ] Finds existing test files
- [ ] Handles Kotest naming conventions

---

### M3-002: Find Insertion Point

**Estimate:** 3 hours

**Tasks:**
- [ ] Parse test file PSI
- [ ] Find BehaviorSpec class body
- [ ] Identify last `given` block
- [ ] Return insertion position

```kotlin
fun findInsertionPoint(testFile: KtFile): PsiElement?
```

**Acceptance:**
- [ ] Correctly identifies Kotest BehaviorSpec
- [ ] Returns valid insertion point

---

### M3-003: Insert Test Code

**Estimate:** 3 hours

**Tasks:**
- [ ] Insert generated code at insertion point
- [ ] Use WriteCommandAction for undo support
- [ ] Auto-format inserted code
- [ ] Navigate to inserted code

```kotlin
fun insertTest(testFile: KtFile, code: String)
```

**Acceptance:**
- [ ] Code inserted in correct location
- [ ] Properly formatted
- [ ] Undo works
- [ ] Cursor moves to new code

---

### M3-004: Run Tests

**Estimate:** 3 hours

**Tasks:**
- [ ] Find or create run configuration for test file
- [ ] Execute tests programmatically
- [ ] Capture results

```kotlin
fun runTests(testFile: PsiFile): TestResult
```

**Acceptance:**
- [ ] Tests run in IDE test runner
- [ ] Results captured (pass/fail count)

---

### M3-005: Verify Red/Green

**Estimate:** 2 hours

**Tasks:**
- [ ] After insert test: run tests, check NEW test failed
- [ ] After insert impl: run tests, check ALL tests pass
- [ ] Show status in panel

```kotlin
sealed class TddStatus {
    object Red : TddStatus()      // New test failed (good)
    object Green : TddStatus()    // All pass (good)
    object BadRed : TddStatus()   // Other tests failed (bad)
    object BadGreen : TddStatus() // New test passed (bad - test is wrong)
}
```

**Acceptance:**
- [ ] Correctly identifies TDD state
- [ ] Clear feedback to user

---

### M3-006: "Insert & Run" Button

**Estimate:** 2 hours

**Tasks:**
- [ ] Single button replaces manual workflow
- [ ] Insert code → Run tests → Show status
- [ ] Disable during execution

**Acceptance:**
- [ ] One click to insert and verify
- [ ] Progress indication
- [ ] Clear pass/fail feedback

---

### M3-007: Implementation Insertion

**Estimate:** 3 hours

**Tasks:**
- [ ] Find production file from test file
- [ ] Find class/object to modify
- [ ] Insert new method or modify existing
- [ ] Auto-format

```kotlin
fun insertImplementation(productionFile: KtFile, code: String)
```

**Acceptance:**
- [ ] Implementation inserted in production code
- [ ] Correct location (inside class)
- [ ] Formatted properly

---

## M4: BDD Scenario Phase (1 week)

Goal: Start with feature description, generate scenarios, then TDD.

### M4-001: Feature Input UI

**Estimate:** 2 hours

**Tasks:**
- [ ] Multi-line input for feature description
- [ ] "Generate Scenarios" button
- [ ] Switches panel to scenario mode

**Acceptance:**
- [ ] Can enter feature description
- [ ] Triggers generation

---

### M4-002: Scenario Generation Prompt

**Estimate:** 2 hours

**Tasks:**
- [ ] Prompt template for Gherkin scenarios
- [ ] Returns structured scenarios (not just text)

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
- [ ] Generates valid BDD scenarios
- [ ] Parses into structured format

---

### M4-003: Scenario Display

**Estimate:** 2 hours

**Tasks:**
- [ ] Display scenarios as cards/list
- [ ] Checkboxes or approval buttons
- [ ] Edit capability (inline or dialog)

**Acceptance:**
- [ ] Scenarios clearly displayed
- [ ] Can approve/reject/edit each

---

### M4-004: Scenario Approval

**Estimate:** 1 hour

**Tasks:**
- [ ] "Approve" locks scenarios
- [ ] Transitions to TDD mode
- [ ] First step pre-populated

**Acceptance:**
- [ ] Clear transition from planning to doing
- [ ] Steps queue ready

---

### M4-005: Step Queue

**Estimate:** 2 hours

**Tasks:**
- [ ] Show all steps as checklist
- [ ] Current step highlighted
- [ ] Progress indication
- [ ] Auto-advance after green

```
✓ Given a registered user
✓ When they request password reset
● Then they receive an email      ← current
○ And the link expires in 24h
```

**Acceptance:**
- [ ] Visual progress through steps
- [ ] Clear what's done/current/remaining

---

### M4-006: Auto-Advance

**Estimate:** 1 hour

**Tasks:**
- [ ] After green, prompt for next step
- [ ] Or auto-advance with confirmation
- [ ] "Done" when all steps complete

**Acceptance:**
- [ ] Smooth flow through steps
- [ ] Clear completion state

---

## M5: Coverage Integration (1 week)

Goal: Coverage check after each green, detect hallucinated code.

### M5-001: Kover Integration

**Estimate:** 3 hours

**Tasks:**
- [ ] Detect Kover in project
- [ ] Run coverage via Gradle task
- [ ] Parse Kover report (XML)

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
- [ ] Coverage runs successfully
- [ ] Report parsed correctly

---

### M5-002: Coverage Baseline

**Estimate:** 2 hours

**Tasks:**
- [ ] Store coverage before implementation
- [ ] Compare after implementation
- [ ] Identify NEW uncovered lines

```kotlin
fun diffCoverage(before: CoverageReport, after: CoverageReport): List<UncoveredCode>

data class UncoveredCode(
    val file: String,
    val lines: IntRange,
    val code: String
)
```

**Acceptance:**
- [ ] Correctly identifies new uncovered lines
- [ ] Ignores pre-existing uncovered code

---

### M5-003: Coverage Analysis Prompt

**Estimate:** 2 hours

**Tasks:**
- [ ] Prompt template for classifying uncovered code
- [ ] Returns: EDGE_CASE, HALLUCINATED, or DEAD_CODE

```kotlin
enum class CoverageClassification {
    EDGE_CASE,    // Queue for later
    HALLUCINATED, // Remove
    DEAD_CODE     // Remove
}
```

**Acceptance:**
- [ ] Reasonable classifications
- [ ] Clear reasoning

---

### M5-004: Coverage UI

**Estimate:** 2 hours

**Tasks:**
- [ ] Show coverage percentage in panel
- [ ] Highlight uncovered code findings
- [ ] Action buttons: "Queue Edge Case" / "Remove Code"

**Acceptance:**
- [ ] Clear coverage feedback
- [ ] Actionable findings

---

### M5-005: Edge Case Queue

**Estimate:** 2 hours

**Tasks:**
- [ ] Store queued edge cases
- [ ] Display as pending items
- [ ] Process after main scenarios done

**Acceptance:**
- [ ] Edge cases tracked
- [ ] Can address later in flow

---

## M6: Polish & Release (1 week)

### M6-001: Settings/Configuration

**Estimate:** 3 hours

**Tasks:**
- [ ] Settings page in IDE preferences
- [ ] OpenCode model selection
- [ ] Timeout configuration
- [ ] Test framework selection (Kotest/JUnit)

**Acceptance:**
- [ ] Settings persist
- [ ] Applied correctly

---

### M6-002: Progress Indication

**Estimate:** 2 hours

**Tasks:**
- [ ] Loading spinner during LLM calls
- [ ] Progress bar for multi-step operations
- [ ] Cancel button for long operations

**Acceptance:**
- [ ] Clear feedback during waits
- [ ] Can cancel stuck operations

---

### M6-003: Error Handling

**Estimate:** 3 hours

**Tasks:**
- [ ] Graceful handling of LLM failures
- [ ] Retry logic with backoff
- [ ] Clear error messages
- [ ] Recovery suggestions

**Acceptance:**
- [ ] No crashes on errors
- [ ] User knows what went wrong

---

### M6-004: Keyboard Shortcuts

**Estimate:** 2 hours

**Tasks:**
- [ ] Register actions with shortcuts
- [ ] Ctrl+Shift+T: Generate test
- [ ] Ctrl+Shift+I: Generate implementation
- [ ] Ctrl+Shift+R: Run tests

**Acceptance:**
- [ ] Shortcuts work
- [ ] Configurable in keymap

---

### M6-005: Plugin Icon & Branding

**Estimate:** 1 hour

**Tasks:**
- [ ] Tool window icon (13x13)
- [ ] Plugin marketplace icon (40x40)
- [ ] Consistent visual style

**Acceptance:**
- [ ] Looks professional
- [ ] Recognizable

---

### M6-006: Plugin Description & Docs

**Estimate:** 2 hours

**Tasks:**
- [ ] Plugin description for marketplace
- [ ] Screenshots
- [ ] Basic README
- [ ] Changelog

**Acceptance:**
- [ ] Ready for marketplace submission

---

### M6-007: Marketplace Submission

**Estimate:** 2 hours

**Tasks:**
- [ ] Sign plugin
- [ ] Submit to JetBrains Marketplace
- [ ] Respond to review feedback

**Acceptance:**
- [ ] Plugin published and installable

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
| M3: Auto-Insert & Run | 7 | 1 week |
| M4: BDD Scenarios | 6 | 1 week |
| M5: Coverage | 5 | 1 week |
| M6: Polish | 7 | 1 week |
| **Total** | **36** | **~5 weeks** |

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
- [ ] Code complete
- [ ] Works in your daily workflow
- [ ] No obvious bugs

### For Each Milestone
- [ ] All tickets complete
- [ ] Used on real feature (dogfooding)
- [ ] Pain points noted for backlog

### For V1 Release
- [ ] M1-M3 complete (minimum usable)
- [ ] Works reliably on your projects
- [ ] Someone else can install and use it
