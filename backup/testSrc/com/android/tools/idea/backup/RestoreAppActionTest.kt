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

import com.android.flags.junit.FlagRule
import com.android.tools.idea.backup.BackupManager.Source.RESTORE_APP_ACTION
import com.android.tools.idea.backup.RestoreAppAction.Config
import com.android.tools.idea.backup.testing.FakeActionHelper
import com.android.tools.idea.backup.testing.FakeBackupManager
import com.android.tools.idea.backup.testing.FakeBackupManager.RestoreModalInvocation
import com.android.tools.idea.backup.testing.FakeDialogFactory
import com.android.tools.idea.backup.testing.FakeDialogFactory.DialogData
import com.android.tools.idea.backup.testing.waitForRestoreInvocations
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.SERIAL_NUMBER_KEY
import com.android.tools.idea.testing.ProjectServiceRule
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.TemporaryDirectory
import com.intellij.testFramework.TestActionEvent
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RestoreAppActionTest {

  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val fakeBackupManager = FakeBackupManager()
  private val fakeDialogFactory = FakeDialogFactory()

  private val temporaryFolder =
    TemporaryFolder(TemporaryDirectory.generateTemporaryPath("").parent.toFile())

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      FlagRule(StudioFlags.BACKUP_ENABLED, true),
      ProjectServiceRule(projectRule, BackupManager::class.java, fakeBackupManager),
      temporaryFolder,
    )

  @Test
  fun update() {
    val action = RestoreAppAction(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val event = testEvent(project)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isEnabledAndVisible).isTrue()
  }

  @Test
  fun update_flagDisabled() {
    StudioFlags.BACKUP_ENABLED.override(false)
    val action = RestoreAppAction(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val event = testEvent(project)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun update_withFileHistory() {
    val action = RestoreAppAction(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val backupFileHistory = BackupFileHistory(project)
    val file1 = temporaryFolder.newFile("1.txt")
    backupFileHistory.setFileHistory(listOf(file1.path))
    val event = testEvent(project)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun actionPerformed() {
    val actionHelper = FakeActionHelper("com.app", 1, "serial")
    val action = RestoreAppAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val event = testEvent(project, "serial")

    action.actionPerformed(event)

    fakeBackupManager.waitForRestoreInvocations(1)

    assertThat(fakeBackupManager.restoreModalInvocations)
      .containsExactly(
        RestoreModalInvocation("serial", Path.of("file.backup"), RESTORE_APP_ACTION, true)
      )
    assertThat(fakeDialogFactory.dialogs).isEmpty()
  }

  @Test
  fun actionPerformed_browse() {
    val actionHelper = FakeActionHelper("com.app", 1, "serial")
    val action =
      RestoreAppAction(
        config = Config.Browse,
        actionHelper = actionHelper,
        dialogFactory = fakeDialogFactory,
      )
    val event = testEvent(project, "serial")

    action.actionPerformed(event)

    fakeBackupManager.waitForRestoreInvocations(1)

    assertThat(fakeBackupManager.restoreModalInvocations)
      .containsExactly(
        RestoreModalInvocation("serial", Path.of("file.backup"), RESTORE_APP_ACTION, true)
      )
    assertThat(fakeDialogFactory.dialogs).isEmpty()
  }

  @Test
  fun actionPerformed_noTargets() {
    val actionHelper = FakeActionHelper("com.app", 1, null)
    val action = RestoreAppAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val event = testEvent(project, null)

    action.actionPerformed(event)

    fakeDialogFactory.waitForDialogs(1)

    assertThat(fakeBackupManager.restoreModalInvocations).isEmpty()
    assertThat(fakeDialogFactory.dialogs)
      .containsExactly(DialogData("Cannot Restore App Data", "Selected device is not running"))
  }

  @Test
  fun actionPerformed_deviceNotSupported() {
    val actionHelper = FakeActionHelper("com.app", 1, "serial")
    fakeBackupManager.isDeviceSupported = false
    val action = RestoreAppAction(actionHelper = actionHelper, dialogFactory = fakeDialogFactory)
    val event = testEvent(project, "serial")

    action.actionPerformed(event)

    fakeDialogFactory.waitForDialogs(1)

    assertThat(fakeBackupManager.restoreModalInvocations).isEmpty()
    assertThat(fakeDialogFactory.dialogs)
      .containsExactly(DialogData("Cannot Restore App Data", "Selected device is not supported"))
  }

  private fun testEvent(project: Project? = null, serialNumber: String? = null): AnActionEvent {
    val dataContext = SimpleDataContext.builder()
    if (project != null) {
      dataContext.add(CommonDataKeys.PROJECT, project)
    }
    if (serialNumber != null) {
      dataContext.add(SERIAL_NUMBER_KEY, serialNumber)
    }
    return TestActionEvent.createTestEvent(dataContext.build())
  }
}
