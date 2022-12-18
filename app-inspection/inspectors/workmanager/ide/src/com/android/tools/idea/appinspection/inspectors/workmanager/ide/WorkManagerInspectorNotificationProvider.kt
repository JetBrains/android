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
package com.android.tools.idea.appinspection.inspectors.workmanager.ide

import com.android.tools.idea.IdeInfo
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId.ANDROIDX_WORK_RUNTIME
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.util.androidFacet
import com.intellij.ide.BrowserUtil
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.module.ModuleUtilCore.findModuleForFile
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.psi.KtFile
import java.util.function.Function
import javax.swing.JComponent

private const val DISMISSED_PROPERTY_KEY = "WORKMANAGER_INSPECTOR_NOTIFICATION_DISMISSED"

class WorkManagerInspectorNotificationPanel(private val project: Project, appInspectionToolWindow: ToolWindow) : EditorNotificationPanel(
  Status.Info) {

  private fun dismissBanner() {
    PropertiesComponent.getInstance(project).setValue(DISMISSED_PROPERTY_KEY, true)
    isVisible = false
  }

  init {
    text = "Inspect your workers when deploying to API level 26 or higher using Background Task Inspector."
    @Suppress("DialogTitleCapitalization") // Capitalization to match tool window name
    createActionLabel("Open App Inspection") {
      appInspectionToolWindow.show()
      dismissBanner()
    }
    createActionLabel("Learn more") {
      BrowserUtil.browse("https://d.android.com/r/studio-ui/background-task-inspector-help")
    }
    createActionLabel("Dismiss") {
      dismissBanner()
    }
  }
}

private const val APP_INSPECTION_ID = "App Inspection"

private val ANDROIDX_WORK_IMPORT_PREFIX = "${ANDROIDX_WORK_RUNTIME.mavenGroupId}."

/**
 * A class that creates a context-appropriate banner which informs users about the existence of the background task
 * inspector if the current file they opened in the editor matches a bunch of criteria.
 */
class WorkManagerInspectorNotificationProvider : EditorNotificationProvider {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?> {
    return Function { createNotificationPanel(file, project) }
  }

  private fun createNotificationPanel(file: VirtualFile, project: Project): WorkManagerInspectorNotificationPanel? {
    if (!IdeInfo.getInstance().isAndroidStudio) return null
    if (PropertiesComponent.getInstance(project).getBoolean(DISMISSED_PROPERTY_KEY)) return null

    if (!file.containsWorkImport(project)) return null

    val module = findModuleForFile(file, project) ?: return null
    val facet = module.androidFacet ?: return null
    if (!facet.supportsWorkManagerInspection()) return null

    val appInspectionToolWindow = ToolWindowManager.getInstance(project).getToolWindow(APP_INSPECTION_ID) ?: return null

    return WorkManagerInspectorNotificationPanel(project, appInspectionToolWindow)
  }

  private fun VirtualFile.containsWorkImport(project: Project): Boolean {
    val psiFile = PsiManager.getInstance(project).findFile(this) ?: return false
    return when (psiFile) {
      is PsiJavaFile -> {
        psiFile.importList?.importStatements?.any { importStatement ->
          importStatement.qualifiedName?.startsWith(ANDROIDX_WORK_IMPORT_PREFIX) ?: false
        } ?: false
      }
      is KtFile -> {
        psiFile.importList?.imports?.any { importStatement ->
          importStatement.importedFqName?.toString()?.startsWith(ANDROIDX_WORK_IMPORT_PREFIX) ?: false
        } ?: false
      }
      else -> false
    }
  }

  private fun AndroidFacet.supportsWorkManagerInspection(): Boolean {
    val version = getModuleSystem()
                    .getResolvedDependency(ANDROIDX_WORK_RUNTIME.getCoordinate("+"))
                    ?.version
                  ?: return false

    return (version >= MINIMUM_WORKMANAGER_VERSION)
  }
}

