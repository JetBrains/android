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
package com.android.tools.idea.gradle.dsl.model.settings

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.VersionCatalogSource
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.VersionCatalogSource.*
import com.android.tools.idea.gradle.dsl.api.util.TypeReference
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform
import com.android.tools.idea.gradle.dsl.parser.settings.VersionCatalogDslElement
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile

class FromCatalogResolvedProperty(private val dslElement: VersionCatalogDslElement,
                                  private val delegate: ResolvedPropertyModelImpl,
                                  private val currentType: VersionCatalogSource) : ResolvedPropertyModel by delegate {
  fun getType(): VersionCatalogSource = currentType


  fun setAsFilesType(): FromCatalogResolvedProperty {
    return FromCatalogResolvedProperty(dslElement, GradlePropertyModelBuilder.create(dslElement, VersionCatalogModelImpl.FROM)
      .addTransform(SingleArgumentMethodTransform("files"))
      .buildResolved(), FILES)
  }

  fun setAsImportedType(): FromCatalogResolvedProperty {
    return FromCatalogResolvedProperty(dslElement,
                                       GradlePropertyModelBuilder.create(dslElement, VersionCatalogModelImpl.FROM).buildResolved(),
                                       IMPORTED)
  }

  override fun setValue(value: Any) {
    delegate.delete() // need to remove old value first as it may be type transformation
    delegate.setValue(value)
  }

  @Suppress("UNCHECKED_CAST")
  override fun <T> getValue(typeReference: TypeReference<T>): T? {
    val fromValue = delegate.getValue(typeReference)
    if (fromValue is String && currentType == FILES) {
      return getRootRelativeTomlPath(fromValue) as? T
    }
    return fromValue
  }

  override fun toString(): String = delegate.toString()

  /**
   * Returns a version catalog path, relative to the root project directory (makes sense for an included build of a composite build).
   * For example, a settings file of an included build `app` declares a version catalog path `gradle/libs.versions.toml`.
   * In this case, a root relative path `app/gradle/libs.versions.toml` will be returned.
   */
  private fun getRootRelativeTomlPath(buildRelativeTomlPath: String): String? {
    val settingsFile: VirtualFile = dslElement.dslFile.file
    val buildDirectory = settingsFile.parent
    val tomlFile = buildDirectory.findFileByRelativePath(buildRelativeTomlPath) ?: return null
    val rootProjectPath: VirtualFile = dslElement.dslFile.project.guessProjectDir() ?: return null
    return VfsUtilCore.getRelativePath(tomlFile, rootProjectPath, VfsUtilCore.VFS_SEPARATOR_CHAR)
  }
}