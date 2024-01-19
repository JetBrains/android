/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.layoutinspector

import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.streaming.RUNNING_DEVICES_TOOL_WINDOW_ID
import com.google.common.truth.Truth.assertThat
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroup
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.DisposableRule
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.verify

class ShowLayoutInspectorActionTest {

  @get:Rule val projectRule = ProjectRule()

  @get:Rule val applicationRule = ApplicationRule()

  @get:Rule val disposableRule = DisposableRule()

  private lateinit var fakeToolWindowManager: FakeToolWindowManager
  private lateinit var fakeNotificationGroupManager: FakeNotificationGroupManager

  @Before
  fun setUp() {
    fakeToolWindowManager = FakeToolWindowManager(projectRule.project)
    projectRule.project.replaceService(
      ToolWindowManager::class.java,
      fakeToolWindowManager,
      disposableRule.disposable,
    )

    fakeNotificationGroupManager = FakeNotificationGroupManager()
    ApplicationManager.getApplication()
      .replaceService(
        NotificationGroupManager::class.java,
        fakeNotificationGroupManager,
        disposableRule.disposable,
      )
  }

  @Test
  fun testActivateLayoutInspectorToolWindow() =
    withEmbeddedLayoutInspector(false) {
      val showLayoutInspectorAction = ShowLayoutInspectorAction()
      assertThat(fakeToolWindowManager.layoutInspectorToolWindow.isActive).isFalse()
      showLayoutInspectorAction.actionPerformed(
        createFakeEvent(projectRule.project, showLayoutInspectorAction)
      )
      assertThat(fakeToolWindowManager.layoutInspectorToolWindow.isActive).isTrue()
    }

  @Test
  fun testActivateRunningDevicesToolWindow() = withEmbeddedLayoutInspector {
    val showLayoutInspectorAction = ShowLayoutInspectorAction()
    assertThat(fakeToolWindowManager.runningDevicesToolWindow.isActive).isFalse()
    showLayoutInspectorAction.actionPerformed(
      createFakeEvent(projectRule.project, showLayoutInspectorAction)
    )
    assertThat(fakeToolWindowManager.runningDevicesToolWindow.isActive).isTrue()
  }

  @Test
  fun testShowLayoutInspectorDiscoveryPopUp() = withEmbeddedLayoutInspector {
    val showLayoutInspectorAction = ShowLayoutInspectorAction()
    showLayoutInspectorAction.actionPerformed(
      createFakeEvent(projectRule.project, showLayoutInspectorAction)
    )
    verify(fakeNotificationGroupManager.mockNotificationGroup)
      .createNotification(
        "Layout Inspector is now embedded in the Running Devices window.",
        "Launch, connect, or mirror a device to start inspecting.",
        NotificationType.INFORMATION,
      )
    verify(fakeNotificationGroupManager.mockNotification).notify(any())
  }
}

private fun createFakeEvent(project: Project, anAction: AnAction) =
  AnActionEvent.createFromAnAction(anAction, null, "") {
    when (it) {
      CommonDataKeys.PROJECT.name -> project
      else -> null
    }
  }

private class FakeToolWindowManager(project: Project) : ToolWindowHeadlessManagerImpl(project) {
  var runningDevicesToolWindow = FakeToolWindow(project)
  var layoutInspectorToolWindow = FakeToolWindow(project)

  override fun getToolWindow(id: String?): ToolWindow? {
    return when (id) {
      RUNNING_DEVICES_TOOL_WINDOW_ID -> runningDevicesToolWindow
      LAYOUT_INSPECTOR_TOOL_WINDOW_ID -> layoutInspectorToolWindow
      else -> super.getToolWindow(id)
    }
  }
}

private class FakeToolWindow(project: Project) :
  ToolWindowHeadlessManagerImpl.MockToolWindow(project) {
  private var isActive = false

  override fun activate(runnable: Runnable?) {
    isActive = true
  }

  override fun isActive(): Boolean {
    return isActive
  }
}

private class FakeNotificationGroupManager : NotificationGroupManager {
  val mockNotification = mock<Notification>()
  val mockNotificationGroup = mock<NotificationGroup>()

  init {
    whenever(
        mockNotificationGroup.createNotification(
          MockitoKt.any<String>(),
          MockitoKt.any<String>(),
          MockitoKt.any<NotificationType>(),
        )
      )
      .thenAnswer { mockNotification }
  }

  override fun getNotificationGroup(groupId: String): NotificationGroup {
    return when (groupId) {
      "LAYOUT_INSPECTOR_DISCOVERY" -> mockNotificationGroup
      else -> throw IllegalArgumentException("Unexpected groupId: $groupId")
    }
  }

  override fun isGroupRegistered(groupId: String): Boolean {
    return when (groupId) {
      "LAYOUT_INSPECTOR_DISCOVERY" -> true
      else -> false
    }
  }

  override fun getRegisteredNotificationGroups() = mutableListOf(mockNotificationGroup)

  override fun isRegisteredNotificationId(notificationId: String) = false
}
