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

import com.android.tools.adtui.status.REFRESH_BUTTON
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.projectsystem.getProjectSystem
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.LightColors
import java.util.function.Function

private const val PREVIEW_OUT_OF_DATE = "The preview is out of date"
private const val BUILD_AND_REFRESH = "Build & Refresh"

internal class CustomViewPreviewNotificationProvider : EditorNotificationProvider {

  override fun collectNotificationData(
    project: Project,
    file: VirtualFile,
  ): Function<FileEditor, EditorNotificationPanel?>? {
    return Function { fileEditor ->
      val previewManager = fileEditor.getCustomViewPreviewManager() ?: return@Function null
      when (previewManager.notificationsState) {
        CustomViewPreviewManager.NotificationsState.CODE_MODIFIED ->
          EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
            setText(PREVIEW_OUT_OF_DATE)
            createActionLabel("$BUILD_AND_REFRESH${getBuildAndRefreshShortcut().asString()}") {
              project.getProjectSystem().getBuildManager().compileFilesAndDependencies(listOf(file))
            }
          }
        CustomViewPreviewManager.NotificationsState.BUILDING ->
          EditorNotificationPanel(fileEditor, EditorNotificationPanel.Status.Info).apply {
            setText("Building...")
            icon(AnimatedIcon.Default())
          }
        CustomViewPreviewManager.NotificationsState.BUILD_FAILED ->
          EditorNotificationPanel(LightColors.RED, EditorNotificationPanel.Status.Error).apply {
            setText("Correct preview cannot be displayed until after a successful build.")
          }
        else -> null
      }
    }
  }
}

internal fun requestBuildForSurface(surface: DesignSurface<*>) {
  val buildManager = surface.project.getProjectSystem().getBuildManager()
  buildManager.compileFilesAndDependencies(surface.models.map { it.virtualFile })
}

/**
 * [AnAction] that triggers a compilation of the current module. The build will automatically
 * trigger a refresh of the surface.
 */
internal class ForceCompileAndRefreshAction(private val surface: DesignSurface<*>) :
  AnAction(BUILD_AND_REFRESH, null, REFRESH_BUTTON) {
  override fun actionPerformed(e: AnActionEvent) = requestBuildForSurface(surface)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val presentation = e.presentation
    presentation.isEnabled = !project.getProjectSystem().getBuildManager().isBuilding
  }
}
