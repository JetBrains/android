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
@file:JvmName("EditorStatsUtil")

package com.android.tools.idea.stats

import com.android.SdkConstants.ANDROID_MANIFEST_XML
import com.android.annotations.concurrency.WorkerThread
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceFolderType.ANIM
import com.android.resources.ResourceFolderType.ANIMATOR
import com.android.resources.ResourceFolderType.COLOR
import com.android.resources.ResourceFolderType.DRAWABLE
import com.android.resources.ResourceFolderType.FONT
import com.android.resources.ResourceFolderType.INTERPOLATOR
import com.android.resources.ResourceFolderType.LAYOUT
import com.android.resources.ResourceFolderType.MENU
import com.android.resources.ResourceFolderType.MIPMAP
import com.android.resources.ResourceFolderType.NAVIGATION
import com.android.resources.ResourceFolderType.RAW
import com.android.resources.ResourceFolderType.TRANSITION
import com.android.resources.ResourceFolderType.VALUES
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.projectsystem.getModuleSystem
import com.android.tools.idea.res.getFolderType
import com.google.wireless.android.sdk.stats.EditorFileType
import com.google.wireless.android.sdk.stats.EditorFileType.GROOVY
import com.google.wireless.android.sdk.stats.EditorFileType.JAVA
import com.google.wireless.android.sdk.stats.EditorFileType.JSON
import com.google.wireless.android.sdk.stats.EditorFileType.KOTLIN
import com.google.wireless.android.sdk.stats.EditorFileType.KOTLIN_COMPOSE
import com.google.wireless.android.sdk.stats.EditorFileType.KOTLIN_SCRIPT
import com.google.wireless.android.sdk.stats.EditorFileType.NATIVE
import com.google.wireless.android.sdk.stats.EditorFileType.PROPERTIES
import com.google.wireless.android.sdk.stats.EditorFileType.UNKNOWN
import com.google.wireless.android.sdk.stats.EditorFileType.XML
import com.google.wireless.android.sdk.stats.EditorFileType.XML_MANIFEST
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_ANIM
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_ANIMATOR
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_COLOR
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_DRAWABLE
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_FONT
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_INTERPOLATOR
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_LAYOUT
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_MENU
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_MIPMAP
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_NAVIGATION
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_RAW
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_TRANSITION
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_VALUES
import com.google.wireless.android.sdk.stats.EditorFileType.XML_RES_XML
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.withContext

/** Computes the file type of [file] for analytics purposes. */
suspend fun getEditorFileTypeForAnalytics(file: VirtualFile, project: Project?): EditorFileType =
  when (file.fileType.name) {
    // We use string literals here (rather than, e.g., JsonFileType.INSTANCE.name) to avoid unnecessary
    // dependencies on other plugins. Fortunately, these values are extremely unlikely to change.
    "JAVA" -> JAVA
    "Kotlin" -> when {
      file.extension == "kts" -> KOTLIN_SCRIPT
      withContext(workerThread) { isComposeEnabled(file, project) } -> KOTLIN_COMPOSE
      else -> KOTLIN
    }

    "Groovy" -> GROOVY
    "Properties" -> PROPERTIES
    "JSON" -> JSON
    "ObjectiveC" -> NATIVE // Derived from OCLanguage constructor.
    "XML" -> when (getFolderType(file)) { // We split XML files by resource kind.
      ANIM -> XML_RES_ANIM
      ANIMATOR -> XML_RES_ANIMATOR
      COLOR -> XML_RES_COLOR
      DRAWABLE -> XML_RES_DRAWABLE
      FONT -> XML_RES_FONT
      INTERPOLATOR -> XML_RES_INTERPOLATOR
      LAYOUT -> XML_RES_LAYOUT
      MENU -> XML_RES_MENU
      MIPMAP -> XML_RES_MIPMAP
      NAVIGATION -> XML_RES_NAVIGATION
      RAW -> XML_RES_RAW
      TRANSITION -> XML_RES_TRANSITION
      VALUES -> XML_RES_VALUES
      ResourceFolderType.XML -> XML_RES_XML
      null -> if (file.name == ANDROID_MANIFEST_XML) XML_MANIFEST else XML
    }

    else -> UNKNOWN
  }

/**
 * This method is not expected to be slow, but it's possible the call to find the file's module could take longer in some circumstances. As
 * such, it's marked with [WorkerThread] to make sure there are no long calls on the UI thread.
 */
@WorkerThread
private fun isComposeEnabled(file: VirtualFile, project: Project?): Boolean {
  if (project == null) return false
  val module = ModuleUtilCore.findModuleForFile(file, project) ?: return false
  return module.getModuleSystem().usesCompose
}
