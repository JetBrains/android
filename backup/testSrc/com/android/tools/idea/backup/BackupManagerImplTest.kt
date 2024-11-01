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

import com.android.backup.BackupException
import com.android.backup.BackupResult
import com.android.backup.BackupService
import com.android.backup.BackupType
import com.android.backup.BackupType.DEVICE_TO_DEVICE
import com.android.backup.ErrorCode
import com.android.backup.ErrorCode.GMSCORE_IS_TOO_OLD
import com.android.backup.ErrorCode.SUCCESS
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.backup.BackupManager.Source.RUN_CONFIG
import com.android.tools.idea.testing.NotificationRule
import com.android.tools.idea.testing.NotificationRule.NotificationInfo
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.BACKUP_USAGE
import com.google.wireless.android.sdk.stats.BackupUsageEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.BackupEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.RestoreEvent
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.notification.NotificationType.WARNING
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import java.nio.file.Path
import kotlin.io.path.pathString
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
  private val notificationRule = NotificationRule(projectRule)

  @get:Rule val rule = RuleChain(projectRule, usageTrackerRule, notificationRule, EdtRule())

  private val mockBackupService = mock<BackupService>()

  @Test
  fun backup_success(): Unit = runBlocking {
    val backupManagerImpl = BackupManagerImpl(project, mockBackupService)
    val serialNumber = "serial"
    val applicationId = "app"
    val backupFile = Path.of("file")
    whenever(
        mockBackupService.backup(
          eq(serialNumber),
          eq(applicationId),
          eq(DEVICE_TO_DEVICE),
          eq(backupFile),
          any(),
        )
      )
      .thenReturn(BackupResult.Success)

    backupManagerImpl.doBackup(serialNumber, applicationId, backupFile, RUN_CONFIG, notify = true)

    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(backupUsageEvent(DEVICE_TO_DEVICE, RUN_CONFIG, SUCCESS))
    assertThat(notificationRule.notifications).hasSize(1)
    notificationRule.notifications
      .first()
      .assert(
        title = "",
        "Backup completed successfully",
        INFORMATION,
        "ShowPostBackupDialogAction",
      )
  }

  @Test
  fun restore_success_absolutePath(): Unit = runBlocking {
    val backupManagerImpl = BackupManagerImpl(project, mockBackupService)
    val serialNumber = "serial"
    val backupFile = Path.of(if (SystemInfo.isWindows) """c:\path\file""" else "/path/file")
    whenever(mockBackupService.restore(eq(serialNumber), eq(backupFile), anyOrNull()))
      .thenReturn(BackupResult.Success)

    backupManagerImpl.restore(serialNumber, backupFile, RUN_CONFIG, notify = true)

    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(restoreUsageEvent(RUN_CONFIG, SUCCESS))
  }

  @Test
  fun restore_success_relativePath(): Unit = runBlocking {
    val backupManagerImpl = BackupManagerImpl(project, mockBackupService)
    val serialNumber = "serial"
    val backupFile = Path.of("file")
    whenever(
        mockBackupService.restore(
          eq(serialNumber),
          eq(Path.of(project.basePath ?: "", backupFile.pathString)),
          anyOrNull(),
        )
      )
      .thenReturn(BackupResult.Success)

    backupManagerImpl.restore(serialNumber, backupFile, RUN_CONFIG, notify = true)

    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(restoreUsageEvent(RUN_CONFIG, SUCCESS))
  }

  @Test
  fun gmsCoreNotUpdated(): Unit = runBlocking {
    val backupManagerImpl = BackupManagerImpl(project, mockBackupService)
    val serialNumber = "serial"
    val backupFile = Path.of("file")
    whenever(mockBackupService.restore(eq(serialNumber), any(), anyOrNull()))
      .thenReturn(
        BackupResult.Error(GMSCORE_IS_TOO_OLD, BackupException(GMSCORE_IS_TOO_OLD, "Error"))
      )

    backupManagerImpl.restore(serialNumber, backupFile, RUN_CONFIG, notify = true)

    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(restoreUsageEvent(RUN_CONFIG, GMSCORE_IS_TOO_OLD))
    assertThat(notificationRule.notifications).hasSize(1)
    notificationRule.notifications
      .first()
      .assert("Restore Failed", "Error", WARNING, "ShowExceptionAction", "UpdateGmsAction")
  }

  private fun NotificationInfo.assert(
    title: String,
    text: String,
    type: NotificationType,
    vararg actions: String,
  ) {
    assertThat(this.groupId).isEqualTo("Backup")
    assertThat(this.icon).isNull()
    assertThat(this.important).isTrue()
    assertThat(this.subtitle).isNull()

    assertThat(this.title).isEqualTo(title)
    assertThat(this.content).isEqualTo(text)
    assertThat(this.type).isEqualTo(type)
    assertThat(this.actions.map { it::class.java.simpleName }).isEqualTo(actions.asList())
  }
}

@Suppress("SameParameterValue")
private fun backupUsageEvent(type: BackupType, source: BackupManager.Source, result: ErrorCode) =
  BackupUsageEvent.newBuilder()
    .setBackup(
      BackupEvent.newBuilder()
        .setTypeValue(type.ordinal)
        .setSourceValue(source.ordinal)
        .setResultValue(result.ordinal)
    )
    .build()

@Suppress("SameParameterValue")
private fun restoreUsageEvent(source: BackupManager.Source, errorCode: ErrorCode) =
  BackupUsageEvent.newBuilder()
    .setRestore(
      RestoreEvent.newBuilder().setSourceValue(source.ordinal).setResultValue(errorCode.ordinal)
    )
    .build()

private fun UsageTrackerRule.backupEvents(): List<BackupUsageEvent> =
  usages.filter { it.studioEvent.kind == BACKUP_USAGE }.map { it.studioEvent.backupUsageEvent }
