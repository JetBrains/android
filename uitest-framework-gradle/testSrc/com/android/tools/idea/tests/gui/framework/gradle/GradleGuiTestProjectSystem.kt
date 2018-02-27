/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.gradle

import com.android.tools.idea.gradle.project.importing.GradleProjectImporter
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.GuiTestProjectSystem
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.vfs.VfsUtil
import org.fest.swing.core.Robot
import org.fest.swing.timing.Wait
import java.io.File

class GradleGuiTestProjectSystem : GuiTestProjectSystem {
  override val id: String
    get() = GradleGuiTestProjectSystem::class.java.name

  override val buildSystem: TargetBuildSystem.BuildSystem
    get() = TargetBuildSystem.BuildSystem.GRADLE

  override fun validateSetup() {
    // Gradle does not currently require any extra setup validation.
  }

  override fun prepareTestForImport(targetTestDirectory: File) {
    // If the uitestignore file exists, then delete the files listed in that file.
    val ignoreFile = File(targetTestDirectory, "gradle.uitestignore")
    if (ignoreFile.exists()) {
      Files.readLines(ignoreFile, Charsets.UTF_8)
          .map { name -> File(targetTestDirectory, name) }
          .forEach { file -> file.delete() }
    }
  }

  override fun importProject(targetTestDirectory: File, robot: Robot, buildPath: String?) {
    val previouslyOpenProjects = ProjectManager.getInstance().openProjects
    val toSelect = VfsUtil.findFileByIoFile(targetTestDirectory, true)
    ApplicationManager.getApplication().invokeAndWait { GradleProjectImporter.getInstance().importProject(toSelect!!) }

    // Wait until the project window is actually displayed
    val newOpenProjects = mutableListOf<Project>()
    Wait.seconds(5).expecting("Project to be open").until {
        newOpenProjects.addAll(ProjectManager.getInstance().openProjects)
        newOpenProjects.removeAll(previouslyOpenProjects)
        !newOpenProjects.isEmpty()
      }

    // After the project is opened there will be an Index and a Gradle Sync phase, and these can happen in any order.
    // Waiting for indexing to finish, makes sure Sync will start next or all Sync was done already.
    GuiTests.waitForProjectIndexingToFinish(newOpenProjects[0])
  }

  override fun requestProjectSync(ideFrameFixture: IdeFrameFixture): GuiTestProjectSystem {
    ideFrameFixture.invokeMenuPath("File", "Sync Project with Gradle Files")
    return this
  }

  override fun waitForProjectSyncToFinish(ideFrameFixture: IdeFrameFixture) {
    ideFrameFixture.waitForGradleProjectSyncToFinish()
  }

  override fun getProjectRootDirectory(project: Project) = project.baseDir!!
}