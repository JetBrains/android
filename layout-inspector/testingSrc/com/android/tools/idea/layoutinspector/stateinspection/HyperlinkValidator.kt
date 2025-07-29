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

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.editor.markup.MarkupModel

/**
 * Test utility to check the folds in the Folding model of an editor. Example:
 * ```
 *     validateMarkupModel(editor.markupModel) {
 *       region(13, "Composition.kt:1015")
 *       region(14, "Recomposer.kt:1519")
 *     }
 * ```
 */
fun validateMarkupModel(model: MarkupModel, block: HyperlinkValidator.() -> Unit) {
  val validator = HyperlinkValidator(model)
  validator.block()
  validator.end()
}

class HyperlinkValidator(model: MarkupModel) {
  val highlighters = model.allHighlighters.toList().sortedBy { it.startOffset }
  val output = formatActual()
  var index: Int = 0

  fun region(line: Int, fileNameAndLineNumber: String) {
    if (index >= highlighters.size) {
      error("There are only ${highlighters.size} highlighters recorded")
    }
    val range = highlighters[index++]
    val document = range.document
    val actualStartLine = document.getLineNumber(range.startOffset) + 1
    val actualEndLine = document.getLineNumber(range.endOffset) + 1
    val message = outputWithMarker()
    assertThat(actualStartLine).named(message).isEqualTo(line)
    assertThat(actualEndLine).named(message).isEqualTo(line)
    assertThat(document.getText(range.textRange)).named(message).isEqualTo(fileNameAndLineNumber)
  }

  fun end() {
    index++
    val rest = outputWithMarker()
    index--
    assertThat(index)
      .named("Only $index out of ${highlighters.size} regions are accounted for:\n $rest")
      .isEqualTo(highlighters.size)
  }

  private fun formatActual(): String {
    val builder = StringBuilder()
    builder.appendLine("validate {")
    highlighters.subList(index, highlighters.size).forEach { range ->
      val document = range.document
      val line = document.getLineNumber(range.startOffset) + 1
      val text = document.getText(range.textRange)
      builder.appendLine("  region($line, \"$text\")")
    }
    builder.appendLine("}")
    return builder.toString()
  }

  private fun outputWithMarker(): String {
    var offset = -1
    repeat(index + 1) { offset = output.indexOf('\n', offset + 1) }
    if (offset < 0) {
      return output
    }
    val builder = StringBuilder(output)
    builder.insert(offset, "  <----- Error here -----")
    return builder.toString()
  }
}
