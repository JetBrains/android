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
package com.android.tools.idea.tests.gui.framework.bazel

import com.android.testutils.TestUtils
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.bazel.fixture.ImportBazelProjectWizardFixture
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture
import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.GuiTestProjectSystem
import com.android.tools.idea.tests.gui.framework.guitestprojectsystem.TargetBuildSystem
import com.google.common.io.Files
import com.intellij.openapi.util.SystemInfo
import org.fest.swing.core.Robot
import java.io.File

class BazelGuiTestProjectSystem : GuiTestProjectSystem {
  override val id: String
    get() = BazelGuiTestProjectSystem::class.java.name

  override val buildSystem: TargetBuildSystem.BuildSystem
    get() = TargetBuildSystem.BuildSystem.BAZEL

  override fun prepareTestForImport(targetTestDirectory: File) {
    // If the uitestignore file exists, then delete the files listed in that file.
    val ignoreFile = File(targetTestDirectory, "bazel.uitestignore")
    if (ignoreFile.exists()) {
      Files.readLines(ignoreFile, Charsets.UTF_8)
          .map { name -> File(targetTestDirectory, name) }
          .forEach { file -> file.delete() }
    }

    targetTestDirectory
        .walk()
        .filter { f -> f.exists() && f.name.endsWith(".bazeltestfile") }
        .forEach { f -> f.renameTo(File(f.parent, f.nameWithoutExtension)) }


    val androidSdkRepositoryInfo =
        """
android_sdk_repository(
    name = "androidsdk",
    path = "${getSdkPath()}"
)
        """

    Files.append(androidSdkRepositoryInfo, File(targetTestDirectory, "WORKSPACE"), Charsets.UTF_8)
  }

  override fun importProject(targetTestDirectory: File, robot: Robot) {
    openBazelImportWizard(robot)
        .setWorkspacePath(targetTestDirectory.path)
        .clickNext()
        .setBazelBinaryPath(getBazelBinaryPath())
        .clickNext()
        .selectGenerateFromBuildFileOptionAndSetPath("app/BUILD")
        .clickNext()
        .uncommentApi27()
        .clickFinish()
  }

  override fun waitForProjectSyncToFinish(ideFrameFixture: IdeFrameFixture) {
    // For bazel projects all we need to wait for are all background tasks to finish, as the bazel
    // sync is a part of the background tasks.
    GuiTests.waitForBackgroundTasks(ideFrameFixture.robot())
  }

  private fun openBazelImportWizard(robot: Robot): ImportBazelProjectWizardFixture {
    WelcomeFrameFixture.find(robot).importBazelProject()
    return ImportBazelProjectWizardFixture.find(robot)
  }

  private fun getBazelBinaryPath(): String {
    val platformPath = getPlatformPathName() ?: throw RuntimeException("Running test on unsupported platform for bazel")
    return File(TestUtils.getWorkspaceRoot(), "prebuilts/tools/$platformPath/bazel/bazel-real").path
  }

  private fun getSdkPath(): String {
    val osName = getOSName() ?: throw RuntimeException("Running test on unsupported OS for bazel")
    return File(TestUtils.getWorkspaceRoot(), "prebuilts/studio/sdk/$osName").path
  }

  private fun getPlatformPathName(): String? {
    val platformArch = if (System.getProperty("os.arch")?.endsWith("64") ?: return null) "x86_64" else "x86"

    return when {
      SystemInfo.isWindows -> if (platformArch == "x86") "windows" else "windows-x86_64"
      SystemInfo.isLinux -> "linux-$platformArch"
      SystemInfo.isMac -> "darwin-$platformArch"
      else -> null
    }
  }

  private fun getOSName(): String? {
    return when {
      SystemInfo.isWindows -> "windows"
      SystemInfo.isLinux -> "linux"
      SystemInfo.isMac -> "darwin"
      else -> null
    }
  }
}

