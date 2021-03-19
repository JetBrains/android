/*
 * Copyright (C) 2021 The Android Open Source Project
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

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Files

private fun lastModifiedTimeFromDisk(vFile: VirtualFile): Long = try {
  Files.getLastModifiedTime(vFile.toNioPath()).toMillis()
} catch (_: UnsupportedOperationException) {
  // If the Virtual File is not backed by an actual file, always return 0. Only the virtual file
  // elements will be used in that case.
  0L
}

/**
 * Class that allows storing a snapshot of the last modification of a VirtualFile.
 */
@VisibleForTesting // internal
class ClassModificationTimestamp(private val vFileTimestamp: Long,
                                 private val vFileLength: Long,
                                 private val fileTimestamp: Long,
                                 private val diskTimeStampProvider: (VirtualFile) -> Long) {
  /**
   * Returns false if the given file does not match the recorded information for this [ClassModificationTimestamp].
   */
  fun isUpToDate(vFile: VirtualFile): Boolean {
    // We first check the VirtualFile. If everything matches, it could be that we are seeing a cached version that
    // has not been updated yet so we need to fallback to checking the file.
    // We leave lastModifiedTimeFromDisk to be the last condition as it will be the slowest check.
    if (!vFile.isValid
        || vFile.timeStamp != vFileTimestamp
        || vFile.length != vFileLength
        || diskTimeStampProvider(vFile) != fileTimestamp) return false
    return true
  }

  companion object {
    @TestOnly
    @JvmStatic
    fun fromVirtualFileForTest(vFile: VirtualFile, diskTimeStampProvider: (VirtualFile) -> Long): ClassModificationTimestamp {
      return ClassModificationTimestamp(vFile.timeStamp, vFile.length, diskTimeStampProvider(vFile), diskTimeStampProvider)
    }

    /**
     * Returns a [ClassModificationTimestamp] from a virtual file.
     */
    @JvmStatic
    fun fromVirtualFile(vFile: VirtualFile): ClassModificationTimestamp {
      return ClassModificationTimestamp(vFile.timeStamp, vFile.length, lastModifiedTimeFromDisk(vFile), ::lastModifiedTimeFromDisk)
    }
  }
}