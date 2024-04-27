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
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.debug.createFakeExecutionEnvironment
import com.android.tools.idea.execution.common.processhandler.AndroidRemoteDebugProcessHandler
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject
import com.android.tools.idea.run.DeploymentApplicationService
import com.android.tools.idea.run.util.SwapInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.onEdt
import com.google.common.truth.Truth.assertThat
import com.intellij.execution.ExecutionManager
import com.intellij.execution.RunManager
import com.intellij.execution.impl.ExecutionManagerImpl
import com.intellij.execution.runners.ExecutionEnvironment
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
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test


class ApplyChangesActionTest  {
  @get:Rule
  val projectRule = AndroidProjectRule.testProject(AndroidCoreTestProject.SIMPLE_APPLICATION).onEdt()

  val project get() = projectRule.project

  private val device = createMockDevice()

  private val APPLICATION_ID = "google.simpleapplication"

  @Before
  fun setup() {
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
  }

  @Ignore("b/311215061")
  @Test
  @RunsInEdt
  fun disabledDuringDebugSession() {
    // Set up
    val client = mock<Client>()
    whenever(client.device).thenReturn(device)

    val debugProcessHandler = AndroidRemoteDebugProcessHandler(project, client, false)
    AndroidSessionInfo.create(debugProcessHandler, listOf(device), APPLICATION_ID)

    val executionEnvironment = createFakeExecutionEnvironment(project, "fake env")

    // Start debug session
    XDebuggerManager.getInstance(project).startSession(executionEnvironment, object : XDebugProcessStarter() {
      override fun start(session: XDebugSession) = object : XDebugProcess(session) {
        override fun getEditorsProvider() = mock<XDebuggerEditorsProvider>()
        override fun doGetProcessHandler() = debugProcessHandler
      }
    })

    // Update
    val action = ApplyChangesAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.update(event)

    // Assert
    assertThat(event.presentation.isEnabled).isFalse()
    assertThat(event.presentation.description).isEqualTo("Apply Changes and Restart Activity is disabled for this device because it is currently not allowed during debugging.")
  }

  @Ignore("b/311215061")
  @Test
  @RunsInEdt
  fun applyChangesAction_executesApplyChanges() {
    // Set up
    var applyChangesExecuted = false
    val executionServiceMock = mock<ExecutionManagerImpl>()

    whenever(executionServiceMock.executeConfiguration(MockitoKt.any(), MockitoKt.any(), MockitoKt.any())).thenAnswer {
      val swapInfo = (it.getArgument(0) as ExecutionEnvironment).getUserData(SwapInfo.SWAP_INFO_KEY)
      applyChangesExecuted = swapInfo?.type == SwapInfo.SwapType.APPLY_CHANGES
      return@thenAnswer Unit
    }

    project.replaceService(ExecutionManager::class.java, executionServiceMock, projectRule.testRootDisposable)

    // Perform
    val action = ApplyChangesAction()
    val event = TestActionEvent.createTestEvent(SimpleDataContext.getSimpleContext(CommonDataKeys.PROJECT, project))
    action.actionPerformed(event)

    // Assert
    assertThat(applyChangesExecuted).isTrue()
  }


  private fun createMockDevice(): IDevice {
    val mockDevice = mock<IDevice>()
    whenever(mockDevice.isOnline).thenReturn(true)
    whenever(mockDevice.version).thenReturn(AndroidVersion(33))
    return mockDevice
  }
}