/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.editors.manifest

import com.android.SdkConstants
import com.android.ide.common.blame.SourcePosition
import java.io.File

enum class ManifestXmlType {
  ANDROID_MANIFEST_XML,
  NAVIGATION_XML
}

/**
 * Manifest representation with additional metadata.
 *
 * Used primarily to hold the source library for a manifest, and to help with sorting.
 */
sealed class ManifestFileWithMetadata(val sortPriority: Int) : Comparable <ManifestFileWithMetadata> {
  abstract val file: File?
  abstract val isProjectFile: Boolean
  override fun compareTo(other: ManifestFileWithMetadata): Int {
    return compareValuesBy(
      this, other,
      {
        it.sortPriority
      },{
        // Project files first
        !it.isProjectFile
      },{
        when(it) {
          is ManifestXmlWithMetadata -> it.sourceLibrary
          else -> ""
        }
      },{
        when(it) {
          is ManifestXmlWithMetadata -> it.type
          // Anything else has lower priority over any of the enum values
          else -> Int.MAX_VALUE
        }
      },{
        it.file?.path?.contains(SdkConstants.EXPLODED_AAR)
      },{
        it.file?.path
      }
    )
  }
}

data class ManifestXmlWithMetadata(
  val type: ManifestXmlType,
  override val file: File,
  val sourceLibrary: String,
  override val isProjectFile: Boolean,
  val sourcePosition: SourcePosition
) : ManifestFileWithMetadata(sortPriority = 0)

abstract class InjectedFile : ManifestFileWithMetadata(sortPriority = 1)

object UnknownManifestFile : ManifestFileWithMetadata(sortPriority = 2) {
  override val file = null
  override val isProjectFile = false
}

