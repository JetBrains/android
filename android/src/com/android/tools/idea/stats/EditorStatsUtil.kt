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

import com.google.wireless.android.sdk.stats.EditorFileType
import com.google.wireless.android.sdk.stats.EditorFileType.GROOVY
import com.google.wireless.android.sdk.stats.EditorFileType.JAVA
import com.google.wireless.android.sdk.stats.EditorFileType.JSON
import com.google.wireless.android.sdk.stats.EditorFileType.KOTLIN
import com.google.wireless.android.sdk.stats.EditorFileType.KOTLIN_SCRIPT
import com.google.wireless.android.sdk.stats.EditorFileType.NATIVE
import com.google.wireless.android.sdk.stats.EditorFileType.PROPERTIES
import com.google.wireless.android.sdk.stats.EditorFileType.UNKNOWN
import com.google.wireless.android.sdk.stats.EditorFileType.XML
import com.intellij.openapi.vfs.VirtualFile

/** Computes the file type of [file] for analytics purposes. */
fun getEditorFileTypeForAnalytics(file: VirtualFile): EditorFileType = when (file.fileType.name) {
  // We use string literals here (rather than, e.g., JsonFileType.INSTANCE.name) to avoid unnecessary
  // dependencies on other plugins. Fortunately, these values are extremely unlikely to change.
  "JAVA" -> JAVA
  "Kotlin" -> if (file.extension == "kts") KOTLIN_SCRIPT else KOTLIN
  "XML" -> XML
  "Groovy" -> GROOVY
  "Properties" -> PROPERTIES
  "JSON" -> JSON
  "ObjectiveC" -> NATIVE // Derived from OCLanguage constructor.
  else -> UNKNOWN
}
