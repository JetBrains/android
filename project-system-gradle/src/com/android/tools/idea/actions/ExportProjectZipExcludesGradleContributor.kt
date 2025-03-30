/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.actions

import com.android.SdkConstants
import com.android.tools.idea.gradle.util.GradleProjectSystemUtil
import com.android.tools.idea.projectsystem.getProjectSystem
import com.android.tools.idea.projectsystem.gradle.GradleProjectSystem
import com.android.tools.idea.util.toIoFile
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtilRt
import java.io.File

class ExportProjectZipExcludesGradleContributor : ExportProjectZipExcludesContributor {
  override fun isApplicable(project: Project): Boolean = project.getProjectSystem() is GradleProjectSystem

  override fun excludes(project: Project): Collection<File> {
    val result = mutableSetOf<File>()
    // TODO(b/306156888): handle linked Gradle builds properly.
    val basePath = FileUtilRt.toSystemDependentName(project.basePath!!)
    result.add(File(basePath, SdkConstants.DOT_GRADLE))
    result.add(File(basePath, GradleProjectSystemUtil.BUILD_DIR_DEFAULT_NAME))
    result.add(File(basePath, Project.DIRECTORY_STORE_FOLDER))
    ModuleManager.getInstance(project).modules.forEach { module ->
      module.moduleFile?.let { result.add(it.toIoFile())}
    }
    return result
  }
}