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
package com.android.tools.idea.common.error

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import java.awt.datatransfer.StringSelection
import javax.swing.JTree

class CopyIssueDescriptionAction : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    val description = getSelectedNode(event)?.getDescription()
    event.presentation.isEnabledAndVisible = description != null
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun actionPerformed(event: AnActionEvent) {
    val description = getSelectedNode(event)?.getDescription() ?: return
    CopyPasteManager.getInstance().setContents(StringSelection(description))
  }
}

private fun getSelectedNode(event: AnActionEvent): DesignerCommonIssueNode? {
  val tree = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? JTree
  return tree?.selectionPath?.lastPathComponent as? DesignerCommonIssueNode
}
