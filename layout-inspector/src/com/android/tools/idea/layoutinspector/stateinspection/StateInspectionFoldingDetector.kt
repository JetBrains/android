/*
 * Copyright (C) 2025 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.layoutinspector.stateinspection

import com.android.tools.idea.layoutinspector.LayoutInspectorBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.FoldingModelEx
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.kotlin.utils.indexOfFirst

private const val RECORD_READ_OF = "at androidx.compose.runtime.CompositionImpl.recordReadOf"
private const val SNAPSHOT_READABLE = "at androidx.compose.runtime.snapshots.SnapshotKt.readable"
private const val SNAPSHOT_PACKAGE = "at androidx.compose.runtime.snapshots."
private const val SNAPSHOT_CLASS = "at androidx.compose.runtime.Snapshot"
private const val DYNAMIC_VALUE = "at androidx.compose.runtime.DynamicValueHolder.readValue"
private const val KOTLIN_METHOD = "at kotlin."
private const val RECOMPOSE = "at androidx.compose.runtime.RecomposeScopeImpl.compose"
private const val LAMBDA_INVOKE = "at androidx.compose.runtime.internal.ComposableLambdaImpl.invoke"
private const val VALUE = "value: "

private val RECORD_READ_EXCEPTION_PREFIXES =
  listOf(SNAPSHOT_PACKAGE, SNAPSHOT_CLASS, KOTLIN_METHOD, DYNAMIC_VALUE)

/**
 * Adds line folding to the content of an editor with State Inspection data.
 *
 * Example of a single state read:
 * ```
 * State read value: TextStyle(Unspecified, 16.0sp, W400, Default, 0.5sp, Unspecifi...
 * value: TextStyle {
 *   color: Unspecified,
 *   fontSize: 16.0sp,
 *   fontWeight: W400,
 *   fontFamily: Default,
 *   letterSpacing: 0.5sp,
 *   background: Unspecified,
 *   textDirection: Unspecified,
 *   lineHeight: 24.0sp
 * }
 *
 *     at androidx.compose.runtime.CompositionImpl.recordReadOf(Composition.kt:1015)
 *     at androidx.compose.runtime.Recomposer.readObserverOf$lambda$87(Recomposer.kt:1519)
 *     at androidx.compose.runtime.Recomposer.$r8$lambda$RNpGrwMSqIkMvRQQdpqhpRw2OuI(Unknown Source:0)
 *     at androidx.compose.runtime.Recomposer$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
 *     at androidx.compose.runtime.snapshots.SnapshotKt.readable(Snapshot.kt:2081)
 *     at androidx.compose.runtime.SnapshotMutableStateImpl.getValue(SnapshotState.kt:142)
 *     at androidx.compose.runtime.DynamicValueHolder.readValue(ValueHolders.kt:71)
 *     at androidx.compose.runtime.CompositionLocalMapKt.read(CompositionLocalMap.kt:88)
 *     at androidx.compose.runtime.ComposerImpl.consume(Composer.kt:2473)
 *     at androidx.compose.material3.TextKt.Text--4IGK_g(Text.kt:352)
 *     at com.example.recompositiontest.MainActivityKt.Item(MainActivity.kt:58)
 *     at com.example.recompositiontest.MainActivityKt.Item$lambda$3(Unknown Source:6)
 *     at com.example.recompositiontest.MainActivityKt.$r8$lambda$-WQ9lcw2L62jBkR0JG7MNrSlbR4(Unknown Source:0)
 *     at com.example.recompositiontest.MainActivityKt$$ExternalSyntheticLambda6.invoke(D8$$SyntheticClass:0)
 *     at androidx.compose.runtime.RecomposeScopeImpl.compose(RecomposeScopeImpl.kt:198)
 *     at androidx.compose.runtime.ComposerImpl.recomposeToGroupEnd(Composer.kt:2926)
 *     at androidx.compose.runtime.ComposerImpl.skipCurrentGroup(Composer.kt:3262)
 *     at androidx.compose.runtime.ComposerImpl.doCompose-aFTiNEg(Composer.kt:3893)
 *     at androidx.compose.runtime.ComposerImpl.recompose-aFTiNEg$runtime(Composer.kt:3817)
 *     at androidx.compose.runtime.CompositionImpl.recompose(Composition.kt:1076)
 *     at androidx.compose.runtime.Recomposer.performRecompose(Recomposer.kt:1400)
 *     at androidx.compose.runtime.Recomposer.access$performRecompose(Recomposer.kt:156)
 *     at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.invokeSuspend$lambda$22(Recomposer.kt:635)
 *     at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.$r8$lambda$OqADLCDYmRw1RgNUvn1CR0kX32M(Unknown Source:0)
 *     at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
 *     at androidx.compose.ui.platform.AndroidUiFrameClock$withFrameNanos$2$callback$1.doFrame(AndroidUiFrameClock.android.kt:39)
 *     at androidx.compose.ui.platform.AndroidUiDispatcher.performFrameDispatch(AndroidUiDispatcher.android.kt:108)
 *     at androidx.compose.ui.platform.AndroidUiDispatcher.access$performFrameDispatch(AndroidUiDispatcher.android.kt:41)
 *     at androidx.compose.ui.platform.AndroidUiDispatcher$dispatchCallback$1.doFrame(AndroidUiDispatcher.android.kt:69)
 *     at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1337)
 *     at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1348)
 *     at android.view.Choreographer.doCallbacks(Choreographer.java:952)
 *     at android.view.Choreographer.doFrame(Choreographer.java:878)
 *     at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:1322)
 *     at android.os.Handler.handleCallback(Handler.java:958)
 *     at android.os.Handler.dispatchMessage(Handler.java:99)
 *     at android.os.Looper.loopOnce(Looper.java:205)
 *     at android.os.Looper.loop(Looper.java:294)
 *     at android.app.ActivityThread.main(ActivityThread.java:8177)
 * ```
 *
 * This folding detector will attempt to fold:
 * - the detailed value (since the value expression may be enough to understand the situation)
 * - the start of the exception stacktrace (common to all/most state reads)
 * - the end of the exception stacktrace (usually doesn't hold informative data)
 */
class StateInspectionFoldingDetector(
  private val editor: Editor,
  private val scope: CoroutineScope,
) {
  private val document = editor.document
  private val foldingModel = editor.foldingModel as? FoldingModelEx
  private var lines: List<String> = emptyList()

  fun detectFolding(): Job? {
    val model = foldingModel ?: return null
    return scope.launch(Dispatchers.EDT) {
      writeAction {
        model.runBatchFoldingOperation {
          fetchLines()
          model.clearFoldRegions()
          foldLines()
        }
      }
    }
  }

  private fun foldLines() {
    var lineIndex = 0
    while (lineIndex < lines.size) {
      val line = line(lineIndex)
      when {
        line.startsWith(VALUE) -> lineIndex = foldDetailedValue(lineIndex)
        line.startsWith(RECORD_READ_OF) -> lineIndex = foldStartOfReadException(lineIndex)
        line.startsWith(RECOMPOSE) || line.startsWith(LAMBDA_INVOKE) ->
          lineIndex = foldEndOfException(lineIndex)
        else -> lineIndex++
      }
    }
  }

  /**
   * The detailed value is usually a record or a list of the form:
   * ```
   * value: TextStyle {
   *   color: Unspecified,
   *   fontSize: 16.0sp,
   *   fontWeight: W400,
   *   fontFamily: Default,
   *   letterSpacing: 0.5sp,
   *   background: Unspecified,
   *   textDirection: Unspecified,
   *   lineHeight: 24.0sp
   * }
   *
   * ```
   *
   * or:
   * ```
   * value: List[777] [
   *   [0]: "Data",
   *   [1]: "in",
   *   [2]: "a",
   *   [3]: "string",
   *   [4]: "list",
   *   ...
   * ]
   *
   * ```
   *
   * An empty line will always appear before the start of the stacktrace.
   */
  private fun foldDetailedValue(start: Int): Int {
    // Find the end of the value by searching for the empty line before the stacktrace:
    val next = lines.indexOfFirst(start) { it.isEmpty() }
    if (next > start) {
      val placeholderText = LayoutInspectorBundle.message("layout.inspector.value.folding.hint")
      editor.foldingModel.addFoldRegion(startOffset(start), endOffset(next), placeholderText)?.let {
        it.isExpanded = false
      }
    }
    return next
  }

  /**
   * The start of the stacktrace is folded from patterns like:
   * ```
   *     at androidx.compose.runtime.CompositionImpl.recordReadOf(Composition.kt:1015)
   *     at androidx.compose.runtime.Recomposer.readObserverOf$lambda$87(Recomposer.kt:1519)
   *     at androidx.compose.runtime.Recomposer.$r8$lambda$RNpGrwMSqIkMvRQQdpqhpRw2OuI(Unknown Source:0)
   *     at androidx.compose.runtime.Recomposer$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
   *     at androidx.compose.runtime.snapshots.SnapshotKt.readable(Snapshot.kt:2081)
   *     at androidx.compose.runtime.SnapshotMutableStateImpl.getValue(SnapshotState.kt:142)
   *     at androidx.compose.runtime.DynamicValueHolder.readValue(ValueHolders.kt:71)
   *     at androidx.compose.runtime.CompositionLocalMapKt.read(CompositionLocalMap.kt:88) <-----
   *     at androidx.compose.runtime.ComposerImpl.consume(Composer.kt:2473)
   *     at androidx.compose.material3.TextKt.Text--4IGK_g(Text.kt:352)
   *     at com.example.recompositiontest.MainActivityKt.Item(MainActivity.kt:58)
   *     at com.example.recompositiontest.MainActivityKt.Item$lambda$3(Unknown Source:6)
   *     at com.example.recompositiontest.MainActivityKt.$r8$lambda$-WQ9lcw2L62jBkR0JG7MNrSlbR4(Unknown Source:0)
   *     at com.example.recompositiontest.MainActivityKt$$ExternalSyntheticLambda6.invoke(D8$$SyntheticClass:0)
   *     ...
   *  ```
   *
   * i.e. it always starts with `CompositionImpl.recordReadOf`. We want to fold from this line down
   * to a line that we think has useful information skipping all lines around snapshot handling.
   */
  private fun foldStartOfReadException(start: Int): Int {
    // First skip the `CompositionImpl.recordReadOf` before we enter this function:
    var next = start + 1
    var nextLine = line(next)

    // Skip down to `SnapshotKt.readable` in the stacktrace:
    while (nextLine.isNotEmpty() && !nextLine.startsWith(SNAPSHOT_READABLE)) {
      nextLine = line(++next)
    }

    // Stop now and abandon the fold if we didn't find `SnapshotKt.readable`:
    if (!nextLine.startsWith(SNAPSHOT_READABLE)) {
      return start
    }

    // Skip any snapshot or kotlin runtime frames:
    while (RECORD_READ_EXCEPTION_PREFIXES.any { nextLine.startsWith(it) }) {
      nextLine = line(++next)
    }
    next--

    if (next > start) {
      // Fold the start of the exception here:
      val placeholderText =
        LayoutInspectorBundle.message(
          "layout.inspector.stacktrace.folding.hint",
          (next - start + 1).toString(),
        )
      editor.foldingModel.addFoldRegion(startOffset(start), endOffset(next), placeholderText)?.let {
        it.isExpanded = false
      }
    }
    return next
  }

  /**
   * The end of the stacktrace is folded from patterns like:
   * ```
   *     ... (start of exception omitted)
   *     at androidx.compose.runtime.ComposerImpl.consume(Composer.kt:2473) <--- start
   *     at androidx.compose.material3.TextKt.Text--4IGK_g(Text.kt:352)
   *     at com.example.recompositiontest.MainActivityKt.Item(MainActivity.kt:58)
   *     at com.example.recompositiontest.MainActivityKt.Item$lambda$3(Unknown Source:6)
   *     at com.example.recompositiontest.MainActivityKt.$r8$lambda$-WQ9lcw2L62jBkR0JG7MNrSlbR4(Unknown Source:0)
   *     at com.example.recompositiontest.MainActivityKt$$ExternalSyntheticLambda6.invoke(D8$$SyntheticClass:0)
   *     at androidx.compose.runtime.RecomposeScopeImpl.compose(RecomposeScopeImpl.kt:198)
   *     at androidx.compose.runtime.ComposerImpl.recomposeToGroupEnd(Composer.kt:2926)
   *     at androidx.compose.runtime.ComposerImpl.skipCurrentGroup(Composer.kt:3262)
   *     at androidx.compose.runtime.ComposerImpl.doCompose-aFTiNEg(Composer.kt:3893)
   *     at androidx.compose.runtime.ComposerImpl.recompose-aFTiNEg$runtime(Composer.kt:3817)
   *     at androidx.compose.runtime.CompositionImpl.recompose(Composition.kt:1076)
   *     at androidx.compose.runtime.Recomposer.performRecompose(Recomposer.kt:1400)
   *     at androidx.compose.runtime.Recomposer.access$performRecompose(Recomposer.kt:156)
   *     at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.invokeSuspend$lambda$22(Recomposer.kt:635)
   *     at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2.$r8$lambda$OqADLCDYmRw1RgNUvn1CR0kX32M(Unknown Source:0)
   *     at androidx.compose.runtime.Recomposer$runRecomposeAndApplyChanges$2$$ExternalSyntheticLambda0.invoke(D8$$SyntheticClass:0)
   *     at androidx.compose.ui.platform.AndroidUiFrameClock$withFrameNanos$2$callback$1.doFrame(AndroidUiFrameClock.android.kt:39)
   *     at androidx.compose.ui.platform.AndroidUiDispatcher.performFrameDispatch(AndroidUiDispatcher.android.kt:108)
   *     at androidx.compose.ui.platform.AndroidUiDispatcher.access$performFrameDispatch(AndroidUiDispatcher.android.kt:41)
   *     at androidx.compose.ui.platform.AndroidUiDispatcher$dispatchCallback$1.doFrame(AndroidUiDispatcher.android.kt:69)
   *     at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1337)
   *     at android.view.Choreographer$CallbackRecord.run(Choreographer.java:1348)
   *     at android.view.Choreographer.doCallbacks(Choreographer.java:952)
   *     at android.view.Choreographer.doFrame(Choreographer.java:878)
   *     at android.view.Choreographer$FrameDisplayEventReceiver.run(Choreographer.java:1322)
   *     at android.os.Handler.handleCallback(Handler.java:958)
   *     at android.os.Handler.dispatchMessage(Handler.java:99)
   *     at android.os.Looper.loopOnce(Looper.java:205)
   *     at android.os.Looper.loop(Looper.java:294)
   *     at android.app.ActivityThread.main(ActivityThread.java:8177)
   *
   *  ```
   *
   * i.e. start with either: `RecomposeScopeImpl.compose` or `ComposableLambdaImpl.invoke`. We want
   * to fold from this line down to the first empty line.
   */
  private fun foldEndOfException(start: Int): Int {
    var next = lines.indexOfFirst(start) { it.isEmpty() }
    if (next < 0) {
      return start
    }
    next--

    if (next > start) {
      // Fold the end of the exception here:
      val placeholderText =
        LayoutInspectorBundle.message(
          "layout.inspector.stacktrace.folding.hint",
          (next - start + 1).toString(),
        )
      editor.foldingModel.addFoldRegion(startOffset(start), endOffset(next), placeholderText)?.let {
        it.isExpanded = false
      }
    }
    return next
  }

  private fun line(lineNumber: Int): String =
    if (lines.size > lineNumber) lines[lineNumber].trimStart() else ""

  private fun fetchLines() {
    val text = document.getText()
    lines = text.lines()
  }

  private fun startOffset(line: Int) = document.getLineStartOffset(line)

  private fun endOffset(line: Int) = document.getLineEndOffset(line)
}
