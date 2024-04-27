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
package com.android.tools.idea.execution.common.applychanges

import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.run.DeploymentApplicationService
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.UnknownConfigurationType
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.remote.RemoteConfigurationType
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.util.asSafely
import org.junit.Rule
import org.junit.Test

class CodeSwapActionTest {

  @get:Rule
  val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.SIMPLE_APPLICATION).onEdt()

  val project get() = projectRule.project

  private val APPLICATION_ID = "google.simpleapplication"

  @Test
  @RunsInEdt
  fun `enabled when running on device`() {
    // Set up
    val device = createMockDevice()
    val service = mock<DeploymentApplicationService>().also {
      whenever(it.findClient(device, APPLICATION_ID)).thenReturn(listOf(mock<Client>()))
    }
    ApplicationManager.getApplication().replaceService(DeploymentApplicationService::class.java, service, projectRule.testRootDisposable)
    setExecutionTargetForConfiguration(
      project,
      device,
      RunManager.getInstance(project).selectedConfiguration!!.configuration,
      projectRule.testRootDisposable
    )

    // Update
    val action = CodeSwapAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  @RunsInEdt
  fun applyCodeChangesAction_executesApplyCodeChanges() {
    // Set up
    val device = createMockDevice()
    val service = mock<DeploymentApplicationService>().also {
      whenever(it.findClient(device, APPLICATION_ID)).thenReturn(listOf(mock<Client>()))
    }
    ApplicationManager.getApplication().replaceService(DeploymentApplicationService::class.java, service, projectRule.testRootDisposable)
    setExecutionTargetForConfiguration(
      project,
      device,
      RunManager.getInstance(project).selectedConfiguration!!.configuration,
      projectRule.testRootDisposable
    )

    var applyCodeChangesExecuted = false

    val executionServiceMock = mock<ExecutionManagerImpl>()

    whenever(executionServiceMock.executeConfiguration(any(), any(), any())).thenAnswer {
      val swapInfo = (it.getArgument(0) as ExecutionEnvironment).getUserData(SwapInfo.SWAP_INFO_KEY)
      applyCodeChangesExecuted = swapInfo?.type == SwapInfo.SwapType.APPLY_CODE_CHANGES
      return@thenAnswer Unit
    }

    project.replaceService(ExecutionManager::class.java, executionServiceMock, projectRule.testRootDisposable)

    // Perform
    val action = CodeSwapAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.actionPerformed(event)

    // Assert
    assertThat(applyCodeChangesExecuted).isTrue()
  }

  @Test
  @RunsInEdt
  fun `disabled, no configuration selected`() {
    // Set up
    RunManager.getInstance(project).selectedConfiguration = null

    // Update
    val action = CodeSwapAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo(
      "Apply Code Changes is disabled for this device because there is no configuration selected.")
  }

  @Test
  @RunsInEdt
  fun `disabled, can't get applicationId`() {
    // Set up
    RunManager.getInstance(project).createConfiguration("unsupported", RemoteConfigurationType.getInstance()).also {
      it.configuration.asSafely<RunConfigurationBase<*>>()!!.putUserData(BaseAction.SHOW_APPLY_CHANGES_UI, true)
      RunManager.getInstance(project).addConfiguration(it)
      RunManager.getInstance(project).selectedConfiguration = it
    }

    // Update
    val action = CodeSwapAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo(
      "Apply Code Changes is disabled for this device because can't detect applicationId.")
  }

  @Test
  @RunsInEdt
  fun `disabled, unsupported execution target`() {
    // Update
    val action = CodeSwapAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo(
      "Apply Code Changes is disabled for this device because unsupported execution target.")
  }

  @Test
  @RunsInEdt
  fun `disabled, incompatible device API level`() {
    // Set up
    val device = createMockDevice(25)

    setExecutionTargetForConfiguration(
      project,
      device,
      RunManager.getInstance(project).selectedConfiguration!!.configuration,
      projectRule.testRootDisposable
    )

    // Update
    val action = CodeSwapAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo(
      "Apply Code Changes is disabled for this device because its API level is lower than 26.")
  }

  @Test
  @RunsInEdt
  fun `disabled, devices not connected`() {
    // Set up
    setExecutionTargetForConfiguration(
      project,
      null,
      RunManager.getInstance(project).selectedConfiguration!!.configuration,
      projectRule.testRootDisposable
    )

    // Update
    val action = CodeSwapAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo(
      "Apply Code Changes is disabled for this device because the selected devices are not connected.")
  }

  @Test
  @RunsInEdt
  fun `disabled, app not detected`() {
    // Set up
    val device = createMockDevice()

    setExecutionTargetForConfiguration(
      project,
      device,
      RunManager.getInstance(project).selectedConfiguration!!.configuration,
      projectRule.testRootDisposable
    )

    // Update
    val action = CodeSwapAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isVisible).isTrue()
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo(
      "Apply Code Changes is disabled for this device because the app is not yet running or not debuggable.")
  }

  @Test
  @RunsInEdt
  fun `invisible, unsupported configuration`() {
    // Set up
    RunManager.getInstance(project).createConfiguration("unsupported", UnknownConfigurationType.getInstance()).also {
      RunManager.getInstance(project).addConfiguration(it)
      RunManager.getInstance(project).selectedConfiguration = it
    }

    // Update
    val action = CodeSwapAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isVisible).isFalse()
    assertThat(event.presentation.description).isEqualTo(
      "Apply Code Changes is disabled for this device because the selected configuration is not supported.")
  }


  private fun createMockDevice(apiLevel: Int = 33): IDevice {
    val mockDevice = mock<IDevice>()
    whenever(mockDevice.isOnline).thenReturn(true)
    whenever(mockDevice.version).thenReturn(AndroidVersion(apiLevel))
    return mockDevice
  }
}