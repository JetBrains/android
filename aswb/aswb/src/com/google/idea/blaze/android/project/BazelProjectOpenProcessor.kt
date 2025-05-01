/*
 * Copyright 2018 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.project

import com.android.tools.idea.projectsystem.ProjectSystemService.Companion.projectSystemOpenProjectTask
import com.google.idea.blaze.android.projectsystem.BlazeProjectSystemProvider
import com.google.idea.blaze.base.settings.Blaze
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor
import com.intellij.util.application
import icons.BlazeIcons
import javax.swing.Icon

/**
 * Allows directly opening a project (`File` -> `Open folder` in UI).
 */
class BazelProjectOpenProcessor : ProjectOpenProcessor() {
  override val name: String get() = Blaze.defaultBuildSystemName() + " Project"
  override val icon: Icon get() = BlazeIcons.Logo
  override val isStrongProjectInfoHolder: Boolean    get() = true
  override fun lookForProjectsInDirectory(): Boolean = true

  /**
   * Check if the project directory contains a blaze project file.
   */
  override fun canOpenProject(file: VirtualFile): Boolean {
    return if (file.isDirectory) file.children.any { checkIfProjectFile(it) } else checkIfProjectFile(file)
  }

  private fun checkIfProjectFile(file: VirtualFile): Boolean {
    return file.path.contains(BLAZEPROJECT) || file.path.contains(BAZELPROJECT)
  }

  override fun doOpenProject(
    virtualFile: VirtualFile,
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
  ): Project? = error("Not expected to be called")

  override suspend fun openProjectAsync(
    virtualFile: VirtualFile,
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
  ): Project? {
    val file = if (checkIfProjectFile(virtualFile)) virtualFile.parent else virtualFile
    return ProjectManagerEx.getInstanceEx().openProjectAsync(
      file.toNioPath(),
      projectSystemOpenProjectTask(
        BlazeProjectSystemProvider.ID,
        forceOpenInNewFrame, projectToClose
      ) { project ->
        if (application.isUnitTestMode) {
          PROJECT_INITIALIZER_FOR_TESTING_EXTENSION_POINT_NAME.extensionList.forEach { it(project) }
        }
        true
      }
    )
  }

  fun interface BazelProjectInitializerForTesting: (Project) -> Unit

  companion object {
    @JvmField
    val PROJECT_INITIALIZER_FOR_TESTING_EXTENSION_POINT_NAME: ExtensionPointName<BazelProjectInitializerForTesting> = ExtensionPointName("com.google.idea.blaze.projectInitializerForTesting")

    const val BAZELPROJECT: String = ".bazelproject"
    const val BLAZEPROJECT: String = ".blazeproject"
  }
}
