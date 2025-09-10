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
package com.android.tools.idea.gradle.project

import com.android.tools.adtui.workbench.PropertiesComponentMock
import com.android.tools.idea.gradle.project.sync.AutoSyncBehavior
import com.android.tools.idea.gradle.project.sync.AutoSyncSettingStore
import com.google.common.truth.Truth.assertThat
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.TestApplicationManager
import com.intellij.testFramework.replaceService
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncDueMessageTest {

  private var time: Long = 0L

  @get:Rule
  val applicationRule = ApplicationRule()

  @get:Rule
  val disposableRule = DisposableRule()

  private lateinit var projectManagerMock: ProjectManager
  private lateinit var propertiesComponent: PropertiesComponent

  @Before
  fun setup() {
    time = 0L // 1970-01-01
    SyncDueMessage.timeProvider = { time }
    AutoSyncSettingStore.timeProvider = { time }
    TestApplicationManager.getInstance()
    projectManagerMock = mock()
    propertiesComponent = PropertiesComponentMock()
    ApplicationManager.getApplication().replaceService(ProjectManager::class.java, projectManagerMock, disposableRule.disposable)
    ApplicationManager.getApplication().replaceService(PropertiesComponent::class.java, propertiesComponent, disposableRule.disposable)
  }

  @Test
  fun `snooze label says snooze is until tomorrow`() {
    PropertiesComponent.getInstance().setValue(SYNC_DUE_APP_WIDE_SNOOZE_EXPIRATION_DATE, "1970-01-02")
    val text = SyncDueMessage.getSnoozedProjectsSummaryNote()
    assertThat(text).isEqualTo("Reminders snoozed until tomorrow.")
  }

  @Test
  fun `snooze label null if no projects opened`() {
    whenever(projectManagerMock.openProjects).thenReturn(emptyArray<Project>())
    val text = SyncDueMessage.getSnoozedProjectsSummaryNote()
    assertThat(text).isNull()
  }

  @Test
  fun `snooze label mentions only current project`() {
    val (project1, properties1) = createMockProject("Project1")
    whenever(projectManagerMock.openProjects).thenReturn(arrayOf(project1))

    AutoSyncSettingStore.autoSyncBehavior = AutoSyncBehavior.Manual
    time++
    properties1.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())

    val text = SyncDueMessage.getSnoozedProjectsSummaryNote()
    assertThat(text).isEqualTo("Reminders snoozed for this project.")
  }

  @Test
  fun `snooze label mentions only one of two currently opened project`() {
    val (project1, properties1) = createMockProject("Project1")
    val (project2, _) = createMockProject("Project2")
    whenever(projectManagerMock.openProjects).thenReturn(arrayOf(project1, project2))

    AutoSyncSettingStore.autoSyncBehavior = AutoSyncBehavior.Manual
    time++
    properties1.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())

    val text = SyncDueMessage.getSnoozedProjectsSummaryNote()
    assertThat(text).isEqualTo("Reminders snoozed for project \"Project1\".")
  }

  @Test
  fun `snooze label mentions both opened projects`() {
    val (project1, properties1) = createMockProject("Project1")
    val (project2, properties2) = createMockProject("Project2")
    whenever(projectManagerMock.openProjects).thenReturn(arrayOf(project1, project2))

    AutoSyncSettingStore.autoSyncBehavior = AutoSyncBehavior.Manual
    time++
    properties1.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())
    properties2.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())

    val text = SyncDueMessage.getSnoozedProjectsSummaryNote()
    assertThat(text).isEqualTo("Reminders snoozed for projects: \"Project1\", \"Project2\".")
  }

  @Test
  fun `snooze label has individual snooze options overwritten when temporary project-wide snooze is on`() {
    val (project1, properties1) = createMockProject("Project1")
    val (project2, properties2) = createMockProject("Project2")
    whenever(projectManagerMock.openProjects).thenReturn(arrayOf(project1, project2))

    AutoSyncSettingStore.autoSyncBehavior = AutoSyncBehavior.Manual
    time++
    properties1.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())
    properties2.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())

    PropertiesComponent.getInstance().setValue(SYNC_DUE_APP_WIDE_SNOOZE_EXPIRATION_DATE, "1970-01-02")

    val text = SyncDueMessage.getSnoozedProjectsSummaryNote()
    assertThat(text).isEqualTo("Reminders snoozed until tomorrow.")
  }

  @Test
  fun `snooze label has individual snooze options again when temporary project-wide snooze is expired`() {
    val (project1, properties1) = createMockProject("Project1")
    val (project2, properties2) = createMockProject("Project2")
    whenever(projectManagerMock.openProjects).thenReturn(arrayOf(project1, project2))

    AutoSyncSettingStore.autoSyncBehavior = AutoSyncBehavior.Manual
    time++
    properties1.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())
    properties2.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())

    PropertiesComponent.getInstance().setValue(SYNC_DUE_APP_WIDE_SNOOZE_EXPIRATION_DATE, "1970-01-02")

    time = 3 * 24 * 3600 * 1000

    val text = SyncDueMessage.getSnoozedProjectsSummaryNote()
    assertThat(text).isEqualTo("Reminders snoozed for projects: \"Project1\", \"Project2\".")
  }

  @Test
  fun `snooze label has paths mentioned when project names aren't unique`() {
    val (project1, properties1) = createMockProject("Project-X", "/User/Projects/Project-X-copy-one")
    val (project2, properties2) = createMockProject("Project-X", "/User/Projects/Project-X-copy-two")
    whenever(projectManagerMock.openProjects).thenReturn(arrayOf(project1, project2))

    AutoSyncSettingStore.autoSyncBehavior = AutoSyncBehavior.Manual
    time++
    properties1.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())
    properties2.setValue(SYNC_DUE_PROJECT_SPECIFIC_SNOOZE_FLAG_SET_ON_TIMESTAMP, time.toString())

    val text = SyncDueMessage.getSnoozedProjectsSummaryNote()
    assertThat(text).isEqualTo("Reminders snoozed for projects: <br />&nbsp;&nbsp;&nbsp;\"Project-X\" (/User/Projects/Project-X-copy-one)<br />&nbsp;&nbsp;&nbsp;\"Project-X\" (/User/Projects/Project-X-copy-two)")
  }

  private fun createMockProject(name: String, basePath: String? = "/mock/path/$name"): Pair<Project, PropertiesComponentMock> {
    val project = mock<Project>()
    whenever(project.name).thenReturn(name)
    whenever(project.basePath).thenReturn(basePath)
    whenever(project.isDisposed).thenReturn(false)
    val properties = PropertiesComponentMock()
    whenever(project.getService(PropertiesComponent::class.java)).thenReturn(properties)
    return project to properties
  }
}