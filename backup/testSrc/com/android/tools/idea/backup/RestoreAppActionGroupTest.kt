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
import com.android.tools.idea.backup.asyncaction.ActionEnableState
import com.android.tools.idea.backup.testing.FakeActionHelper
import com.android.tools.idea.backup.testing.FakeBackupManager
import com.android.tools.idea.backup.testing.hasTooltip
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
import com.intellij.testFramework.runInEdtAndWait
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class RestoreAppActionGroupTest {

  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val temporaryFolder =
    TemporaryFolder(TemporaryDirectory.generateTemporaryPath("").parent.toFile())
  private val fakeBackupManager = FakeBackupManager()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      FlagRule(StudioFlags.BACKUP_ENABLED, true),
      ProjectServiceRule(projectRule, BackupManager::class.java, fakeBackupManager),
      temporaryFolder,
    )

  @Test
  fun update_deviceNotSupported() {
    val action = BackupAppAction(FakeActionHelper("com.app", 1, "serial"))
    val event = testEvent(project)
    fakeBackupManager.isDeviceSupported = false

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isVisible).isTrue()
    assertThat(presentation.isEnabled).isFalse()
    assertThat(presentation.hasTooltip()).isTrue()
  }

  @Test
  fun update_withoutFileHistory() {
    val action = RestoreAppActionGroup(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val event = testEvent(project)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isEnabledAndVisible).isFalse()
  }

  @Test
  fun update_flagDisabled() {
    StudioFlags.BACKUP_ENABLED.override(false)
    val action = RestoreAppActionGroup(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val event = testEvent(project)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isVisible).isFalse()
    assertThat(presentation.isEnabled).isFalse()
  }

  @Test
  fun update_withFileHistory() {
    val action = RestoreAppActionGroup(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val backupFileHistory = BackupFileHistory(project)
    val file1 = temporaryFolder.newFile("1.txt")
    backupFileHistory.setFileHistory(listOf(file1.path))
    val event = testEvent(project, "serial")

    action.update(event)
    runInEdtAndWait {}

    val presentation = event.presentation
    assertThat(presentation.isVisible).isTrue()
    assertThat(presentation.isEnabled).isTrue()
  }

  @Test
  fun suspendedUpdate_noCompatibleApplication() {
    val action =
      RestoreAppActionGroup(
        actionHelper = FakeActionHelper("com.app", 1, "serial", isCompatibleApp = false)
      )
    val event = testEvent(project)
    val state = runBlocking { action.suspendedUpdate(project, event) }
    assertThat(state)
      .isEqualTo(ActionEnableState.Disabled(""""No compatible application installed""""))
  }

  @Test
  fun suspendedUpdate_noTargets() {
    val action = RestoreAppActionGroup(actionHelper = FakeActionHelper("com.app", 1, null))
    val event = testEvent(project)
    val state = runBlocking { action.suspendedUpdate(project, event) }
    assertThat(state).isEqualTo(ActionEnableState.Disabled("Selected device is not running"))
  }

  @Test
  fun getChildren() {
    val action = RestoreAppActionGroup(actionHelper = FakeActionHelper("com.app", 1, "serial"))
    val backupFileHistory = BackupFileHistory(project)
    val file1 = temporaryFolder.newFile("1.txt")
    backupFileHistory.setFileHistory(listOf(file1.path))
    val event = testEvent(project)

    val children = action.getChildren(event)

    assertThat(children).isNotEmpty()
    assertThat(children.first()).isInstanceOf(RestoreAppAction::class.java)
    assertThat(children[1]).isInstanceOf(RestoreAppAction::class.java)
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
