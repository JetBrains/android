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

import com.android.backup.BackupResult
import com.android.backup.BackupService
import com.android.backup.ErrorCode
import com.android.tools.analytics.UsageTrackerRule
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.BACKUP_USAGE
import com.google.wireless.android.sdk.stats.BackupUsageEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.BackupEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.BackupEvent.Type.D2D
import com.google.wireless.android.sdk.stats.BackupUsageEvent.RestoreEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.Result.SUCCESS
import com.google.wireless.android.sdk.stats.BackupUsageEvent.Source
import com.google.wireless.android.sdk.stats.BackupUsageEvent.Source.DEVICE_EXPLORER
import com.google.wireless.android.sdk.stats.BackupUsageEvent.Source.RUN_MENU
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/** Tests for [BackupManagerImpl] */
@RunsInEdt
@RunWith(JUnit4::class)
internal class BackupManagerImplTest {
  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val usageTrackerRule = UsageTrackerRule()

  @get:Rule val rule = RuleChain(projectRule, usageTrackerRule, EdtRule())

  private val mockBackupService = mock<BackupService>()

  @Test
  fun backup_success(): Unit = runBlocking {
    val backupManagerImpl = BackupManagerImpl(project, mockBackupService)
    val serialNumber = "serial"
    val applicationId = "app"
    val backupFile = Path.of("file")
    whenever(mockBackupService.backup(eq(serialNumber), eq(applicationId), eq(backupFile), any()))
      .thenReturn(BackupResult.Success)

    backupManagerImpl.backupModal(
      serialNumber,
      applicationId,
      backupFile,
      BackupManager.Source.RUN_MENU,
      notify = true,
    )

    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(backupUsageEvent(D2D, RUN_MENU, SUCCESS))
  }

  @Test
  fun restore_success(): Unit = runBlocking {
    val backupManagerImpl = BackupManagerImpl(project, mockBackupService)
    val serialNumber = "serial"
    val backupFile = Path.of("file")
    whenever(mockBackupService.restore(eq(serialNumber), eq(backupFile), anyOrNull()))
      .thenReturn(BackupResult.Success)

    backupManagerImpl.restore(
      serialNumber,
      backupFile,
      BackupManager.Source.RUN_MENU,
      notify = true,
    )

    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(restoreUsageEvent(RUN_MENU, SUCCESS))
  }

  @Test
  fun backup_errors(): Unit = runBlocking {
    val errors =
      listOf(
        ErrorCode.CANNOT_ENABLE_BMGR to BackupUsageEvent.Result.CANNOT_ENABLE_BMGR,
        ErrorCode.TRANSPORT_NOT_SELECTED to BackupUsageEvent.Result.TRANSPORT_NOT_SELECTED,
        ErrorCode.GMSCORE_NOT_FOUND to BackupUsageEvent.Result.GMSCORE_NOT_FOUND,
        ErrorCode.GMSCORE_IS_TOO_OLD to BackupUsageEvent.Result.GMSCORE_IS_TOO_OLD,
        ErrorCode.BACKUP_FAILED to BackupUsageEvent.Result.BACKUP_FAILED,
        ErrorCode.UNEXPECTED_ERROR to BackupUsageEvent.Result.UNEXPECTED_ERROR,
      )

    errors.forEach { testBackupError(it.first, it.second) }
  }

  @Test
  fun restore_errors(): Unit = runBlocking {
    val errors =
      listOf(
        ErrorCode.CANNOT_ENABLE_BMGR to BackupUsageEvent.Result.CANNOT_ENABLE_BMGR,
        ErrorCode.TRANSPORT_NOT_SELECTED to BackupUsageEvent.Result.TRANSPORT_NOT_SELECTED,
        ErrorCode.GMSCORE_NOT_FOUND to BackupUsageEvent.Result.GMSCORE_NOT_FOUND,
        ErrorCode.GMSCORE_IS_TOO_OLD to BackupUsageEvent.Result.GMSCORE_IS_TOO_OLD,
        ErrorCode.RESTORE_FAILED to BackupUsageEvent.Result.RESTORE_FAILED,
        ErrorCode.INVALID_BACKUP_FILE to BackupUsageEvent.Result.INVALID_BACKUP_FILE,
        ErrorCode.UNEXPECTED_ERROR to BackupUsageEvent.Result.UNEXPECTED_ERROR,
      )

    errors.forEach { testRestoreError(it.first, it.second) }
  }

  private suspend fun testBackupError(
    errorCode: ErrorCode,
    trackingResult: BackupUsageEvent.Result,
  ) {
    val backupManagerImpl = BackupManagerImpl(project, mockBackupService)
    val serialNumber = "serial"
    val applicationId = "app"
    val backupFile = Path.of("file")
    whenever(mockBackupService.backup(eq(serialNumber), eq(applicationId), eq(backupFile), any()))
      .thenReturn(BackupResult.Error(errorCode, RuntimeException()))

    backupManagerImpl.backupModal(
      serialNumber,
      applicationId,
      backupFile,
      BackupManager.Source.DEVICE_EXPLORER,
      notify = true,
    )

    assertThat(usageTrackerRule.backupEvents().last())
      .isEqualTo(backupUsageEvent(D2D, DEVICE_EXPLORER, trackingResult))
  }

  private suspend fun testRestoreError(
    errorCode: ErrorCode,
    trackingResult: BackupUsageEvent.Result,
  ) {
    val backupManagerImpl = BackupManagerImpl(project, mockBackupService)
    val serialNumber = "serial"
    val backupFile = Path.of("file")
    whenever(mockBackupService.restore(eq(serialNumber), eq(backupFile), anyOrNull()))
      .thenReturn(BackupResult.Error(errorCode, RuntimeException()))

    backupManagerImpl.restore(
      serialNumber,
      backupFile,
      BackupManager.Source.DEVICE_EXPLORER,
      notify = true,
    )

    assertThat(usageTrackerRule.backupEvents().last())
      .isEqualTo(restoreUsageEvent(DEVICE_EXPLORER, trackingResult))
  }
}

private fun backupUsageEvent(
  @Suppress("SameParameterValue") type: BackupEvent.Type,
  source: Source,
  result: BackupUsageEvent.Result,
) =
  BackupUsageEvent.newBuilder()
    .setBackup(BackupEvent.newBuilder().setType(type).setSource(source).setResult(result))
    .build()

private fun restoreUsageEvent(source: Source, result: BackupUsageEvent.Result) =
  BackupUsageEvent.newBuilder()
    .setRestore(RestoreEvent.newBuilder().setSource(source).setResult(result))
    .build()

private fun UsageTrackerRule.backupEvents(): List<BackupUsageEvent> =
  usages.filter { it.studioEvent.kind == BACKUP_USAGE }.map { it.studioEvent.backupUsageEvent }
