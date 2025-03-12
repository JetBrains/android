/*
 * Copyright (C) 2025 The Android Open Source Project
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

import com.android.annotations.TestOnly
import com.android.annotations.concurrency.UiThread
import com.android.backup.BackupMetadata
import com.android.backup.BackupProgressListener
import com.android.backup.BackupResult
import com.android.backup.BackupResult.Success
import com.android.tools.idea.backup.BackupManager
import com.android.tools.idea.run.RunConfigSection
import com.android.tools.idea.run.ValidationError
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.util.ui.EDT
import java.nio.file.Path
import javax.swing.JPanel

@TestOnly
class FakeBackupManager : BackupManager {
  var isDeviceSupported = true
  val showBackupDialogInvocations = mutableListOf<ShowBackupDialogInvocation>()
  val restoreModalInvocations = mutableListOf<RestoreModalInvocation>()

  @UiThread
  override fun showBackupDialog(
    serialNumber: String,
    applicationId: String,
    source: BackupManager.Source,
    notify: Boolean,
  ) {
    assert(EDT.isCurrentThreadEdt())
    showBackupDialogInvocations.add(
      ShowBackupDialogInvocation(serialNumber, applicationId, source, notify)
    )
  }

  @UiThread
  override fun restoreModal(
    serialNumber: String,
    backupFile: Path,
    source: BackupManager.Source,
    notify: Boolean,
  ): BackupResult {
    assert(EDT.isCurrentThreadEdt())
    restoreModalInvocations.add(RestoreModalInvocation(serialNumber, backupFile, source, notify))
    return Success
  }

  override suspend fun restore(
    serialNumber: String,
    backupFile: Path,
    source: BackupManager.Source,
    listener: BackupProgressListener?,
    notify: Boolean,
  ): BackupResult = Success

  override fun chooseRestoreFile(): Path? = Path.of("file.backup")

  override suspend fun getMetadata(backupFile: Path): BackupMetadata? = null

  override suspend fun getForegroundApplicationId(serialNumber: String) = "com.app"

  override suspend fun isInstalled(serialNumber: String, applicationId: String) = true

  override suspend fun isDeviceSupported(serialNumber: String) = isDeviceSupported

  override fun isAppSupported(applicationId: String) = true

  override fun getRestoreRunConfigSection(project: Project): RunConfigSection {
    return object : RunConfigSection {
      override fun getComponent(parentDisposable: Disposable) = JPanel()

      override fun resetFrom(runConfiguration: RunConfiguration) {}

      override fun applyTo(runConfiguration: RunConfiguration) {}

      override fun validate(runConfiguration: RunConfiguration): List<ValidationError> {
        return emptyList()
      }

      override fun updateBasedOnInstantState(instantAppDeploy: Boolean) {}
    }
  }

  data class ShowBackupDialogInvocation(
    val serialNumber: String,
    val applicationId: String,
    val source: BackupManager.Source,
    val notify: Boolean,
  )

  data class RestoreModalInvocation(
    val serialNumber: String,
    val backupFile: Path,
    val source: BackupManager.Source,
    val notify: Boolean,
  )
}
