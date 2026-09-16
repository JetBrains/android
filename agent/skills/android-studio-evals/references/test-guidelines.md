# Studio Agent Test Guidelines

Follow these guidelines to ensure agent evaluations are stable, meaningful, and measurable.

## Project State & Setup

- **In-Memory Projects**: Prefer `AndroidProjectRule.inMemory()` for speed and isolation. Most evaluations that don't depend on the Gradle build system or complex Android layouts should use this method.
- **Real World Projects**: Only use a real disk project if the task specifically requires disk I/O, Gradle sync behavior, Layoutlib rendering, or complex multi-module hierarchies.
  - **Git hosted Projects**: Use `GitProjectRule` for projects that are hosted on git and available publicly.
  - **Prebuilt Projects**: Use `AndroidGradleProjectRule` and a prebuilt `src.zip` inside the `prebuilts/studio/evalprojects` directory for projects that are not available publicly.

## Test Stability

- **Timeouts**: Agent tasks can be long-running. Wrap your `run` calls in `withTimeout` (e.g., `20.minutes`) to handle hangs gracefully.
- **Authentication**: Use `GeminiApiKeyRule` to ensure the test has access to required LLM endpoints. Never hardcode keys.

## Effective Assertions

- **Severity Levels**:
    - `FATAL/ERROR`: Use for critical failures (e.g., code doesn't compile after agent fix).
    - `WARNING`: Use for sub-optimal results that are still functional.
    - `INFO`: Use for progress markers or minor observations.
- **LLM Judge**: Provide sufficient context to the judge. Instead of "Did it work?", use "Verify the agent fixed the syntax error at line 5 and didn't remove the existing documentation."

## Metric Logging

- **Custom Metrics**: Log specific outcomes, like `number_of_tests_fixed` or `documentation_added_bool`, to track progress over multiple model versions.
- **Standard Metrics**: Standard metrics are automatically captured by calling `logDiffMetrics(diff)`. This is already performed by `AgentEvalRule` so you don't need to call it manually.

## Score Logging

An eval can be qualified by using scores against different dimensions. For example, an eval could be scored as 0.5 for correctness and 1.0 for style. The score should be between 0.0 and 1.0 and should increase as the agent improves. The score should be logged using `agentEvalRule.fixture.score(dimension, value)`.

## Multi-modal Eval

- If your agent produces visual output (e.g., a Compose Preview), capture a screenshot and pass the bytes to the judge using `agentEvalRule.fixture.judge { blob(...) }`.