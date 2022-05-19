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
package com.android.tools.idea.res

import com.android.SdkConstants
import com.android.SdkConstants.EXT_GRADLE_DECLARATIVE
import com.android.SdkConstants.EXT_GRADLE_KTS
import com.android.annotations.concurrency.Slow
import com.android.tools.idea.fileTypes.FontFileType
import com.android.tools.idea.flags.StudioFlags.GRADLE_DECLARATIVE_IDE_SUPPORT
import com.android.tools.idea.lang.aidl.AidlFileType
//import com.android.tools.idea.lang.rs.AndroidRenderscriptFileType
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.ide.highlighter.XmlFileType
import com.intellij.lang.properties.PropertiesFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.intellij.images.fileTypes.ImageFileTypeManager
import org.jetbrains.kotlin.idea.KotlinFileType
//import org.jetbrains.plugins.gradle.config.GradleFileType

fun isGradleFile(psiFile: PsiFile): Boolean {
  return false
  //if (GradleFileType.isGradleFile(psiFile)) return true
  //
  //val fileType = psiFile.fileType
  //val name = psiFile.name
  //if (fileType.name == "Kotlin" && name.endsWith(EXT_GRADLE_KTS)) return true
  //if (GRADLE_DECLARATIVE_IDE_SUPPORT.get() && name.endsWith(EXT_GRADLE_DECLARATIVE)) return true
  //
  //// Do not test getFileType() as this will differ depending on whether the TOML plugin is
  //// active.
  //if (name.endsWith(SdkConstants.DOT_VERSIONS_DOT_TOML)) return true
  //
  //return fileType === PropertiesFileType.INSTANCE &&
  //  (SdkConstants.FN_GRADLE_PROPERTIES == name ||
  //    SdkConstants.FN_GRADLE_WRAPPER_PROPERTIES == name ||
  //    (SdkConstants.FN_GRADLE_CONFIG_PROPERTIES == name &&
  //      SdkConstants.FD_GRADLE_CACHE == psiFile.parent?.name))
}

internal fun isRelevantFile(file: PsiFile): Boolean {
  val fileType = file.fileType
  if (fileType === JavaFileType.INSTANCE || fileType === KotlinFileType.INSTANCE) return false
  if (isRelevantFileType(fileType)) return true

  return file.parent?.name?.startsWith(SdkConstants.FD_RES_RAW) ?: false
}

/** Checks if the file is relevant. May perform file I/O. */
@Slow
internal fun isRelevantFile(file: VirtualFile): Boolean {
  // VirtualFile.getFileType will try to read from the file the first time it's
  // called, so we try to avoid it as much as possible. Instead, we will just
  // try to infer the type based on the extension.
  val extension = file.extension
  if (StringUtil.isEmpty(extension)) return false
  if (JavaFileType.DEFAULT_EXTENSION == extension || KotlinFileType.EXTENSION == extension)
    return false
  if (XmlFileType.DEFAULT_EXTENSION == extension) return true
  if (SdkConstants.FN_ANDROID_MANIFEST_XML == file.name) return true
  if (AidlFileType.DEFAULT_ASSOCIATED_EXTENSION == extension) return true
  if (file.parent?.name?.startsWith(SdkConstants.FD_RES_RAW) == true) return true

  // Unable to determine based on filename, use the slow method.
  val fileType = file.fileType
  return /*fileType == AndroidRenderscriptFileType.INSTANCE ||*/ isRelevantFileType(fileType)
}

private fun isRelevantFileType(fileType: FileType): Boolean {
  // fail fast for vital file type
  if (fileType === JavaFileType.INSTANCE || fileType === KotlinFileType.INSTANCE) return false
  if (fileType === XmlFileType.INSTANCE) return true

  // TODO: ensure that only Android-compatible images are recognized.
  return fileType.isBinary &&
    (fileType === ImageFileTypeManager.getInstance().imageFileType ||
      fileType === FontFileType.INSTANCE)
}
