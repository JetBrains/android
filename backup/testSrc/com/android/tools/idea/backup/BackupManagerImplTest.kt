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
import com.android.backup.BackupResult.Success
import com.android.backup.BackupResult.WithoutAppData
import com.android.backup.BackupService
import com.android.backup.BackupType
import com.android.backup.BackupType.CLOUD
import com.android.backup.BackupType.DEVICE_TO_DEVICE
import com.android.backup.ErrorCode
import com.android.backup.ErrorCode.GMSCORE_IS_TOO_OLD
import com.android.backup.ErrorCode.SUCCESS
import com.android.backup.testing.BackupFileHelper
import com.android.backup.testing.FakeAdbServices.CommandOverride.Output
import com.android.backup.testing.FakeAdbServicesFactory
import com.android.tools.adtui.swing.HeadlessDialogRule
import com.android.tools.adtui.swing.createModalDialogAndInteractWithIt
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.backup.BackupManager.Source.RUN_CONFIG
import com.android.tools.idea.backup.testing.FakeDialogFactory
import com.android.tools.idea.backup.testing.FakeDialogFactory.DialogData
import com.android.tools.idea.backup.testing.clickOk
import com.android.tools.idea.backup.testing.findComponent
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.testing.NotificationRule
import com.android.tools.idea.testing.NotificationRule.NotificationInfo
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent.EventKind.BACKUP_USAGE
import com.google.wireless.android.sdk.stats.BackupUsageEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.BackupEvent
import com.google.wireless.android.sdk.stats.BackupUsageEvent.RestoreEvent
import com.intellij.execution.ExecutionManager
import com.intellij.execution.process.ProcessHandler
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationType.INFORMATION
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.replaceService
import com.intellij.ui.TextAccessor
import java.nio.file.Path
import kotlin.io.path.deleteExisting
import kotlin.io.path.deleteIfExists
import kotlin.io.path.notExists
import kotlin.io.path.relativeTo
import kotlin.test.fail
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

private const val DUMPSYS_GMSCORE_CMD = "dumpsys package com.google.android.gms"

/** Tests for [BackupManagerImpl] */
@RunsInEdt
@RunWith(JUnit4::class)
internal class BackupManagerImplTest {
  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val temporaryFolder =
    TemporaryFolder(TemporaryDirectory.generateTemporaryPath("").parent.toFile())
  private val backupFileHelper = BackupFileHelper(temporaryFolder)

  private val usageTrackerRule = UsageTrackerRule()
  private val notificationRule = NotificationRule(projectRule)
  private val disposableRule = DisposableRule()

  private val mockVirtualFileManager = mock<VirtualFileManager>()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      usageTrackerRule,
      notificationRule,
      HeadlessDialogRule(),
      temporaryFolder,
      disposableRule,
      EdtRule(),
    )

  private val fakeDialogFactory = FakeDialogFactory()

  @Test
  fun backup_success(): Unit = runBlocking {
    val backupFile =
      project.basePath?.let { Path.of(it) }?.resolve("file.backup")
        ?: fail("Project base path unavailable")
    backupFile.deleteIfExists()
    val backupService = BackupService.getInstance(FakeAdbServicesFactory("app3"))
    project.replaceService(
      ProjectAppsProvider::class.java,
      object : ProjectAppsProvider {
        override fun getApplicationIds(): Set<String> {
          return setOf("app1", "app2", "app3")
        }
      },
      disposableRule.disposable,
    )
    val backupManagerImpl =
      BackupManagerImpl(project, backupService, fakeDialogFactory, mockVirtualFileManager)
    val serialNumber = "serial"

    createModalDialogAndInteractWithIt({
      backupManagerImpl.showBackupDialog(
        serialNumber,
        "app2",
        emptyList(),
        RUN_CONFIG,
        notify = true,
        isBackupEnabled = true,
      )
    }) { dialogWrapper ->
      val dialog = dialogWrapper as BackupDialog
      val applicationIdComboBox = dialog.findComponent<ComboBox<String>>("applicationIdComboBox")
      val typeComboBox = dialog.findComponent<ComboBox<BackupType>>("typeComboBox")
      val fileTextField = dialog.findComponent<TextAccessor>("fileTextField")

      applicationIdComboBox.item = "app3"
      typeComboBox.item = CLOUD
      fileTextField.text = "file.backup"
      dialog.clickOk()
    }

    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(backupUsageEvent(CLOUD, RUN_CONFIG, "SUCCESS"))
    assertThat(notificationRule.notifications).hasSize(1)
    notificationRule.notifications
      .first()
      .assert(
        title = "",
        "Backup completed successfully",
        INFORMATION,
        "ShowPostBackupDialogAction",
      )
    assertThat(fakeDialogFactory.dialogs).isEmpty()
    verify(mockVirtualFileManager).refreshAndFindFileByNioPath(backupFile)
    backupFile.deleteExisting()
  }

  @Test
  fun backup_detachesFromDebuggedApp(): Unit = runBlocking {
    val backupFile =
      project.basePath?.let { Path.of(it) }?.resolve("file.backup")
        ?: fail("Project base path unavailable")
    backupFile.deleteIfExists()
    backupFile.toFile().deleteOnExit()
    val backupService = BackupService.getInstance(FakeAdbServicesFactory("com.app"))
    val backupManagerImpl =
      BackupManagerImpl(project, backupService, fakeDialogFactory, mockVirtualFileManager)
    val mockProcessHandler =
      mock<ProcessHandler>().apply {
        val sessionInfo = AndroidSessionInfo.create(this, emptyList(), "com.app")
        whenever(getUserData(AndroidSessionInfo.KEY)).thenReturn(sessionInfo)
      }
    val mockExecutionManager =
      mock<ExecutionManager>().apply {
        whenever(getRunningProcesses()).thenReturn(arrayOf(mockProcessHandler))
      }
    project.replaceService(
      ExecutionManager::class.java,
      mockExecutionManager,
      disposableRule.disposable,
    )

    backupManagerImpl.doBackup("serial", "com.app", CLOUD, backupFile, RUN_CONFIG, false)

    verify(mockProcessHandler).detachProcess()
  }

  @Test
  fun backup_backupDisabled_withAuth(): Unit = runBlocking {
    val backupFile =
      project.basePath?.let { Path.of(it) }?.resolve("file.backup")
        ?: fail("Project base path unavailable")
    backupFile.deleteIfExists()
    val backupService =
      BackupService.getInstance(
        FakeAdbServicesFactory("app3") {
          it.addCommandOverride(Output("dumpsys package app3", ""))
          it.addContentOverride(
            "content://com.google.android.gms.fileprovider/backup_testing_flows/auth_backup",
            "valid",
          )
        }
      )
    project.replaceService(
      ProjectAppsProvider::class.java,
      object : ProjectAppsProvider {
        override fun getApplicationIds(): Set<String> {
          return setOf("app3")
        }
      },
      disposableRule.disposable,
    )
    val backupManagerImpl =
      BackupManagerImpl(project, backupService, fakeDialogFactory, mockVirtualFileManager)
    val serialNumber = "serial"

    val result =
      backupManagerImpl.doBackup(
        serialNumber,
        "app3",
        DEVICE_TO_DEVICE,
        backupFile,
        RUN_CONFIG,
        true,
      )

    assertThat(result).isEqualTo(WithoutAppData)
    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(backupUsageEvent(DEVICE_TO_DEVICE, RUN_CONFIG, "WithoutAppData"))
    assertThat(notificationRule.notifications).hasSize(1)
    notificationRule.notifications
      .first()
      .assert(
        title = "Backup completed successfully",
        text =
          "Only Restore Keys were backed up. App-data was not backed up since allowBackup property is false.",
        INFORMATION,
        "ShowPostBackupDialogAction",
        "BackupDisabledLearnMoreAction",
      )
    assertThat(fakeDialogFactory.dialogs).isEmpty()
    verify(mockVirtualFileManager).refreshAndFindFileByNioPath(backupFile)
    backupFile.deleteExisting()
  }

  @Test
  fun backup_backupDisabled_withoutAuth(): Unit = runBlocking {
    val backupFile =
      project.basePath?.let { Path.of(it) }?.resolve("file.backup")
        ?: fail("Project base path unavailable")
    backupFile.deleteIfExists()
    val backupService =
      BackupService.getInstance(
        FakeAdbServicesFactory("app3") {
          it.addCommandOverride(Output("dumpsys package app3", ""))
          it.addContentOverride(
            "content://com.google.android.gms.fileprovider/backup_testing_flows/auth_backup",
            "",
          )
        }
      )
    project.replaceService(
      ProjectAppsProvider::class.java,
      object : ProjectAppsProvider {
        override fun getApplicationIds(): Set<String> {
          return setOf("app3")
        }
      },
      disposableRule.disposable,
    )
    val backupManagerImpl =
      BackupManagerImpl(project, backupService, fakeDialogFactory, mockVirtualFileManager)
    val serialNumber = "serial"

    val result =
      backupManagerImpl.doBackup(
        serialNumber,
        "app3",
        DEVICE_TO_DEVICE,
        backupFile,
        RUN_CONFIG,
        true,
      )

    assertThat(result).isInstanceOf(BackupResult.Error::class.java)
    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(backupUsageEvent(DEVICE_TO_DEVICE, RUN_CONFIG, "BACKUP_NOT_ENABLED"))
    assertThat(notificationRule.notifications).isEmpty()
    assertThat(fakeDialogFactory.dialogs)
      .containsExactly(
        DialogData(
          "Backup Failed",
          "No data was generated in backup since allowBackup property is false",
          listOf("Learn More"),
        )
      )
    assertThat(backupFile.notExists()).isTrue()
  }

  @Test
  fun restore_success_absolutePath(): Unit = runBlocking {
    val backupService = BackupService.getInstance(FakeAdbServicesFactory("com.app"))
    val backupManagerImpl = BackupManagerImpl(project, backupService, fakeDialogFactory)
    val serialNumber = "serial"
    val backupFile = backupFileHelper.createBackupFile("com.app", "11223344556677889900", CLOUD)

    val result = backupManagerImpl.restore(serialNumber, backupFile, RUN_CONFIG, notify = true)

    assertThat(result).isEqualTo(Success)
    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(restoreUsageEvent(RUN_CONFIG, SUCCESS))
  }

  @Test
  fun restore_success_relativePath(): Unit = runBlocking {
    val backupService = BackupService.getInstance(FakeAdbServicesFactory("com.app"))
    val projectPath = project.basePath?.let { Path.of(it) } ?: fail("Project base path unavailable")
    val backupManagerImpl = BackupManagerImpl(project, backupService, fakeDialogFactory)
    val serialNumber = "serial"
    val backupFile = backupFileHelper.createBackupFile("com.app", "11223344556677889900", CLOUD)
    val relativePath = backupFile.relativeTo(projectPath)

    val result = backupManagerImpl.restore(serialNumber, relativePath, RUN_CONFIG, notify = true)

    assertThat(result).isEqualTo(Success)
    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(restoreUsageEvent(RUN_CONFIG, SUCCESS))
  }

  @Test
  fun gmsCoreNotUpdated(): Unit = runBlocking {
    val backupService =
      BackupService.getInstance(
        FakeAdbServicesFactory("com.app") {
          it.addCommandOverride(
            Output(
              DUMPSYS_GMSCORE_CMD,
              """
          Packages:
              versionCode=50 minSdk=31 targetSdk=34
        """
                .trimIndent(),
            )
          )
        }
      )
    val backupManagerImpl = BackupManagerImpl(project, backupService, fakeDialogFactory)
    val serialNumber = "serial"
    val backupFile = backupFileHelper.createBackupFile("com.app", "11223344556677889900", CLOUD)

    backupManagerImpl.restore(serialNumber, backupFile, RUN_CONFIG, notify = true)

    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(restoreUsageEvent(RUN_CONFIG, GMSCORE_IS_TOO_OLD))
    assertThat(notificationRule.notifications).isEmpty()
    assertThat(fakeDialogFactory.dialogs)
      .containsExactly(
        DialogData(
          "Restore Failed",
          "Google Services version is too old (50).  Min version is 100",
          listOf("Show Full Error", "Open Play Store"),
        )
      )
  }

  @Test
  fun gmsCoreNotUpdated_noPlayStore(): Unit = runBlocking {
    val backupService =
      BackupService.getInstance(
        FakeAdbServicesFactory("com.app") {
          it.addCommandOverride(
            Output(
              DUMPSYS_GMSCORE_CMD,
              """
                Packages:
                    versionCode=50 minSdk=31 targetSdk=34
              """
                .trimIndent(),
            )
          )
          it.addCommandOverride(
            Output(
              "pm resolve-activity market://details?id=com.android.vending",
              "No activity found\n",
            )
          )
        }
      )
    val backupManagerImpl = BackupManagerImpl(project, backupService, fakeDialogFactory)
    val serialNumber = "serial"
    val backupFile = backupFileHelper.createBackupFile("com.app", "11223344556677889900", CLOUD)

    backupManagerImpl.restore(serialNumber, backupFile, RUN_CONFIG, notify = true)

    assertThat(usageTrackerRule.backupEvents())
      .containsExactly(restoreUsageEvent(RUN_CONFIG, GMSCORE_IS_TOO_OLD))
    assertThat(notificationRule.notifications).isEmpty()
    assertThat(fakeDialogFactory.dialogs)
      .containsExactly(
        DialogData(
          "Restore Failed",
          "Google Services version is too old (50).  Min version is 100",
          listOf("Show Full Error"),
        )
      )
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
private fun backupUsageEvent(type: BackupType, source: BackupManager.Source, result: String) =
  BackupUsageEvent.newBuilder()
    .setBackup(
      BackupEvent.newBuilder()
        .setTypeString(type.name)
        .setSourceString(source.name)
        .setResultString(result)
    )
    .build()

@Suppress("SameParameterValue")
private fun restoreUsageEvent(source: BackupManager.Source, errorCode: ErrorCode) =
  BackupUsageEvent.newBuilder()
    .setRestore(
      RestoreEvent.newBuilder().setSourceString(source.name).setResultString(errorCode.name)
    )
    .build()

private fun UsageTrackerRule.backupEvents(): List<BackupUsageEvent> =
  usages.filter { it.studioEvent.kind == BACKUP_USAGE }.map { it.studioEvent.backupUsageEvent }
