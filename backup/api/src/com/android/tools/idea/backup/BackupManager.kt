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
import com.android.backup.BackupMetadata
import com.android.backup.BackupProgressListener
import com.android.backup.BackupResult
import com.android.tools.idea.run.RunConfigSection
import com.intellij.openapi.project.Project
import java.nio.file.Path

/** A project service for app backup/restore */
interface BackupManager {
  /** Where in the UI was the action invoked from */
  enum class Source {
    DEVICE_EXPLORER,
    PROJECT_VIEW,
    RUN_CONFIG,
    /** Correlate with `BackupAppAction.place` */
    BACKUP_APP_ACTION,
    /** Correlate with `BackupForegroundAppAction.place` */
    BACKUP_FOREGROUND_APP_ACTION,
    /** Correlate with `BackupAppAction.place` */
    RESTORE_APP_ACTION,
  }

  /**
   * Backup an app to a local file and show a progress bar
   *
   * @param serialNumber Serial number of a connected device
   * @param applicationId Application ID (package name) of the app
   * @param notify If true, will post a notification on completion
   */
  @UiThread
  fun showBackupDialog(
    serialNumber: String,
    applicationId: String,
    source: Source,
    notify: Boolean = true,
  )

  /**
   * Restore an app from a local file and show a progress dialog
   *
   * @param serialNumber Serial number of a connected device
   * @param backupFile A path to write the backup data to
   * @param notify If true, will post a notification on completion
   */
  @UiThread
  fun restoreModal(
    serialNumber: String,
    backupFile: Path,
    source: Source,
    notify: Boolean = true,
  ): BackupResult

  /**
   * Restore an app from a local file
   *
   * @param serialNumber Serial number of a connected device
   * @param backupFile A path to write the backup data to
   * @param listener A [BackupProgressListener] that gets called after every step
   * @param notify If true, will post a notification on completion
   */
  suspend fun restore(
    serialNumber: String,
    backupFile: Path,
    source: Source,
    listener: BackupProgressListener? = null,
    notify: Boolean = true,
  ): BackupResult

  /** Display a file chooser dialog for loading a backup file */
  @UiThread fun chooseRestoreFile(): Path?

  /**
   * Gets the application id of the associated app
   *
   * @param backupFile The path of a backup file to validate
   * @return The metadata from the backup file
   * @throws Exception `backupFile` is not valid
   */
  suspend fun getMetadata(backupFile: Path): BackupMetadata?

  /** Gets the application id of the foreground on the device with the serial number provided. */
  suspend fun getForegroundApplicationId(serialNumber: String): String

  /** Returns true is the application is installed on the device . */
  suspend fun isInstalled(serialNumber: String, applicationId: String): Boolean

  /** Returns true is the device supports Backup/Restore . */
  suspend fun isDeviceSupported(serialNumber: String): Boolean

  /** Returns a new [RunConfigSection] object */
  fun getRestoreRunConfigSection(project: Project): RunConfigSection

  // Returns true if the applicationId is supported
  fun isAppSupported(applicationId: String): Boolean

  companion object {
    const val NOTIFICATION_GROUP = "Backup"

    @JvmStatic
    fun getInstance(project: Project): BackupManager = project.getService(BackupManager::class.java)
  }
}
