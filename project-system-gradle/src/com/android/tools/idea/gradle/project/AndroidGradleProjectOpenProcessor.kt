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
package com.android.tools.idea.gradle.project

import com.android.tools.idea.gradle.project.ProjectImportUtil.findGradleTarget
import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.gradle.util.GradleProjects
import com.android.tools.idea.util.toPathString
import com.android.tools.idea.util.toVirtualFile
import com.intellij.featureStatistics.fusCollectors.LifecycleUsageTriggerCollector
import com.intellij.ide.GeneralSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectNewWindowDoNotAskOption
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor


/**
 * A project open processor to open Gradle projects in Android Studio.
 *
 * It supports opening projects with or without .idea directory.
 */
class AndroidGradleProjectOpenProcessor : ProjectOpenProcessor() {
  override val name: String
    get() = "Android Gradle"

  override fun canOpenProject(file: VirtualFile): Boolean =
      GradleProjects.canImportAsGradleProject(file)

  override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    if (!canOpenProject(virtualFile)) return null

    val importTarget = findGradleTarget(virtualFile) ?: virtualFile
    val adjustedOpenTarget =
        if (importTarget.isDirectory)importTarget
        else importTarget.parent

    val gradleImporter = GradleProjectImporter.getInstance()
    if (!canOpenAsExistingProject(adjustedOpenTarget)) {
      if (!forceOpenInNewFrame) {
        if (!promptToCloseIfNecessary(projectToClose)) {
          return null
        }
      }

      val projectFolder = if (virtualFile.isDirectory) virtualFile else virtualFile.parent
      return gradleImporter.importAndOpenProjectCore(projectToClose, forceOpenInNewFrame, projectFolder)
    }
    return ProjectManagerEx.getInstanceEx().openProject(
      adjustedOpenTarget.toNioPath(), OpenProjectTask(
        forceOpenInNewFrame = forceOpenInNewFrame,
        projectToClose = projectToClose,
      )
    )
  }

  private fun promptToCloseIfNecessary(project: Project?): Boolean {
    var success = true
    val openProjects = ProjectManager.getInstance().openProjects
    if (openProjects.isNotEmpty()) {
      val exitCode = confirmOpenNewProject()
      if (exitCode == GeneralSettings.OPEN_PROJECT_SAME_WINDOW) {
        val toClose = if (project != null && !project.isDefault) project else openProjects[openProjects.size - 1]
        if (!ProjectManagerEx.getInstanceEx().closeAndDispose(toClose)) {
          success = false
        }
      }
      else if (exitCode != GeneralSettings.OPEN_PROJECT_NEW_WINDOW) {
        success = false
      }
    }
    return success
  }

  private fun canOpenAsExistingProject(file: VirtualFile): Boolean =
      file.toPathString().resolve(Project.DIRECTORY_STORE_FOLDER).toVirtualFile(true) != null
}

/**
 * todo Android should somehow do not duplicate platfrom functionality (it should be as part of openProject)
 */
@Suppress("DuplicatedCode")
private fun confirmOpenNewProject(): Int {
  if (ApplicationManager.getApplication().isUnitTestMode) {
    return GeneralSettings.OPEN_PROJECT_NEW_WINDOW
  }

  var mode = GeneralSettings.getInstance().confirmOpenNewProject
  if (mode == GeneralSettings.OPEN_PROJECT_ASK) {
    val message =  IdeBundle.message("prompt.open.project.in.new.frame")
    val exitCode = MessageDialogBuilder.yesNoCancel(IdeBundle.message("title.open.project"), message)
      .yesText(IdeBundle.message("button.existing.frame"))
      .noText(IdeBundle.message("button.new.frame"))
      .doNotAsk(ProjectNewWindowDoNotAskOption())
      .guessWindowAndAsk()
    mode = when (exitCode) {
      Messages.YES -> GeneralSettings.OPEN_PROJECT_SAME_WINDOW
      Messages.NO -> GeneralSettings.OPEN_PROJECT_NEW_WINDOW
      else -> Messages.CANCEL
    }
    if (mode != Messages.CANCEL) {
      LifecycleUsageTriggerCollector.onProjectFrameSelected(mode)
    }
  }
  return mode
}
