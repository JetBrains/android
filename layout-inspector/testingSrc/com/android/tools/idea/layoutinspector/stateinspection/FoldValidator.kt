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
import com.intellij.openapi.editor.FoldingModel
import com.intellij.testFramework.runInEdtAndWait

/**
 * Test utility to check the folds in the Folding model of an editor. Example:
 * ```
 *    validateFoldingModel(editor.foldingModel) {
 *       fold(startLine= 2, endLine=12, "<detailed value...>")
 *       fold(startLine = 13, endLine=19, "<7 more...>")
 *       fold(startLine = 26, endLine=79, "<54 more...>")
 *       fold(startLine = 82, endLine=90, "<9 more...>")
 *       fold(startLine = 95, endLine=122, "<28 more...>")
 *     }
 * ```
 */
fun validateFoldingModel(model: FoldingModel, block: FoldValidator.() -> Unit) {
  runInEdtAndWait {
    val validator = FoldValidator(model)
    validator.block()
    validator.end()
  }
}

class FoldValidator(private val model: FoldingModel) {
  var index: Int = 0
  val output = formatActual()

  fun fold(startLine: Int, endLine: Int, placeHolder: String) {
    if (index >= model.allFoldRegions.size) {
      error("There are only ${model.allFoldRegions.size} fold regions recorded")
    }
    val region = model.allFoldRegions[index++]
    val document = region.document
    val actualStartLine = document.getLineNumber(region.startOffset) + 1
    val actualEndLine = document.getLineNumber(region.endOffset) + 1
    val message = outputWithMarker()
    assertThat(actualStartLine).named(message).isEqualTo(startLine)
    assertThat(actualEndLine).named(message).isEqualTo(endLine)
    assertThat(document.getLineStartOffset(startLine - 1))
      .named(message)
      .isEqualTo(region.startOffset)
    assertThat(document.getLineEndOffset(endLine - 1)).named(message).isEqualTo(region.endOffset)
    assertThat(region.placeholderText).named(message).isEqualTo(placeHolder)
  }

  fun end() {
    index++
    val rest = outputWithMarker()
    index--
    val expected = model.allFoldRegions.size
    assertThat(index)
      .named("Only $index out of $expected regions are accounted for:\n $rest")
      .isEqualTo(expected)
  }

  private fun formatActual(): String {
    val builder = StringBuilder()
    builder.appendLine("validate {")
    model.allFoldRegions.forEach { region ->
      val document = region.document
      val startLine = document.getLineNumber(region.startOffset) + 1
      val endLine = document.getLineNumber(region.endOffset) + 1
      builder.appendLine("  fold($startLine, $endLine, \"${region.placeholderText}\")")
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
