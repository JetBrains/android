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
package com.android.tools.idea.gradle.dsl.model

import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogView
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.NotNull
import java.io.File

/**
 * Implementation returns updated CatalogToFileMap if underneath settings file is changed.
 */
class GradleVersionCatalogViewImpl(private val parsedModel: GradleSettingsModel?) : GradleVersionCatalogView {
  private var catalogToFile: Map<String, VirtualFile>
  private var lastModificationStamp: Long?

  init {
    catalogToFile = catalogToFileMap(parsedModel)
    lastModificationStamp = getTimeStamp(parsedModel)
  }

  private fun catalogToFileMap(settings: GradleSettingsModel?): Map<String, VirtualFile> {
    val result: MutableMap<String, VirtualFile> = HashMap()
    if (settings == null) return result
    for (versionCatalogModel in settings.dependencyResolutionManagement().versionCatalogs()) {
      val name = versionCatalogModel.name
      val path = versionCatalogModel.from().getValue(GradlePropertyModel.STRING_TYPE)
      if (path != null) {
        normalisePath(path)?.let { result[name] = it }
      }
    }
    return result
  }

  private fun getTimeStamp(parsedModel: GradleSettingsModel?) =
    parsedModel?.psiFile?.modificationStamp

  private fun normalisePath(path: String): VirtualFile? {
    val fromPath = FileUtil.toSystemIndependentName(path)
    val project = parsedModel?.project
    val rootPath = project?.guessProjectDir()?.path ?: return null
    val normalizedPath = "$rootPath/$fromPath"
    return VfsUtil.findFileByIoFile(File(FileUtil.toSystemDependentName(
      normalizedPath)), false)
  }

  private fun updateViewIfRequired() {
    val newModificationStamp = getTimeStamp(parsedModel)
    if (lastModificationStamp == null || newModificationStamp == null || newModificationStamp > lastModificationStamp!!) {
      catalogToFile = catalogToFileMap(parsedModel)
      lastModificationStamp = newModificationStamp
    }
  }

  override fun getCatalogToFileMap(): Map<String, VirtualFile> {
    updateViewIfRequired()
    return catalogToFile.toMap()
  }
}