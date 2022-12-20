/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.logcat.actions

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.LogcatToolWindowFactory
import com.android.tools.idea.logcat.messages.LOGCAT_FILTER_HINT_KEY
import com.android.tools.idea.logcat.messages.TextAccumulator.FilterHint.Tag
import com.android.tools.idea.logcat.settings.AndroidLogcatSettings
import com.intellij.openapi.actionSystem.ActionUpdateThread.EDT
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction

/**
 * An action that adds or a tag to the global ignore tag set
 */
internal class IgnoreTagAction : DumbAwareAction("Ignore Tag") {

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = false
    val tag = e.findTagAtCaret() ?: return
    e.presentation.isVisible = true
    e.presentation.text = LogcatBundle.message("logcat.ignore.tag", tag)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val tag = e.findTagAtCaret() ?: return
    val settings = AndroidLogcatSettings.getInstance()
    settings.ignoredTags += tag
    LogcatToolWindowFactory.logcatPresenters.forEach { it.reloadMessages() }
  }

  override fun getActionUpdateThread() = EDT
}

private fun AnActionEvent.findTagAtCaret(): String? {
  val editor = getData(CommonDataKeys.EDITOR) as EditorEx? ?: return null
  val offset = editor.caretModel.offset

  var tag: String? = null
  editor.document.processRangeMarkersOverlappingWith(offset, offset) {
    tag = it.getUserData(LOGCAT_FILTER_HINT_KEY)?.takeIf { hint -> hint is Tag }?.text
    tag == null
  }
  return tag
}
