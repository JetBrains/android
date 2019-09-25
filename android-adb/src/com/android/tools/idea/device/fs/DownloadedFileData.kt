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
package com.android.tools.idea.device.fs

import com.intellij.openapi.vfs.VirtualFile

/**
 * Contains information about a downloaded file.
 */
data class DownloadedFileData(
  /** DeviceFileId] corresponding to the downloaded file. */
  val deviceFileId: DeviceFileId,

  /** [VirtualFile] corresponding to the downloaded file. */
  val virtualFile: VirtualFile,

  /** Additional [VirtualFile]s downloaded are contained in this list.
   * Some files, when downloaded, trigger the download of additional files
   * @see [com.android.tools.idea.deviceExplorer.FileHandler.getAdditionalDevicePaths]. */
  val additionalFiles: List<VirtualFile>
)