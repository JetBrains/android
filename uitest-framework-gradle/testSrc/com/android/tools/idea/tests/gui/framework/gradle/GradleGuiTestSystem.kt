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
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.guitestsystem.GuiTestSystem
import com.android.tools.idea.tests.gui.framework.guitestsystem.RunWithBuildSystem
import com.google.common.io.Files
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import org.fest.swing.core.Robot
import java.io.File

class GradleGuiTestSystem : GuiTestSystem {
  override val id: String
    get() = GradleGuiTestSystem::class.java.name

  override val buildSystem: RunWithBuildSystem.BuildSystem
    get() = RunWithBuildSystem.BuildSystem.GRADLE

  override fun prepareTestForImport(targetTestDirectory: File) {
    // If the uitestignore file exists, then delete the files listed in that file.
    val ignoreFile = File(targetTestDirectory, "gradle.uitestignore")
    if (ignoreFile.exists()) {
      Files.readLines(ignoreFile, Charsets.UTF_8)
          .map { name -> File(targetTestDirectory, name) }
          .forEach { file -> file.delete() }
    }
  }

  override fun importProject(targetTestDirectory: File, robot: Robot) {
    val toSelect = VfsUtil.findFileByIoFile(targetTestDirectory, true)
    ApplicationManager.getApplication().invokeAndWait { GradleProjectImporter.getInstance().importProject(toSelect!!) }
  }

  override fun waitForProjectSyncToFinish(ideFrameFixture: IdeFrameFixture) {
    ideFrameFixture.waitForGradleProjectSyncToFinish()
  }
}