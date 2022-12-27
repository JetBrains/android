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

import com.android.tools.adtui.common.ColoredIconGenerator
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.editors.shortcuts.asString
import com.android.tools.idea.editors.shortcuts.getBuildAndRefreshShortcut
import com.android.tools.idea.gradle.project.build.GradleBuildState
import com.android.tools.idea.gradle.project.build.invoker.GradleBuildInvoker
import com.android.tools.idea.gradle.project.build.invoker.TestCompileType
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.JBColor
import com.intellij.ui.LightColors
import java.util.function.Function
import javax.swing.JComponent

private const val PREVIEW_OUT_OF_DATE = "The preview is out of date"
private const val BUILD_AND_REFRESH = "Build & Refresh"

private fun requestBuild(project: Project, module: Module) =
  GradleBuildInvoker.getInstance(project).compileJava(setOf(module).toTypedArray(), TestCompileType.NONE)

internal class CustomViewPreviewNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    return Function { createNotificationPanel(file, it, project) }
  }

  private fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    val previewManager = fileEditor.getCustomViewPreviewManager() ?: return null
    val module = ModuleUtil.findModuleForFile(file, project) ?: return null
    return when (previewManager.notificationsState) {
      CustomViewPreviewManager.NotificationsState.CODE_MODIFIED -> EditorNotificationPanel(fileEditor,
                                                                                           EditorNotificationPanel.Status.Info).apply {
        setText(PREVIEW_OUT_OF_DATE)
        createActionLabel("$BUILD_AND_REFRESH${getBuildAndRefreshShortcut().asString()}") {
          requestBuild(project, module)
        }
      }
      CustomViewPreviewManager.NotificationsState.BUILDING -> EditorNotificationPanel(fileEditor,
                                                                                      EditorNotificationPanel.Status.Info).apply {
        setText("Building...")
        icon(AnimatedIcon.Default())
      }
      CustomViewPreviewManager.NotificationsState.BUILD_FAILED -> EditorNotificationPanel(LightColors.RED,
                                                                                          EditorNotificationPanel.Status.Error).apply {
        setText("Correct preview cannot be displayed until after a successful build.")
      }
      else -> null
    }
  }
}


private val GREEN_REFRESH_BUTTON = ColoredIconGenerator.generateColoredIcon(AllIcons.Actions.ForceRefresh,
                                                                            JBColor(0x59A869, 0x499C54))

internal fun requestBuildForSurface(surface: DesignSurface<*>) {
  surface.models.map { it.module }.distinct().forEach {
    requestBuild(surface.project, it)
  }
}
/**
 * [AnAction] that triggers a compilation of the current module. The build will automatically trigger a refresh of the surface.
 */
internal class ForceCompileAndRefreshAction(private val surface: DesignSurface<*>) :
  AnAction(BUILD_AND_REFRESH, null, GREEN_REFRESH_BUTTON) {
  override fun actionPerformed(e: AnActionEvent) = requestBuildForSurface(surface)

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val presentation = e.presentation
    presentation.isEnabled = !GradleBuildState.getInstance(project).isBuildInProgress
  }
}