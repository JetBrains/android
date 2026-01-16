# Compose Designer Agent Guide

This document describes how an AI agent can interact specifically with the Compose Designer. For common preview interaction patterns, see the [Preview Designer Agent Guide](../preview-designer/AGENTS.md).

## Key Entry Points

### 1. Fast Preview Availability
- **Fast Preview**: Check `FastPreviewManager.getInstance(project).isAvailable`. If it's disabled (e.g., due to an unsupported Kotlin version), refreshes will default to a full Gradle build, which is much slower.

### 3. Rendering Tools
- **ComposeRenderer**: Uses `Layoutlib` to render Composables in isolation.
- **`RenderComposePreviewTool`**: The standard tool used by AI agents to grab the current visual state of a specific `@Preview`.

### 3. Advanced Interaction & Modes
- **`ComposePreviewFlowManager`**: Controls the flow of preview instances. Use this to query the current set of rendered elements.
- **UI Check Mode**: Controlled via `UiCheckModeFilter`. To programmatically enter "UI Check" (visual linting) mode for a specific instance, agents can interact with the `uiCheckFilterFlow` in `ComposePreviewRepresentation`.

## AI Agent Specialized Hooks
Specific hooks for AI agents are located in `tools/vendor/google/ml/aiplugin/android/src/main/kotlin/com/android/studio/ml/designer/compose/preview/agents/`.
- **`MatchUiToTargetImageAgent`**: Uses Vision models to compare a rendered preview against a mockup and suggest code fixes.
- **`TransformPreviewAgent`**: Orchestrates deep memory edits and verify-by-render loops.

## Examples and Testing

### Non-Obvious Test Patterns
- **Golden Image Comparison**: `com.android.tools.idea.compose.preview.ComposePreviewRepresentationTest` demonstrates how to verify rendering output against expected images.
- **Visual Lint testing**: `com.android.tools.idea.compose.preview.ComposeVisualLintIssueProviderTest` shows how to test the automated detection of accessibility issues.

### How to run tests
Use Bazel for the compose designer tests:
```bash
bazel test //tools/adt/idea/compose-designer:intellij.android.compose-designer.tests
```

## Related Documentation
- [Compose Designer Architecture](docs/architecture.md)
