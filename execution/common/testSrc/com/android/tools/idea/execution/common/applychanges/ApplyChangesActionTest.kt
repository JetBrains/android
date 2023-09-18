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
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.AndroidExecutionTarget
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.debug.createFakeExecutionEnvironment
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.run.DeploymentApplicationService
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionTargetManager
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.RunConfiguration
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.RunsInEdt
import com.intellij.testFramework.TestActionEvent
import com.intellij.testFramework.replaceService
import com.intellij.xdebugger.XDebugProcess
import com.intellij.xdebugger.XDebugProcessStarter
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import org.junit.Rule
import org.junit.Test


class ApplyChangesActionTest  {
  @get:Rule
  val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.SIMPLE_APPLICATION).onEdt()

  private val APPLICATION_ID = "google.simpleapplication"

  @Test
  @RunsInEdt
  fun disabledDuringDebugSession() {
    // Set up
    val device = createMockDevice()
    val service = mock<DeploymentApplicationService>().also {
      whenever(it.findClient(device, APPLICATION_ID)).thenReturn(listOf(mock<Client>()))
    }
    ApplicationManager.getApplication()
      .replaceService(DeploymentApplicationService::class.java, service, projectRule.testRootDisposable)
    setTarget(device, RunManager.getInstance(projectRule.project).selectedConfiguration!!.configuration)

    val client = mock<Client>()
    whenever(client.device).thenReturn(device)

    val debugProcessHandler = AndroidRemoteDebugProcessHandler(projectRule.project, client, false)
    AndroidSessionInfo.create(debugProcessHandler, listOf(device), APPLICATION_ID)

    val executionEnvironment = createFakeExecutionEnvironment(projectRule.project, "fake env")

    // Start debug session
    XDebuggerManager.getInstance(projectRule.project).startSession(executionEnvironment, object : XDebugProcessStarter() {
      override fun start(session: XDebugSession) = object : XDebugProcess(session) {
        override fun getEditorsProvider() = mock<XDebuggerEditorsProvider>()
        override fun doGetProcessHandler() = debugProcessHandler
      }
    })

    // Update
    val action = ApplyChangesAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, projectRule.project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo("Apply Changes and Restart Activity is disabled for this device because it is currently not allowed during debugging.")
  }

  private fun setTarget(device: IDevice, config: RunConfiguration) {
    val executionTarget = object : AndroidExecutionTarget() {
      override fun getId() = "Test Target"

      override fun getDisplayName() = "Test Target"

      override fun getIcon() = null

      override fun getAvailableDeviceCount() = 1

      override fun getRunningDevices() = listOf(device)
    }

    val executionTargetManager = mock<ExecutionTargetManager>()
      .also {
        whenever(it.activeTarget).thenReturn(executionTarget)
        whenever(it.getTargetsFor(config)).thenReturn(listOf(executionTarget))
      }
    projectRule.project.replaceService(ExecutionTargetManager::class.java, executionTargetManager, projectRule.testRootDisposable)
  }

  private fun createMockDevice(): IDevice {
    val mockDevice = mock<IDevice>()
    whenever(mockDevice.isOnline).thenReturn(true)
    whenever(mockDevice.version).thenReturn(AndroidVersion(33))
    return mockDevice
  }
}