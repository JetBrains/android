/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.execution.common.debug.impl.java
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.eq
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.debug.DebugSessionStarter
import com.android.tools.idea.execution.common.debug.createFakeExecutionEnvironment
import com.android.tools.idea.run.DeploymentApplicationService
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.util.ExceptionUtil
import com.intellij.xdebugger.XDebuggerManager
import junit.framework.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for [AndroidJavaDebugger] code.
 */
class AndroidJavaDebuggerTest {
  @get:Rule
  var fakeAdbRule: FakeAdbTestRule = FakeAdbTestRule()

  @get:Rule
  val projectRule = ProjectRule()

  val project
    get() = projectRule.project

  private lateinit var client: Client
  private lateinit var device: IDevice
  private lateinit var executionEnvironment: ExecutionEnvironment
  private lateinit var javaDebugger: AndroidJavaDebugger

  @Before
  fun setUp() {
    // Connect a test device.
    val deviceState = fakeAdbRule.connectAndWaitForDevice()

    deviceState.setActivityManager { args, _ ->
      if ("force-stop" == args[0] && FakeAdbTestRule.CLIENT_PACKAGE_NAME == args[1]) {
        deviceState.stopClient(client.clientData.pid)
      }
    }

    device = AndroidDebugBridge.getBridge()!!.devices.single()
    client = FakeAdbTestRule.launchAndWaitForProcess(deviceState, true)
    assertThat(device.getClient(FakeAdbTestRule.CLIENT_PACKAGE_NAME)).isEqualTo(client)

    executionEnvironment = createFakeExecutionEnvironment(project, "myConfiguration")
    javaDebugger = AndroidJavaDebugger()
  }

  @After
  fun tearDown() {
    XDebuggerManager.getInstance(project).debugSessions.forEach {
      it.stop()
    }
  }

  private val onDebugProcessDestroyed: (IDevice) -> Unit = { device ->
    device.forceStop(FakeAdbTestRule.CLIENT_PACKAGE_NAME)
  }

  @Test
  fun testAllInformationForApplyChangesAndPositionManager() {
    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      FakeAdbTestRule.CLIENT_PACKAGE_NAME,
      executionEnvironment,
      javaDebugger,
      javaDebugger.createState(),
      onDebugProcessDestroyed,
      EmptyProgressIndicator()
    )

    val processHandler = session!!.debugProcess.processHandler
    // For AndroidPositionManager.
    assertThat(processHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).isEqualTo(AndroidVersion(26))
    // For Apply Changes.
    val sessionInfo = processHandler.getUserData(AndroidSessionInfo.KEY)!!
    assertThat(sessionInfo.processHandler).isEqualTo(processHandler)
    assertThat(sessionInfo.executor).isEqualTo(executionEnvironment.executor)
  }

  @Test
  fun testSessionCreated() {
    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      FakeAdbTestRule.CLIENT_PACKAGE_NAME,
      executionEnvironment,
      javaDebugger,
      javaDebugger.createState(), onDebugProcessDestroyed, EmptyProgressIndicator()
    )
    assertThat(session).isNotNull()
    assertThat(session!!.sessionName).isEqualTo("myConfiguration")
  }

  @Test
  fun testOnDebugProcessDestroyCallback() {
    val countDownLatch = CountDownLatch(1)
    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      FakeAdbTestRule.CLIENT_PACKAGE_NAME,
      executionEnvironment,
      javaDebugger,
      javaDebugger.createState(), destroyRunningProcess = { countDownLatch.countDown() }, EmptyProgressIndicator())

    session.debugProcess.processHandler.destroyProcess()
    session.debugProcess.processHandler.waitFor()
    if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
      fail("Callback wasn't called")
    }
  }

  @Test
  fun testSessionName() {
    val session = AndroidJavaDebugger().attachToClient(project, client, null)
    assertThat(session).isNotNull()
    assertThat(client.clientData.pid).isAtLeast(0)
    assertThat(session!!.sessionName).isEqualTo("Java Only (${client.clientData.pid})")
  }

  @Test
  fun testCatchError() {
    val debuggerManagerExMock = Mockito.mock(DebuggerManagerEx::class.java)
    project.registerServiceInstance(DebuggerManager::class.java, debuggerManagerExMock)
    whenever(debuggerManagerExMock.attachVirtualMachine(any())).thenThrow(
      ExecutionException("Test execution exception in test testCatchError"))

    try {
      AndroidJavaDebugger().attachToClient(project, client, null)
      fail()
    }
    catch (e: Throwable) {
      val cause = ExceptionUtil.findCause(e, ExecutionException::class.java)
      assertThat(cause.message).isEqualTo("Test execution exception in test testCatchError")
    }
  }

  @Test
  fun testKillAppOnDestroy() {
    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      FakeAdbTestRule.CLIENT_PACKAGE_NAME,
      executionEnvironment,
      javaDebugger,
      javaDebugger.createState(), onDebugProcessDestroyed, indicator = EmptyProgressIndicator())

    val countDownLatch = CountDownLatch(1)

    AndroidDebugBridge.addDeviceChangeListener(object : IDeviceChangeListener {
      override fun deviceConnected(device: IDevice) {}
      override fun deviceDisconnected(device: IDevice) {}

      override fun deviceChanged(device: IDevice, changeMask: Int) {
        if (device == client.device && changeMask and IDevice.CHANGE_CLIENT_LIST != 0) {
          if (device.getClient(FakeAdbTestRule.CLIENT_PACKAGE_NAME) == null) {
            countDownLatch.countDown()
            AndroidDebugBridge.removeDeviceChangeListener(this)
          }
        }
      }
    })

    session.debugProcess.processHandler.destroyProcess()
    session.debugProcess.processHandler.waitFor()
    if (!countDownLatch.await(20, TimeUnit.SECONDS)) {
      fail("Process wasn't killed")
    }
  }

  @Test
  fun testVMExitedNotifierIsInvokedOnDetach() {
    val spyClient = Mockito.spy(client)

    val mockDeploymentAppService = mock<DeploymentApplicationService>()

    ApplicationManager.getApplication()
      .replaceService(DeploymentApplicationService::class.java, mockDeploymentAppService, projectRule.project)

    Mockito.`when`(mockDeploymentAppService.findClient(eq(device), eq(FakeAdbTestRule.CLIENT_PACKAGE_NAME))).thenReturn(listOf(spyClient))


    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      FakeAdbTestRule.CLIENT_PACKAGE_NAME,
      executionEnvironment,
      javaDebugger,
      javaDebugger.createState(),
      onDebugProcessDestroyed,
      EmptyProgressIndicator()
    )

    session.debugProcess.processHandler.detachProcess()
    session.debugProcess.processHandler.waitFor()
    Mockito.verify(spyClient).notifyVmMirrorExited()
  }
}