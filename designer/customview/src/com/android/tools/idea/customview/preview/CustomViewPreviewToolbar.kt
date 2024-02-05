/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.customview.preview

import com.android.tools.adtui.actions.DropDownAction
import com.android.tools.idea.common.actions.IssueNotificationAction
import com.android.tools.idea.common.editor.SeamlessTextEditorWithPreview
import com.android.tools.idea.common.editor.ToolbarActionGroups
import com.android.tools.idea.common.surface.DesignSurface
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorManager
import icons.StudioIcons

internal class CustomViewPreviewToolbar(surface: DesignSurface<*>) : ToolbarActionGroups(surface) {

  private class CustomViewOption(val viewName: String) : AnAction(viewName) {
    override fun actionPerformed(e: AnActionEvent) {
      // Here we iterate over all editors as change in selection (write) should trigger updates in
      // all of them
      findPreviewEditorsForContext(e.dataContext).forEach { it.currentView = viewName }
    }
  }

  private class CustomViewSelector :
    DropDownAction(null, "Custom View for Preview", StudioIcons.LayoutEditor.Palette.CUSTOM_VIEW) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      removeAll()

      // We need just a single previewEditor here (any) to retrieve (read) the states and currently
      // selected state
      findPreviewEditorsForContext(e.dataContext).firstOrNull()?.let { previewEditor ->
        previewEditor.views.forEach { add(CustomViewOption(it)) }
        e.presentation.setText(previewEditor.currentView, false)
      }
    }

    override fun displayTextInToolbar() = true

    override fun getActionUpdateThread() = ActionUpdateThread.BGT
  }

  override fun getNorthGroup(): ActionGroup {
    val customViewPreviewActions = DefaultActionGroup()
    val customViews = CustomViewSelector()

    val wrapWidth =
      object :
        ToggleAction(
          "Wrap content horizontally",
          "Set preview width to wrap content",
          StudioIcons.LayoutEditor.Toolbar.WRAP_WIDTH,
        ) {
        override fun isSelected(e: AnActionEvent) =
          findPreviewEditorsForContext(e.dataContext).any { it.shrinkWidth }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
          findPreviewEditorsForContext(e.dataContext).forEach { it.shrinkWidth = state }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
      }

    val wrapHeight =
      object :
        ToggleAction(
          "Wrap content vertically",
          "Set preview height to wrap content",
          StudioIcons.LayoutEditor.Toolbar.WRAP_HEIGHT,
        ) {
        override fun isSelected(e: AnActionEvent) =
          findPreviewEditorsForContext(e.dataContext).any { it.shrinkHeight }

        override fun setSelected(e: AnActionEvent, state: Boolean) {
          findPreviewEditorsForContext(e.dataContext).forEach { it.shrinkHeight = state }
        }

        override fun getActionUpdateThread() = ActionUpdateThread.BGT
      }

    customViewPreviewActions.add(customViews)
    customViewPreviewActions.add(wrapWidth)
    customViewPreviewActions.add(wrapHeight)

    return customViewPreviewActions
  }

  override fun getNorthEastGroup(): ActionGroup =
    DefaultActionGroup().apply { add(IssueNotificationAction.getInstance()) }
}

private fun findPreviewEditorsForContext(context: DataContext): List<CustomViewPreviewManager> {
  val project = context.getData(CommonDataKeys.PROJECT) ?: return emptyList()
  val file = context.getData(CommonDataKeys.VIRTUAL_FILE) ?: return emptyList()
  return FileEditorManager.getInstance(project)
    ?.getAllEditors(file)
    ?.filterIsInstance<SeamlessTextEditorWithPreview<out FileEditor>>()
    ?.mapNotNull { it.preview.getCustomViewPreviewManager() }
    ?.distinct() ?: emptyList()
}
