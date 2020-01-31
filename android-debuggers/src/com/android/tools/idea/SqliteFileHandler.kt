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
package com.android.tools.idea
import com.android.annotations.NonNull
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.idea.deviceExplorer.FileHandler
import com.android.tools.idea.editors.sqlite.SqliteFileType
import com.intellij.openapi.vfs.VirtualFile

/**
 * Picks additional files to download when opening a SQLite database.
 * Room uses write-ahead-log by default on API 16+. Therefore for databases created by Room downloading the .db file is not enough,
 * we also need to download the .db-shm and .db-wal.
 */
class SqliteFileHandler : FileHandler {
  @WorkerThread
  @NonNull
  override fun getAdditionalDevicePaths(@NonNull deviceFilePath: String, @NonNull localFile: VirtualFile): List<String> {
    return if (localFile.fileType == SqliteFileType) {
      listOf("$deviceFilePath-shm", "$deviceFilePath-wal")
    } else {
      emptyList()
    }
  }
}