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

package com.android.tools.idea.backup

import com.android.backup.BackupProgressListener
import com.intellij.openapi.project.Project
import java.nio.file.Path

/** A project service for app backup/restore */
interface BackupManager {

  /**
   * Backup an app to a local file
   *
   * @param serialNumber Serial number of a connected device
   * @param applicationId Application ID (package name) of the app
   * @param backupFile A path to write the backup data to
   * @param progressListener An optional listener to report progress to the UI
   */
  suspend fun backup(
    serialNumber: String,
    applicationId: String,
    backupFile: Path,
    progressListener: BackupProgressListener? = null,
  )

  /**
   * Backup an app to a local file
   *
   * @param serialNumber Serial number of a connected device
   * @param backupFile A path to write the backup data to
   * @param progressListener An optional listener to report progress to the UI
   */
  suspend fun restore(
    serialNumber: String,
    backupFile: Path,
    progressListener: BackupProgressListener? = null,
  )

  /** Display a file chooser dialog for saving a backup file */
  suspend fun chooseBackupFile(nameHint: String): Path?

  /** Display a file chooser dialog for loading a backup file */
  fun chooseRestoreFile(): Path?

  companion object {
    fun getInstance(project: Project): BackupManager = project.getService(BackupManager::class.java)
  }
}
