# Preview Designer Agent Guide

This document describes how an AI agent can interact with common Preview Designer features.

## Key Entry Points

### 1. Manual Refresh and Wait
If you modify code and need to ensure the preview is updated before proceeding:
- **`CommonPreviewRepresentation`**: Use `requestRefresh()` on the active representation.
- **`PreviewRepresentationManager`**: Use `getInstance(file).currentRepresentation` to find the active lifecycle manager.
- **Wait Pattern**: To wait for a refresh to complete in an agent context, use `CompletableDeferred`.

### 2. Activation State
Previews might be in a "Background" or "Paused" state. Use `PreviewLifecycleManager` to ensure the representation is active before requesting a render.

## Examples and Testing

### How to run tests
Use Bazel to run common preview tests:
```bash
bazel test //tools/adt/idea/preview-designer:intellij.android.preview-designer.tests
```

## Related Documentation
- [Preview Designer Architecture](docs/architecture.md)
