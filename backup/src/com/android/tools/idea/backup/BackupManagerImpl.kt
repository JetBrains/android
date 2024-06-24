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

import com.android.backup.BackupHandler
import com.android.backup.BackupProgressListener
import com.android.backup.RestoreHandler
import com.android.tools.environment.Logger
import com.android.tools.idea.adblib.AdbLibService
import com.intellij.openapi.project.Project
import java.nio.file.Path

/** Implementation of [BackupManager] */
internal class BackupManagerImpl(project: Project) : BackupManager {
  private val adbSession = AdbLibService.getSession(project)
  private val logger: Logger = Logger.getInstance(this::class.java)

  override suspend fun backup(
    serialNumber: String,
    applicationId: String,
    backupFile: Path,
    progressListener: BackupProgressListener?,
  ) {
    logger.debug("Backing up '$applicationId' from $backupFile on '${serialNumber}'")
    BackupHandler(adbSession, serialNumber, logger, progressListener, backupFile, applicationId)
      .backup()
  }

  override suspend fun restore(
    serialNumber: String,
    backupFile: Path,
    progressListener: BackupProgressListener?,
  ) {
    logger.debug("Restoring from $backupFile on '${serialNumber}'")
    RestoreHandler(adbSession, logger, serialNumber, progressListener, backupFile).restore()
  }
}
