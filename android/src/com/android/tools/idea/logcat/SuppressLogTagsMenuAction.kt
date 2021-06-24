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
package com.android.tools.idea.logcat

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR
import com.intellij.openapi.actionSystem.CommonDataKeys.PROJECT
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.text.StringUtil
import org.jetbrains.android.util.AndroidBundle

internal class SuppressLogTagsMenuAction(private val onRefresh: Runnable) : DumbAwareAction(
  AndroidBundle.message("android.configure.logcat.suppress.single.tag.text"),
  AndroidBundle.message("android.configure.logcat.suppress.single.tag.description"),
  AllIcons.RunConfigurations.ShowIgnored) {

  private val preferences: AndroidLogcatGlobalPreferences = AndroidLogcatGlobalPreferences.getInstance()

  override fun actionPerformed(e: AnActionEvent) {
    val editor = e.getData(EDITOR) ?: throw IllegalArgumentException("AnActionEvent Data Context is missing required Editor")
    val document = editor.document
    val selectionModel = editor.selectionModel
    val tags = if (selectionModel.hasSelection()) {
      extractLogTagsFromDocument(document, selectionModel.selectionStart, selectionModel.selectionEnd)
    }
    else {
      val offset = editor.caretModel.offset
      extractLogTagsFromDocument(document, offset, offset)
    }
    if (tags.size <= 1) {
      preferences.suppressedLogTags.addAll(tags)
      onRefresh.run()
    }
    else {
      val project = e.getData(PROJECT) ?: throw IllegalArgumentException("AnActionEvent Data Context is missing required Project")
      val confirmDialog = SuppressLogTagsDialog.newConfirmTagsDialog(project, tags)
      if (confirmDialog.dialogWrapper.showAndGet()) {
        val selectedTags = confirmDialog.getSelectedTags()
        if (selectedTags.isNotEmpty()) {
          preferences.suppressedLogTags.addAll(selectedTags)
          onRefresh.run()
        }
      }
    }
  }

  private fun extractLogTagsFromDocument(document: Document, offsetStart: Int, offsetEnd: Int): Set<String> {
    val tags = mutableSetOf<String>()
    if (offsetEnd <= document.textLength) {
      val start = document.getLineStartOffset(document.getLineNumber(offsetStart))
      val end = document.getLineEndOffset(document.getLineNumber(offsetEnd))
      val lines = StringUtil.splitByLines(document.text.substring(start, end).trim())
      for (line in lines) {
        val matcher = AndroidLogcatFormatter.TAG_PATTERN.matcher(line)
        if (matcher.find()) {
          tags.add(matcher.group(AndroidLogcatFormatter.TAG_PATTERN_GROUP_NAME))
        }
      }
    }
    return tags
  }
}

