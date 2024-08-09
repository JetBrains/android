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

import com.android.flags.junit.FlagRule
import com.android.tools.idea.backup.testing.FakeActionHelper
import com.android.tools.idea.backup.testing.FakeBackupManager
import com.android.tools.idea.backup.testing.FakeBackupManager.Action.BackupModal
import com.android.tools.idea.backup.testing.FakeBackupManager.Action.ChooseBackupFile
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
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.runInEdtAndWait
import java.nio.file.Path
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/** Tests for [BackupAppAction] */
@RunWith(JUnit4::class)
internal class BackupAppActionTest {
  private val projectRule = ProjectRule()
  private val project
    get() = projectRule.project

  private val fakeBackupManager = FakeBackupManager()

  @get:Rule
  val rule =
    RuleChain(
      projectRule,
      FlagRule(StudioFlags.BACKUP_ENABLED, true),
      ProjectServiceRule(projectRule, BackupManager::class.java, fakeBackupManager),
    )

  @Test
  fun update() {
    val action = BackupAppAction(FakeActionHelper("com.app", 1, "serial"))
    val event = testEvent(project)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isVisible).isTrue()
    assertThat(presentation.isEnabled).isTrue()
  }

  @Test
  fun update_flagDisabled() {
    StudioFlags.BACKUP_ENABLED.override(false)
    val action = BackupAppAction(FakeActionHelper("com.app", 1, "serial"))
    val event = testEvent(project)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isVisible).isFalse()
  }

  @Test
  fun update_noApplicationId() {
    val action = BackupAppAction(FakeActionHelper(null, 1, "serial"))
    val event = testEvent(project)

    action.update(event)

    val presentation = event.presentation
    assertThat(presentation.isVisible).isTrue()
    assertThat(presentation.isEnabled).isFalse()
  }

  @Test
  fun actionPerformed() {
    val actionHelper = FakeActionHelper("com.app", 1, "serial")
    val action = BackupAppAction(actionHelper)
    val event = testEvent(project, "serial")

    action.actionPerformed(event)

    runInEdtAndWait {}
    assertThat(fakeBackupManager.actions)
      .containsExactly(
        ChooseBackupFile("com.app"),
        BackupModal("serial", "com.app", Path.of("com.app.backup"), notify = true),
      )
      .inOrder()
    assertThat(actionHelper.warnings).isEmpty()
  }

  @Test
  fun actionPerformed_noTargets() {
    val actionHelper = FakeActionHelper("com.app", 0, "serial")
    val action = BackupAppAction(actionHelper)
    val event = testEvent(project, "serial")

    action.actionPerformed(event)

    runInEdtAndWait {}
    assertThat(fakeBackupManager.actions).isEmpty()
    assertThat(actionHelper.warnings)
      .containsExactly("Cannot Backup Application: Selected device is not running")
  }

  @Test
  fun actionPerformed_multipleTargets() {
    val actionHelper = FakeActionHelper("com.app", 2, "serial")
    val action = BackupAppAction(actionHelper)
    val event = testEvent(project, "serial")
    action.actionPerformed(event)

    runInEdtAndWait {}
    assertThat(fakeBackupManager.actions).isEmpty()
    assertThat(actionHelper.warnings)
      .containsExactly("Cannot Backup Application: Action is not supported for multiple devices")
  }

  @Test
  fun actionPerformed_noRunningTargets() {
    val actionHelper = FakeActionHelper("com.app", 1, null)
    val action = BackupAppAction(actionHelper)
    val event = testEvent(project, "serial")

    action.actionPerformed(event)

    runInEdtAndWait {}
    assertThat(fakeBackupManager.actions).isEmpty()
    assertThat(actionHelper.warnings)
      .containsExactly("Cannot Backup Application: Selected device is not running")
  }
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
