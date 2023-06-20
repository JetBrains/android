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
package com.android.tools.idea.gradle

import com.android.tools.idea.gradle.model.IdeAndroidLibrary
import com.android.tools.idea.gradle.model.IdeJavaLibrary
import com.android.tools.idea.gradle.model.IdeModuleLibrary
import com.android.tools.idea.gradle.model.IdeUnknownLibrary
import com.android.tools.idea.gradle.project.model.GradleAndroidModel
import com.android.tools.idea.model.ClassJarProvider
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtilCore
import org.jetbrains.android.facet.AndroidRootUtil.getExternalLibraries
import java.io.File

class AndroidGradleClassJarProvider : ClassJarProvider {
  override fun getModuleExternalLibraries(module: Module): List<File> {
    val gradleModule = GradleAndroidModel.get(module)
                       ?: return getExternalLibraries(module).map(VfsUtilCore::virtualToIoFile)
    return gradleModule.selectedMainRuntimeDependencies.libraries.flatMap { library ->
      when (library) {
        is IdeAndroidLibrary -> library.runtimeJarFiles
        is IdeJavaLibrary -> listOf(library.artifact)
        is IdeModuleLibrary -> listOf()
        is IdeUnknownLibrary -> listOf()
      }
    }
  }
}