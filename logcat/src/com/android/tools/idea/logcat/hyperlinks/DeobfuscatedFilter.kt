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
package com.android.tools.idea.logcat.hyperlinks

import com.android.tools.adtui.validation.ErrorDetailDialog
import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.messages.LOGCAT_MESSAGE_KEY
import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project

private val linkText = LogcatBundle.message("logcat.proguard.link.text")

/** Creates a link for showing the obfuscated stack trace of a deobfuscated log entry */
internal class DeobfuscatedFilter(private val editor: EditorEx) : Filter, DumbAware {
  override fun applyFilter(line: String, entireLength: Int): Filter.Result? {
    val offset = entireLength - line.length
    val start = line.indexOf(linkText)
    if (start < 0) {
      return null
    }
    val end = start + linkText.length

    val item = Filter.ResultItem(start + offset, end + offset, DeobfuscatedHyperlinkInfo(editor))

    return Filter.Result(listOf(item))
  }

  private class DeobfuscatedHyperlinkInfo(private val editor: EditorEx) : HyperlinkInfo {
    override fun navigate(project: Project) {
      val offset = editor.caretModel.offset
      editor.document.processRangeMarkersOverlappingWith(offset, offset) {
        val message =
          it.getUserData(LOGCAT_MESSAGE_KEY) ?: return@processRangeMarkersOverlappingWith true
        val title = LogcatBundle.message("logcat.proguard.original.trace.dialog.title")
        val header = LogcatBundle.message("logcat.proguard.original.trace.dialog.header")
        val text = message.message.replace("r8-map-id-.*:".toRegex(), "SourceFile:")
        ErrorDetailDialog(title, header, text).show()
        false
      }
    }
  }
}
