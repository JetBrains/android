# Studio Agent Evaluation Developer Guide

This guide details the API and patterns for building new evaluations using the Studio Agent framework.

## 1. Project Initialization Patterns

Evaluating agents requires a host IDE project. You can arrange this in two distinct ways:

### In-Memory Projects (Recommended)
Fast, lightweight, and isolated. Use regular JUnit rules if the task does not depend on a full Gradle build or deep Android structure.

```kotlin
@get:Rule val androidProjectRule = AndroidProjectRule.inMemory()
@get:Rule val agentEvalRule = AgentEvalRule(evalId = "my_eval") { androidProjectRule.project }
```
Then manually add files via `androidProjectRule.fixture.addFileToProject(...)`.

### Real World Projects
For tasks involving Gradle Sync, Rendering, or complex Android multi-module structures, use a real world project.
#### Git hosted Projects
1. **Location**: The project must be hosted on git and available publicly.
2. **Rule Configuration**: Use `GitProjectRule` combined with `TemporaryFolder` to download and setup the project and `AgentEvalRule` to run the evaluation. Use a `RuleChain` to ensure the project is setup before the `AgentEvalRule`.

Here is an example:
```kotlin
val temporaryFolder = TemporaryFolder()
  val gitRepository = GitRepository(
    url = "some git url",
    refSha = "some ref sha",
  )
  val gitProjectRule = GitProjectRule(
    taskDir = { temporaryFolder.newFolder().resolve("my_eval").toPath() },
    gitRepository = { gitRepository },
  )
  val agentEvalRule = AgentEvalRule(evalId = "my_eval") { gitProjectRule.project }
  val geminiKeyRule = GeminiApiKeyRule()

  @get:Rule
  val chain: RuleChain =
    RuleChain.outerRule(temporaryFolder).around(projectRule).around(agentEvalRule).around(geminiKeyRule)
```

#### Prebuilt Zipped Projects
1. **Format**: The project should be completely self-contained, zipped, and named `src.zip`.
2. **Location**: Add the `src.zip` folder structure to the `prebuilts/studio/evalprojects/` tree in `studio-main` (e.g., `prebuilts/studio/evalprojects/ui-tools/myproject/src.zip`).
3. **Rule Configuration**: Use `AndroidGradleProjectRule` wrapped by a custom test rule (like `UiToolsEvalRule`) that unzips `src.zip` before starting the eval.

```kotlin
// Example using a composite rule like UiToolsEvalRule that
// wraps AndroidGradleProjectRule and AgentEvalRule
@get:Rule val evalRule = UiToolsEvalRule()
val project get() = evalRule.project
```

## 2. Core API Reference

### AgentEvalRule
Orchestrates results and fails the test if any `ERROR` or `FATAL` assertions are logged.

### AgentFixture
The main interface for evaluation logic, accessed via `agentEvalRule.fixture`.
- `configureAgent(project, model, tools)`: Sets up the agent's capabilities.
- `run(prompt)`: Executes the task and returns a `DiffResult`.
- `assertTrue/assertFalse(severity, message, condition)`: Logs assertions.
- `logMetric(name, value)`: Records quantitative data for the evaluation run.
- `score(dimension, value)`: Scores the eval based on a specific dimension. The score value is between 0.0 and 1.0. The eval should not aim at scoring 1.0 from the start as the value should increase as the agent improves.

### LlmJudge
Provides qualitative analysis of the agent's output.
- `judge(prompt)`: Evaluates the current `DiffResult`.
- `judgeGoldenDiff(prompt, diff, targetDiff)`: Validates against an expert-provided diff.

## 3. Defining Build Targets

Use existing macros in your `BUILD.bazel` file to ensure correct infrastructure setup and dependency paths.

### Example: Prebuilt Project Macro
```python
load(":my_macros.bzl", "my_feature_eval")

my_feature_eval(
    name = "new_scenario_target",
    project_path = "prebuilts/studio/evalprojects/ui-tools/myproject",
    scenarios = ["my_new_scenario"],
)
```

If you are using a prebuilt zipped project, ensure the `java_test` target produced by the macro includes a `data` dependency on the `prebuilts` directory containing your `src.zip`.