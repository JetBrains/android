/*
 * Copyright (C) 2024 The Android Open Source Project
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
package org.jetbrains.android.facet

import com.android.tools.idea.projectsystem.CommonTestType
import com.android.tools.idea.projectsystem.GradleToken
import com.android.tools.idea.projectsystem.SourceProviders
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.projectsystem.isAndroidTestModule
import com.android.tools.idea.projectsystem.isScreenshotTestModule
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VirtualFile

class ResourceFolderManagerGradleToken : GradleToken, ResourceFolderManagerToken<GradleProjectSystem> {
  override fun computeFoldersFromSourceProviders(
    projectSystem: GradleProjectSystem,
    sourceProviders: SourceProviders,
    module: Module
  ): List<VirtualFile>? = when {
    module.isAndroidTestModule() -> sourceProviders.run {
      val sources = currentDeviceTestSourceProviders[CommonTestType.ANDROID_TEST]?.flatMap { it.resDirectories } ?: listOf()
      val generated = generatedDeviceTestSources[CommonTestType.ANDROID_TEST]?.resDirectories ?: listOf()
      (sources + generated).toList()
    }
    module.isScreenshotTestModule() -> sourceProviders.run {
      val sources = currentHostTestSourceProviders[CommonTestType.SCREENSHOT_TEST]?.flatMap { it.resDirectories } ?: listOf()
      val generated = generatedHostTestSources[CommonTestType.SCREENSHOT_TEST]?.resDirectories ?: listOf()
      (sources + generated).toList()
    }
    else -> null
  }
}