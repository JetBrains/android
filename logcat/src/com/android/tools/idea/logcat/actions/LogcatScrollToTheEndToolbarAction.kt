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
import com.android.tools.idea.logcat.util.isScrollAtBottom
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.actions.ScrollToTheEndToolbarAction
import com.intellij.openapi.editor.ex.EditorEx

/**
 * A Logcat specific version of [ScrollToTheEndToolbarAction]
 *
 * This version takes into account the scrollbar position for toggling the action state.
 */
internal class LogcatScrollToTheEndToolbarAction(private val editor: EditorEx): ScrollToTheEndToolbarAction(editor) {
  init {
    @Suppress("DialogTitleCapitalization")
    templatePresentation.text = LogcatBundle.message("logcat.scroll.to.end.action.text")
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return super.isSelected(e) && editor.isScrollAtBottom(false)
  }
}