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

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel.STRING_TYPE
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.VersionCatalogSource
import com.android.tools.idea.gradle.dsl.api.settings.VersionCatalogModel.VersionCatalogSource.*
import com.android.tools.idea.gradle.dsl.api.util.TypeReference
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl
import com.android.tools.idea.gradle.dsl.model.ext.transforms.SingleArgumentMethodTransform
import com.android.tools.idea.gradle.dsl.parser.settings.VersionCatalogDslElement
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
    if (typeReference.type == VirtualFile::class.java && currentType == FILES) {
      val tomlPath: String = delegate.getValue(STRING_TYPE) ?: return null
      return getFileByBuildRelativePath(tomlPath) as? T
    }
    return delegate.getValue(typeReference)
  }

  /**
   * Returns a file by the given build directory relative path. Assumes that the build directory:
   * - is the one that contains the settings file.
   * - could be not only the base project directory but also a directory of an included build (that could be located outside)
   */
  private fun getFileByBuildRelativePath(relativePath: String): VirtualFile? {
    val settingsFile: VirtualFile = dslElement.dslFile.file
    val buildDirectory = settingsFile.parent
    return buildDirectory.findFileByRelativePath(relativePath)
  }

  override fun toString(): String = delegate.toString()

}