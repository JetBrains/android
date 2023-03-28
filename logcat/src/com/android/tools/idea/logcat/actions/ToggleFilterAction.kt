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
import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.filters.LogcatFilterParser
import com.android.tools.idea.logcat.util.getFilterHint
import com.android.tools.idea.logcat.util.toggleFilterTerm
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.DumbAwareAction

/**
 * An action that adds or removes a filter term from the Logcat filter.
 */
internal class ToggleFilterAction(
  private val logcatPresenter: LogcatPresenter,
  private val logcatFilterParser: LogcatFilterParser,
) : DumbAwareAction("Toggle Filter") {

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = false

    val term = getFilterHintTerm(e) ?: return
    val filter = logcatPresenter.getFilter()
    val newFilter = toggleFilterTerm(logcatFilterParser, filter, term)
    if (newFilter != null) {
      e.presentation.isVisible = true
      e.presentation.text = if (filter.contains(term)) {
        LogcatBundle.message("logcat.toggle.filter.remove", term)
      }
      else {
        LogcatBundle.message("logcat.toggle.filter.add", term)
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val term = getFilterHintTerm(e) ?: return
    val newFilter = toggleFilterTerm(logcatFilterParser, logcatPresenter.getFilter(), term)
    if (newFilter != null) {
      logcatPresenter.setFilter(newFilter)
    }
  }
}

private fun getFilterHintTerm(e: AnActionEvent): String? {
  val editor = e.getData(EDITOR) as EditorEx? ?: return null
  val formattingOptions = e.getData(LogcatPresenter.LOGCAT_PRESENTER_ACTION)?.formattingOptions ?: return null
  return editor.getFilterHint(editor.caretModel.offset, formattingOptions)?.getFilter()
}
