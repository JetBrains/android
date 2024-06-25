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
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.vfs.LocalFileSystem
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlinx.coroutines.withContext

private const val BACKUP_PATH_KEY = "Backup.Path"
private const val BACKUP_EXT = ".backup"

/** Implementation of [BackupManager] */
internal class BackupManagerImpl(private val project: Project) : BackupManager {
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

  override suspend fun chooseBackupFile(nameHint: String): Path? {
    val dialog =
      FileChooserFactory.getInstance()
        .createSaveFileDialog(
          FileSaverDescriptor(
            BackupBundle.message("backup.choose.backup.file.dialog.title"),
            "",
            BACKUP_EXT,
          ),
          project,
        )
    val path = dialog.save(getBackupPath(), nameHint)?.file?.toPath()
    if (path != null) {
      setBackupPath(path)
    }
    return path
  }

  override fun chooseRestoreFile(): Path? {
    val descriptor =
      FileChooserDescriptor(true, false, true, true, false, false)
        .withTitle(BackupBundle.message("backup.choose.restore.file.dialog.title"))
        .withFileFilter { it.name.endsWith(BACKUP_EXT) }
    return FileChooserFactory.getInstance()
      .createFileChooser(descriptor, project, null)
      .choose(project)
      .firstOrNull()
      ?.toNioPath()
      ?.normalize()
  }

  private suspend fun getBackupPath(): Path? {
    val properties = PropertiesComponent.getInstance(project)
    return withContext(AndroidDispatchers.diskIoThread) {
      when (val lastPath = properties.getValue(BACKUP_PATH_KEY)) {
        null -> project.guessProjectDir()?.toNioPath()
        else -> LocalFileSystem.getInstance().findFileByPath(lastPath)?.toNioPath()
      }
    }
  }

  private fun setBackupPath(path: Path) {
    PropertiesComponent.getInstance(project).setValue(BACKUP_PATH_KEY, path.pathString)
  }
}
