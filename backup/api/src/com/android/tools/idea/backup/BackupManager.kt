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

import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.run.RunConfigSection
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
   */
  @UiThread fun backup(serialNumber: String, applicationId: String, backupFile: Path)

  /**
   * Backup an app to a local file
   *
   * @param serialNumber Serial number of a connected device
   * @param backupFile A path to write the backup data to
   */
  @UiThread fun restore(serialNumber: String, backupFile: Path)

  /** Display a file chooser dialog for saving a backup file */
  suspend fun chooseBackupFile(nameHint: String): Path?

  /** Display a file chooser dialog for loading a backup file */
  fun chooseRestoreFile(): Path?

  /**
   * Gets the application id of the associated app
   *
   * @param backupFile The path of a backup file to validate
   * @return The application id of the associated app
   * @throws Exception `backupFile` is not valid
   */
  suspend fun getApplicationId(backupFile: Path): String?

  /** Returns a new [RunConfigSection] object */
  fun getRestoreRunConfigSection(project: Project): RunConfigSection

  companion object {
    @JvmStatic
    fun getInstance(project: Project): BackupManager = project.getService(BackupManager::class.java)
  }
}
