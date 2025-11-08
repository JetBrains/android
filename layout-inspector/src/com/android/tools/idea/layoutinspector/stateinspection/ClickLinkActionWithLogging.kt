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

import com.intellij.execution.filters.HyperlinkInfo
import com.intellij.execution.impl.EditorHyperlinkListener
import com.intellij.execution.impl.EditorHyperlinkSupport
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.Key

/** The key to indicate where to log ClinkLinkActions */
val CLICK_LINK_LOGGING_KEY = Key.create<EditorHyperlinkListener>("EditorHyperlinkListener")

class ClickLinkActionWithLogging : DumbAwareAction() {

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val hyperlink = getLink(event) ?: return
    hyperlink.navigate(project)
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return
    val listener = editor.getUserData(CLICK_LINK_LOGGING_KEY) ?: return
    listener.hyperlinkActivated(hyperlink)
  }

  private fun getLink(event: AnActionEvent): HyperlinkInfo? {
    val editor = event.getData(CommonDataKeys.EDITOR) ?: return null
    val offset = editor.caretModel.offset
    return EditorHyperlinkSupport.get(editor).getHyperlinkAt(offset)
  }

  override fun update(event: AnActionEvent) {
    val link = getLink(event)
    event.presentation.setEnabledAndVisible(link != null)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }
}
