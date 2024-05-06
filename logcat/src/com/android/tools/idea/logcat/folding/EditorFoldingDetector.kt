/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.logcat.folding

import com.android.annotations.concurrency.UiThread
import com.intellij.execution.ConsoleFolding
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project

private val consoleView = ConsoleViewForFolding()

/**
 * A [FoldingDetector] that adds [com.intellij.openapi.editor.FoldRegion] to an [Editor].
 *
 * This code is based on ConsoleViewImpl.updateFoldings() but simplified considerably due to
 * additional assumptions that can be made about the text being processed.
 *
 * The original code seems to have been designed to handle folding regions that can span between
 * consecutive calls to the updateFoldings() method. However, this code can safely assume that
 * folding regions are contained in a single call to [detectFoldings] because Logcat messages are
 * always added as a whole piece of text and never split.
 *
 * The simplified code also has a side effect of handling nested folding better than the original.
 * For example, given the following stack trace:
 * ```
 * java.lang.RuntimeException: Fail
 *   at com.example.myapplication.MainActivity.printToLogcat(MainActivity.java:32)
 *   at java.lang.reflect.Method.invoke(Native Method)
 *   at androidx.appcompat.app.AppCompatViewInflater$DeclaredOnClickListener.onClick(AppCompatViewInflater.java:409)
 *   at android.os.Looper.loop(Looper.java:223)
 *   at android.app.ActivityThread.main(ActivityThread.java:7656)
 *   at java.lang.reflect.Method.invoke(Native Method)
 *   at com.android.internal.os.RuntimeInit$MethodAndArgsCaller.run(RuntimeInit.java:592)
 * Caused by: java.lang.Exception: error
 *   at com.example.myapplication.MainActivity.doMoreWork(MainActivity.java:25)
 *   at com.example.myapplication.MainActivity.doSomeWork(MainActivity.java:21)
 *   at com.example.myapplication.MainActivity.printToLogcat(MainActivity.java:30)
 *   ... 7 more
 * ```
 *
 * This stack trace results in nested folding regions. The "`7 more`" line is expanded into the 7
 * lines of the top level exception which themselves contains 2 regions (Method.invoke).
 *
 * The original code combines the nested regions with the outer one and shows the hint:
 * ```
 * <1 more...> <1 internal line> <3 more...> <1 internal line> <1 more...>
 * ```
 *
 * Which is very confusing and results in unexpected behavior when expanding the regions,
 *
 * The new code results in:
 * ```
 * <7 more...>
 * ```
 *
 * And when expanded, shows 5 lines with 2 regions of:
 * ```
 * <1 internal line>
 * ```
 */
internal class EditorFoldingDetector(
  private val project: Project,
  private val editor: Editor,
  consoleFoldings: List<ConsoleFolding> = ConsoleFolding.EP_NAME.extensionList + ExceptionFolding(),
) : FoldingDetector {
  private val document = editor.document
  private val activeConsoleFoldings = consoleFoldings.filter { it.isEnabledForConsole(consoleView) }

  @UiThread
  override fun detectFoldings(startLine: Int, endLine: Int) {
    if (activeConsoleFoldings.isEmpty()) return

    editor.foldingModel.runBatchFoldingOperation {
      for (folding in activeConsoleFoldings) {
        var line = startLine
        while (line <= endLine) {
          // Outer loop finds first folded line
          if (!shouldFoldLine(folding, line)) {
            line++
            continue
          }
          val foldStartLine = line
          line++
          while (line <= endLine && shouldFoldLine(folding, line)) {
            // Inner loop finds last folding line
            line++
          }
          addFoldRegion(folding, foldStartLine, line - 1)
        }
      }
    }
  }

  private fun shouldFoldLine(folding: ConsoleFolding, line: Int) =
    folding.shouldFoldLine(project, getLineText(line))

  private fun addFoldRegion(folding: ConsoleFolding, startLine: Int, endLine: Int) {
    val lines = (startLine..endLine).map(this::getLineText)
    val startOffset =
      document.getLineStartOffset(startLine).let {
        if (folding.shouldBeAttachedToThePreviousLine() && it > 0) it - 1 else it
      }
    val endOffset = document.getLineEndOffset(endLine)
    val placeholder = folding.getPlaceholderText(project, lines) ?: return

    editor.foldingModel.addFoldRegion(startOffset, endOffset, placeholder)?.let {
      it.isExpanded = false
    }
  }

  private fun getLineText(line: Int) =
    EditorHyperlinkSupport.getLineText(document, line, /* includeEol */ false)
}
