/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework

import com.android.SdkConstants
import com.android.SdkConstants.GRADLE_LATEST_VERSION
import com.android.tools.idea.npw.model.MultiTemplateRenderer.TemplateRendererListener
import com.android.tools.idea.testing.AndroidGradleTests
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtil
import java.io.File

/**
 * Listener to update a new Project created with the New Project Wizard.
 * This listener waits that NPW generates all files (from the NPW Templates) and before the project is imported:
 * - If "build.gradle" is found, it adds to it a list of local repositories
 * - More modification can be added later
 */
class NpwControl(private val project: Project) : TemplateRendererListener {

  override fun multiRenderingFinished() {
    // Update project files.
    updateRepositories(File(project.basePath!!, SdkConstants.FN_BUILD_GRADLE))
    updateRepositories(File(project.basePath!!, SdkConstants.FN_SETTINGS_GRADLE))
    AndroidGradleTests.createGradleWrapper(File(project.basePath!!), GRADLE_LATEST_VERSION) // Point distributionUrl to local file
  }

  private fun updateRepositories(gradleFile: File) {
    if (gradleFile.exists()) {
      val gradleVirtualFile = VfsUtil.findFileByIoFile(gradleFile, true)!!
      val origContent = VfsUtil.loadText(gradleVirtualFile)
      val newContent = AndroidGradleTests.updateLocalRepositories(origContent, AndroidGradleTests.getLocalRepositoriesForGroovy())
      if (newContent != origContent) {
        runWriteAction {
          VfsUtil.saveText(gradleVirtualFile, newContent)
        }
      }
    }
  }
}