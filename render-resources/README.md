
### Render resources

A module/library that contains logic responsible for managing and delivering resources essential for Android on-host rendering. While the
logic might not be used exclusively to support rendering, it is important to keep it in a separate module because it is predominantly
Android Studio agnostic. It is supposed to be used both from Android Studio (for the previews) and outside, for CLI execution or as a part
of a Gradle task. The only exception is dependency on `intellij-core` library that contains a subset of studio (Intellij Idea) specific API.

Therefore, the module belongs to both `tools/adt/idea` and `tools/base` projects, having 2 separate targets. For more information on the
examples of code can be share between `tools/adt/idea` and `tools/base` see `android.sdktools.base.lint.cli` and `intellij.android.lint`.
