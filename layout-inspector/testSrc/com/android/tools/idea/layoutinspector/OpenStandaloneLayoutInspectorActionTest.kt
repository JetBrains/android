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
package com.android.tools.idea.layoutinspector

import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceState
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.tools.adtui.actions.createTestActionEvent
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.devicemanagerv2.DEVICE_ROW_DATA_KEY
import com.android.tools.idea.devicemanagerv2.DeviceRowData
import com.android.tools.idea.deviceprovisioner.DEVICE_HANDLE_KEY
import com.android.tools.idea.layoutinspector.pipeline.InspectorClientSettings
import com.android.tools.idea.layoutinspector.runningdevices.LayoutInspectorManager
import com.android.tools.idea.layoutinspector.runningdevices.withEmbeddedLayoutInspector
import com.android.tools.idea.layoutinspector.settings.LayoutInspectorSettings
import com.android.tools.idea.testing.disposable
import com.google.common.truth.Truth.assertThat
import com.google.common.util.concurrent.MoreExecutors
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.replaceService
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever

class OpenStandaloneLayoutInspectorActionTest {
  @get:Rule val projectRule = ProjectRule()
  @get:Rule val edtRule = EdtRule()

  private lateinit var fakeToolWindowManager: FakeToolWindowManager

  @Before
  fun setUp() {
    fakeToolWindowManager = FakeToolWindowManager(projectRule.project)
    projectRule.project.replaceService(
      ToolWindowManager::class.java,
      fakeToolWindowManager,
      projectRule.disposable,
    )
  }

  @Test
  fun actionEnabledForXr() {
    val action = OpenStandaloneLayoutInspectorAction()

    val deviceRowData = mock<DeviceRowData>()
    whenever(deviceRowData.type).thenAnswer { DeviceType.XR }

    val deviceHandle = mock<DeviceHandle>()
    whenever(deviceHandle.state).thenAnswer { mock<DeviceState.Connected>() }

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(DEVICE_ROW_DATA_KEY, deviceRowData)
        .add(DEVICE_HANDLE_KEY, deviceHandle)
        .build()

    val fakeEvent = createTestActionEvent(action, dataContext = dataContext)
    action.update(fakeEvent)

    assertThat(fakeEvent.presentation.isVisible).isTrue()
    assertThat(fakeEvent.presentation.isEnabled).isTrue()
  }

  @Test
  fun actionDisabledForRegularDevice() {
    val action = OpenStandaloneLayoutInspectorAction()

    val deviceRowData = mock<DeviceRowData>()
    whenever(deviceRowData.type).thenAnswer { DeviceType.HANDHELD }

    val deviceHandle = mock<DeviceHandle>()
    whenever(deviceHandle.state).thenAnswer { mock<DeviceState.Connected>() }

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(DEVICE_ROW_DATA_KEY, deviceRowData)
        .add(DEVICE_HANDLE_KEY, deviceHandle)
        .build()

    val fakeEvent = createTestActionEvent(action, dataContext = dataContext)
    action.update(fakeEvent)

    assertThat(fakeEvent.presentation.isVisible).isFalse()
    assertThat(fakeEvent.presentation.isEnabled).isTrue()
  }

  @RunsInEdt
  @Test
  fun actionPerformedTest() = withEmbeddedLayoutInspector {
    val coroutineScope = AndroidCoroutineScope(projectRule.disposable)
    val fakeForegroundProcessDetection = FakeForegroundProcessDetection()

    val layoutInspector =
      LayoutInspector(
        coroutineScope = coroutineScope,
        processModel = mock(),
        deviceModel = mock(),
        foregroundProcessDetection = fakeForegroundProcessDetection,
        inspectorClientSettings = InspectorClientSettings(projectRule.project),
        launcher = mock(),
        layoutInspectorModel = mock(),
        notificationModel = mock(),
        treeSettings = mock(),
        executor = MoreExecutors.directExecutor(),
        renderModel = mock(),
      )

    val mockLayoutInspectorProjectService = mock<LayoutInspectorProjectService>()
    whenever(mockLayoutInspectorProjectService.getLayoutInspector()).thenAnswer { layoutInspector }
    projectRule.project.replaceService(
      LayoutInspectorProjectService::class.java,
      mockLayoutInspectorProjectService,
      projectRule.disposable,
    )

    val mockLayoutInspectorManager = mock<LayoutInspectorManager>()
    projectRule.project.replaceService(
      LayoutInspectorManager::class.java,
      mockLayoutInspectorManager,
      projectRule.disposable,
    )

    val action = OpenStandaloneLayoutInspectorAction()

    val deviceRowData = mock<DeviceRowData>()
    whenever(deviceRowData.type).thenAnswer { DeviceType.XR }

    val deviceHandle = mock<DeviceHandle>()
    whenever(deviceHandle.state).thenAnswer { mock<DeviceState.Connected>() }

    val dataContext =
      SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, projectRule.project)
        .add(DEVICE_ROW_DATA_KEY, deviceRowData)
        .add(DEVICE_HANDLE_KEY, deviceHandle)
        .build()

    // Verify that the tool window is not present.
    val layoutInspectorToolWindow1 =
      ToolWindowManager.getInstance(projectRule.project)
        .getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
    assertThat(layoutInspectorToolWindow1).isNull()

    val fakeEvent = createTestActionEvent(action, dataContext = dataContext)
    action.actionPerformed(fakeEvent)

    PlatformTestUtil.dispatchAllEventsInIdeEventQueue()

    // Verify that the tool window was added.
    val layoutInspectorToolWindow2 =
      ToolWindowManager.getInstance(projectRule.project)
        .getToolWindow(LAYOUT_INSPECTOR_TOOL_WINDOW_ID)
    assertThat(layoutInspectorToolWindow2).isNotNull()
    assertThat(layoutInspectorToolWindow2!!.isActive).isTrue()
  }

  private class FakeToolWindowManager(project: Project) : ToolWindowHeadlessManagerImpl(project) {
    var layoutInspectorToolWindow = FakeToolWindow(project)

    override fun getToolWindow(id: String?): ToolWindow? {
      return when (id) {
        LAYOUT_INSPECTOR_TOOL_WINDOW_ID ->
          if (LayoutInspectorSettings.getInstance().embeddedLayoutInspectorEnabled) null
          else layoutInspectorToolWindow
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
}
