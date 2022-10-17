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
package com.android.tools.idea.run.debug
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener
import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.ddmlib.internal.FakeAdbTestRule
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.run.AndroidSessionInfo
import com.android.tools.idea.run.editor.AndroidJavaDebugger
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.testFramework.ProjectRule
import com.intellij.testFramework.registerServiceInstance
import com.intellij.util.ExceptionUtil
import com.intellij.xdebugger.XDebuggerManager
import junit.framework.Assert.fail
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for [attachJavaDebuggerToClient], method will eventually replace all [AndroidJavaDebugger] code.
 */
@Ignore("FakeAdbTestRule hangs")
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
  }

  @After
  fun tearDown() {
    XDebuggerManager.getInstance(project).debugSessions.forEach {
      it.stop()
    }
  }

  @Test
  fun testAllInformationForApplyChangesAndPositionManager() {
    val session = attachJavaDebuggerToClient(project, client, executionEnvironment, null,
                                             onDebugProcessDestroyed = { device -> device.forceStop(FakeAdbTestRule.CLIENT_PACKAGE_NAME) }).blockingGet(10,
                                                                                                                                TimeUnit.SECONDS)
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
    val session = attachJavaDebuggerToClient(project, client, executionEnvironment, null,
                                             onDebugProcessDestroyed = { device ->
                                               device.forceStop(FakeAdbTestRule.CLIENT_PACKAGE_NAME)
                                             }).blockingGet(10,
                                                            TimeUnit.SECONDS)
    assertThat(session).isNotNull()
    assertThat(session!!.sessionName).isEqualTo("myConfiguration")
  }

  @Test
  fun testOnDebugProcessStartedCallback() {
    var callbackCount = 0
    val onDebugProcessStarted: () -> Unit = {
      callbackCount++
    }

    val session = attachJavaDebuggerToClient(project, client, executionEnvironment,
                                             onDebugProcessStarted = onDebugProcessStarted,
                                             onDebugProcessDestroyed = { device ->
                                               device.forceStop(FakeAdbTestRule.CLIENT_PACKAGE_NAME)
                                             }).blockingGet(10,
                                                            TimeUnit.SECONDS)
    assertThat(session).isNotNull()
    assertThat(callbackCount).isEqualTo(1)
  }

  @Test
  fun testOnDebugProcessDestroyCallback() {
    val countDownLatch = CountDownLatch(1)
    val session = attachJavaDebuggerToClient(project, client, executionEnvironment,
                                             onDebugProcessDestroyed = { countDownLatch.countDown() }).blockingGet(10, TimeUnit.SECONDS)!!
    session.debugProcess.processHandler.destroyProcess()
    session.debugProcess.processHandler.waitFor()
    if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
      fail("Callback wasn't called")
    }
  }

  @Test
  fun testSessionName() {
    val session = attachJavaDebuggerToClientAndShowTab(project, client).blockingGet(10, TimeUnit.SECONDS)
    assertThat(session).isNotNull()
    assertThat(client.debuggerListenPort).isAtLeast(0)
    assertThat(client.clientData.pid).isAtLeast(0)
    assertThat(session!!.sessionName).isEqualTo(
      "Android Debugger (pid: ${client.clientData.pid}, debug port: ${client.debuggerListenPort})")
  }

  @Test
  fun testCatchError() {
    val debuggerManagerExMock = Mockito.mock(DebuggerManagerEx::class.java)
    project.registerServiceInstance(DebuggerManager::class.java, debuggerManagerExMock)
    whenever(debuggerManagerExMock.attachVirtualMachine(any())).thenThrow(
      ExecutionException("Test execution exception in test testCatchError"))

    try {
      attachJavaDebuggerToClientAndShowTab(project, client).blockingGet(30, TimeUnit.SECONDS)
      fail()
    }
    catch (e: Throwable) {
      val cause = ExceptionUtil.findCause(e, ExecutionException::class.java)
      assertThat(cause.message).isEqualTo("Test execution exception in test testCatchError")
    }
  }

  @Test
  fun testKillAppOnDestroy() {
    val session = attachJavaDebuggerToClient(project, client, executionEnvironment,
                                             onDebugProcessDestroyed = { device ->
                                               device.forceStop(FakeAdbTestRule.CLIENT_PACKAGE_NAME)
                                             }).blockingGet(10,
                                                            TimeUnit.SECONDS)!!

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
    val session = attachJavaDebuggerToClient(project, spyClient, executionEnvironment,
                                             onDebugProcessDestroyed = { device ->
                                               device.forceStop(FakeAdbTestRule.CLIENT_PACKAGE_NAME)
                                             }).blockingGet(10,
                                                            TimeUnit.SECONDS)!!

    session.debugProcess.processHandler.detachProcess()
    session.debugProcess.processHandler.waitFor()
    Mockito.verify(spyClient).notifyVmMirrorExited()
  }
}