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
package com.android.tools.idea.logcat.messages

import com.android.annotations.concurrency.UiThread
import com.intellij.openapi.editor.ex.DocumentEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.project.Project
import kotlin.math.max

internal class DocumentAppender(project: Project, private val document: DocumentEx) {
  private val markupModel = DocumentMarkupModel.forDocument(document, project, /* create= */ true)

  @UiThread
  fun appendToDocument(buffer: TextAccumulator) {
    // Under extreme conditions, we could be inserting text that is longer than the cyclic buffer.
    // TODO(aalbert): Consider optimizing by truncating text to not be longer than cyclic buffer.
    document.insertString(document.textLength, buffer.text)

    // Document has a cyclic buffer, so we need to get document.textLength again after inserting text.
    val offset = document.textLength - buffer.text.length
    for (range in buffer.ranges) {
      val rangeEnd = offset + range.end
      if (rangeEnd <= 0) {
        continue
      }
      val rangeStart = max(offset + range.start, 0)
      markupModel.addRangeHighlighter(
        rangeStart, offset + range.end, HighlighterLayer.SYNTAX, range.textAttributes, HighlighterTargetArea.EXACT_RANGE)
    }
  }
}