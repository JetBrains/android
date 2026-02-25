# Studio Agent Test Guidelines

Follow these guidelines to ensure agent evaluations are stable, meaningful, and measurable.

## Project State & Setup

- **In-Memory Projects**: Prefer `AndroidProjectRule.inMemory()` for speed and isolation. Most evaluations that don't depend on the Gradle build system or complex Android layouts should use this method.
- **Prebuilt Projects**: Only use a real disk project (via `AndroidGradleProjectRule` and a prebuilt `src.zip` inside the `prebuilts/studio/evalprojects` directory) if the task specifically requires disk I/O, Gradle sync behavior, Layoutlib rendering, or complex multi-module hierarchies.

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

- **Standard Metrics**: Always call `logDiffMetrics(diff)`. It automatically captures file counts, line changes, and execution time.
- **Custom Metrics**: Log specific outcomes, like `number_of_tests_fixed` or `documentation_added_bool`, to track progress over multiple model versions.

## Multi-modal Eval

- If your agent produces visual output (e.g., a Compose Preview), capture a screenshot and pass the bytes to the judge using `agentEvalRule.fixture.judge { blob(...) }`.