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

package com.android.tools.idea.backup.testing

import com.android.backup.BackupProgressListener
import com.android.backup.BackupResult
import com.android.tools.idea.backup.BackupManager
import com.android.tools.idea.backup.testing.FakeBackupManager.Action.BackupModal
import com.android.tools.idea.backup.testing.FakeBackupManager.Action.ChooseBackupFile
import com.android.tools.idea.run.RunConfigSection
import com.intellij.openapi.project.Project
import java.nio.file.Path

/** Test implementation of [BackupManager] */
internal class FakeBackupManager : BackupManager {

  val actions = mutableListOf<Action>()

  override fun backupModal(
    serialNumber: String,
    applicationId: String,
    backupFile: Path,
    source: BackupManager.Source,
    notify: Boolean,
  ): BackupResult {
    actions.add(BackupModal(serialNumber, applicationId, backupFile, notify))
    return BackupResult.Success
  }

  override fun restoreModal(
    serialNumber: String,
    backupFile: Path,
    source: BackupManager.Source,
    notify: Boolean,
  ): BackupResult {
    TODO("Not yet implemented")
  }

  override suspend fun restore(
    serialNumber: String,
    backupFile: Path,
    source: BackupManager.Source,
    listener: BackupProgressListener?,
    notify: Boolean,
  ): BackupResult {
    TODO("Not yet implemented")
  }

  override suspend fun chooseBackupFile(nameHint: String): Path? {
    actions.add(ChooseBackupFile(nameHint))
    return Path.of("$nameHint.backup")
  }

  override fun chooseRestoreFile(): Path? {
    TODO("Not yet implemented")
  }

  override suspend fun getApplicationId(backupFile: Path): String? {
    TODO("Not yet implemented")
  }

  override fun getRestoreRunConfigSection(project: Project): RunConfigSection {
    TODO("Not yet implemented")
  }

  sealed class Action {
    data class BackupModal(
      val serialNumber: String,
      val applicationId: String,
      val backupFile: Path,
      val notify: Boolean,
    ) : Action()

    data class ChooseBackupFile(val nameHint: String) : Action()
  }
}
