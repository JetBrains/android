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
package com.android.tools.idea.device.explorer.files

import com.android.annotations.concurrency.AnyThread
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import java.nio.file.Path

object DeviceExplorerFilesUtils {
  /**
   * Creates a [VirtualFile] corresponding to the [Path] passed as argument.
   */
  @AnyThread
  suspend fun findFile(localPath: Path): VirtualFile {
    // We run this operation using invokeLater because we need to refresh a VirtualFile instance
    // this has to be done in a write-safe context.
    // See https://github.com/JetBrains/intellij-community/commit/10c0c11281b875e64c31186eac20fc28ba3fc37a
    return withWriteSafeContextWithCurrentModality {
      // findFileByIoFile should be called from the write thread, in a write-safe context
      VfsUtil.findFileByIoFile(localPath.toFile(), true)
          ?: throw RuntimeException("Unable to locate file \"$localPath\"")
    }
  }
}