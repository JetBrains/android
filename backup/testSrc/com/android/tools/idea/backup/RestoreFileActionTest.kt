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
package com.android.tools.idea.backup

import com.android.backup.BackupMetadata
import com.android.backup.BackupType
import com.android.flags.junit.FlagRule
import com.android.testutils.retryUntilPassing
import com.android.tools.idea.backup.testing.FakeActionHelper
import com.android.tools.idea.backup.testing.FakeDialogFactory
import com.android.tools.idea.backup.testing.FakeDialogFactory.DialogData
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.testing.ProjectServiceRule
import com.android.tools.idea.util.toVirtualFile
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.any
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

@RunWith(JUnit4::class)
class RestoreFileActionTest {

  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val mockBackupManager =
    mock<BackupManager>().apply {
      runBlocking {
        whenever(isInstalled(any(), any())).thenReturn(true)
        whenever(chooseRestoreFile()).thenReturn(Path.of("file.backup"))
        whenever(getMetadata(argWhere { x -> x.fileName.endsWith("file.backup") }))
          .thenReturn(BackupMetadata("com.app", BackupType.DEVICE_TO_DEVICE))
        whenever(
            getMetadata(argWhere { x -> x.fileName.endsWith("file_no_application_id.backup") })
          )
          .thenReturn(null)
      }
    }

  private val fakeDialogFactory = FakeDialogFactory()

  private val temporaryFolder =
    TemporaryFolder(TemporaryDirectory.generateTemporaryPath("").parent.toFile())

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      FlagRule(StudioFlags.BACKUP_ENABLED, true),
      ProjectServiceRule(projectRule, BackupManager::class.java, mockBackupManager),
      temporaryFolder,
    )

  @Test
  fun update_noFile() {
    val action = RestoreFileAction(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val event = testEvent(project)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun update_notBackupFileType() {
    val action = RestoreFileAction(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val notBackupFile = temporaryFolder.newFile("file").toVirtualFile()
    val event = testEvent(project, file = notBackupFile)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun update_backupFileType() {
    val action = RestoreFileAction(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val backupFile = temporaryFolder.newFile("file.backup").toVirtualFile()
    val event = testEvent(project, file = backupFile)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isEnabledAndVisible).isTrue()
  }

  @Test
  fun actionPerformed() {
    val actionHelper = FakeActionHelper("com.app", 1, "serial")
    val action = RestoreFileAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val backupFile = temporaryFolder.newFile("file.backup").toVirtualFile()!!
    val event = testEvent(project, "serial", backupFile)

    action.actionPerformed(event)

    runInEdtAndWait {}

    retryUntilPassing(3.seconds) {
      verifyBlocking(mockBackupManager) { getMetadata(backupFile.toNioPath()) }
      assertThat(fakeDialogFactory.dialogs).isEmpty()
    }
  }

  @Test
  fun actionPerformed_noApplicationId() {
    val actionHelper = FakeActionHelper(null, 1, "serial")
    val action = RestoreFileAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val backupFile = temporaryFolder.newFile("file.backup").toVirtualFile()!!
    val event = testEvent(project, "serial", backupFile)

    action.actionPerformed(event)

    runInEdtAndWait {}

    retryUntilPassing(3.seconds) {
      verifyNoInteractions(mockBackupManager)
      assertThat(fakeDialogFactory.dialogs)
        .containsExactly(DialogData("Cannot Restore App Data", "Incompatible run configuration"))
    }
  }

  @Test
  fun actionPerformed_invalidFileApplicationId() {
    val actionHelper = FakeActionHelper("com.app", 1, "serial")
    val action = RestoreFileAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val backupFile = temporaryFolder.newFile("file_no_application_id.backup").toVirtualFile()!!
    val event = testEvent(project, "serial", backupFile)

    action.actionPerformed(event)

    runInEdtAndWait {}

    retryUntilPassing(3.seconds) {
      verifyBlocking(mockBackupManager) { getMetadata(backupFile.toNioPath()) }
      verifyNoMoreInteractions(mockBackupManager)
      assertThat(fakeDialogFactory.dialogs)
        .containsExactly(DialogData("Cannot Restore App Data", "Backup file is invalid"))
    }
  }

  @Test
  fun actionPerformed_mismatchingApplicationId() {
    val actionHelper = FakeActionHelper("com.app.mismatching", 1, "serial")
    val action = RestoreFileAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val backupFile = temporaryFolder.newFile("file.backup").toVirtualFile()!!
    val event = testEvent(project, "serial", backupFile)

    action.actionPerformed(event)

    runInEdtAndWait {}

    retryUntilPassing(3.seconds) {
      verifyBlocking(mockBackupManager) { getMetadata(backupFile.toNioPath()) }
      verifyNoMoreInteractions(mockBackupManager)
      assertThat(fakeDialogFactory.dialogs)
        .containsExactly(
          DialogData(
            "Cannot Restore App Data",
            """Application id in file does not match run app: "com.app" != "com.app.mismatching"""",
          )
        )
    }
  }

  @Test
  fun actionPerformed_noTargets() {
    val actionHelper = FakeActionHelper("com.app", 0, "serial")
    val action = RestoreFileAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val backupFile = temporaryFolder.newFile("file.backup").toVirtualFile()!!
    val event = testEvent(project, "serial", backupFile)

    action.actionPerformed(event)

    runInEdtAndWait {}

    retryUntilPassing(3.seconds) {
      verifyBlocking(mockBackupManager) { getMetadata(backupFile.toNioPath()) }
      verifyNoMoreInteractions(mockBackupManager)
      assertThat(fakeDialogFactory.dialogs)
        .containsExactly(DialogData("Cannot Restore App Data", "Selected device is not running"))
    }
  }

  @Test
  fun actionPerformed_multipleTargets() {
    val actionHelper = FakeActionHelper("com.app", 2, "serial")
    val action = RestoreFileAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val backupFile = temporaryFolder.newFile("file.backup").toVirtualFile()!!
    val event = testEvent(project, "serial", backupFile)

    action.actionPerformed(event)

    runInEdtAndWait {}

    retryUntilPassing(3.seconds) {
      verifyBlocking(mockBackupManager) { getMetadata(backupFile.toNioPath()) }
      verifyNoMoreInteractions(mockBackupManager)
      assertThat(fakeDialogFactory.dialogs)
        .containsExactly(
          DialogData("Cannot Restore App Data", "Action is not supported for multiple devices")
        )
    }
  }

  @Test
  fun actionPerformed_noRunningTargets() {
    val actionHelper = FakeActionHelper("com.app", 1, null)
    val action = RestoreFileAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val backupFile = temporaryFolder.newFile("file.backup").toVirtualFile()!!
    val event = testEvent(project, "serial", backupFile)

    action.actionPerformed(event)

    runInEdtAndWait {}

    retryUntilPassing(3.seconds) {
      verifyBlocking(mockBackupManager) { getMetadata(backupFile.toNioPath()) }
      verifyNoMoreInteractions(mockBackupManager)
      assertThat(fakeDialogFactory.dialogs)
        .containsExactly(DialogData("Cannot Restore App Data", "Selected device is not running"))
    }
  }

  private fun testEvent(
    project: Project? = null,
    serialNumber: String? = null,
    file: VirtualFile? = null,
  ): AnActionEvent {
    val dataContext = SimpleDataContext.builder()
    project.let { dataContext.add(CommonDataKeys.PROJECT, project) }
    serialNumber.let { dataContext.add(SERIAL_NUMBER_KEY, serialNumber) }
    file.let { dataContext.add(CommonDataKeys.VIRTUAL_FILE, file) }
    return TestActionEvent.createTestEvent(dataContext.build())
  }
}
