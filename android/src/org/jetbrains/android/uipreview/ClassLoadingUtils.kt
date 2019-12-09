/*
 * Copyright (C) 2019 The Android Open Source Project
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
package org.jetbrains.android.uipreview

import com.android.SdkConstants
import com.android.tools.idea.model.AndroidModel
import com.android.utils.SdkUtils
import com.google.common.io.Files
import com.intellij.openapi.module.Module
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidRootUtil
import java.io.File
import java.net.MalformedURLException
import java.net.URL

/**
 * Returns a stream of JAR files of the referenced libraries for the [Module] of this class loader.
 */
private fun Module.getExternalLibraryJars(): Sequence<File> {
  val facet = AndroidFacet.getInstance(this)
  if (facet != null && AndroidModel.isRequired(facet)) {
    val model = AndroidModel.get(facet)
    if (model != null) {
      return model.classJarProvider.getModuleExternalLibraries(this).asSequence()
    }
  }
  return AndroidRootUtil.getExternalLibraries(this).asSequence().map { file: VirtualFile? -> VfsUtilCore.virtualToIoFile(file!!) }
}

/**
 * Returns the list of external JAR files referenced by the class loader.
 */
internal fun Module?.getLibraryDependenciesJars(): List<URL> {
  if (this == null || this.isDisposed) {
    return emptyList()
  }
  return this.getExternalLibraryJars()
    .filter { file: File -> SdkConstants.EXT_JAR == Files.getFileExtension(file.name) && file.exists() }
    .mapNotNull {
      try {
        SdkUtils.fileToUrl(it)
      }
      catch (e: MalformedURLException) {
        null
      }
    }
    .toList()
}