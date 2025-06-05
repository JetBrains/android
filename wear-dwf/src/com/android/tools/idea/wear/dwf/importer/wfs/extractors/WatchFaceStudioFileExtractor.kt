/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.wear.dwf.importer.wfs.extractors

import java.nio.file.Path
import kotlinx.coroutines.flow.Flow

/**
 * Interface for classes that can extract manifests and resources from files produced by
 * [Watch Face Studio](https://developer.samsung.com/watch-face-studio/overview.html).
 */
interface WatchFaceStudioFileExtractor {
  /** Extracts [ExtractedItem]s from the given [filePath]. */
  suspend fun extract(filePath: Path): Flow<ExtractedItem>
}

/**
 * Represents an item such as a manifest or a resource that has been extracted from a file
 * containing watch face data, such as an `.apk` or an `.aab` file.
 *
 * @see WatchFaceStudioFileExtractor
 */
sealed interface ExtractedItem {
  data class Manifest(val content: String) : ExtractedItem

  sealed interface Resource : ExtractedItem {
    val name: String
    val filePath: Path
  }

  data class StringResource(
    override val name: String,
    override val filePath: Path,
    val value: String,
  ) : Resource

  data class BinaryResource(
    override val name: String,
    override val filePath: Path,
    val binaryContent: ByteArray,
  ) : Resource {
    // https://www.jetbrains.com/help/inspectopedia/ArrayInDataClass.html
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (javaClass != other?.javaClass) return false

      other as BinaryResource

      if (name != other.name) return false
      if (filePath != other.filePath) return false
      if (!binaryContent.contentEquals(other.binaryContent)) return false

      return true
    }

    override fun hashCode(): Int {
      var result = name.hashCode()
      result = 31 * result + filePath.hashCode()
      result = 31 * result + binaryContent.contentHashCode()
      return result
    }
  }

  data class TextResource(
    override val name: String,
    override val filePath: Path,
    val text: String,
  ) : Resource
}
