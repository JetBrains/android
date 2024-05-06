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
import com.android.tools.analytics.UsageTrackerRule
import com.android.tools.idea.execution.common.AndroidSessionInfo
import com.android.tools.idea.execution.common.assertTaskPresentedInStats
import com.android.tools.idea.execution.common.debug.AndroidDebuggerState
import com.android.tools.idea.execution.common.debug.DebugSessionStarter
import com.android.tools.idea.execution.common.debug.DebuggerThreadCleanupRule
import com.android.tools.idea.execution.common.debug.createFakeExecutionEnvironment
import com.android.tools.idea.execution.common.stats.RunStats
import com.android.tools.idea.execution.common.stats.RunStatsService
import com.android.tools.idea.projectsystem.TestApplicationProjectContext
import com.android.tools.idea.run.DeploymentApplicationService
import com.android.tools.idea.testing.AndroidModuleModelBuilder
import com.android.tools.idea.testing.AndroidProjectBuilder
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.common.truth.Truth.assertThat
import com.intellij.debugger.DebuggerManager
import com.intellij.debugger.DebuggerManagerEx
import com.intellij.execution.ExecutionException
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.registerServiceInstance
import com.intellij.testFramework.replaceService
import com.intellij.util.ExceptionUtil
import com.intellij.xdebugger.XDebuggerManager
import junit.framework.Assert.fail
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Tests for [AndroidJavaDebugger] code.
 */
@Ignore("FakeAdbTestRule hangs")
class AndroidJavaDebuggerTest {

  @get:Rule(order = 0)
  val projectRule = AndroidProjectRule.withAndroidModels(
    AndroidModuleModelBuilder(
      ":",
      "debug",
      AndroidProjectBuilder(
        applicationIdFor = { "com.test.integration.ddmlib" }
      ))
  )

  @get:Rule(order = 1)
  val fakeAdbRule: FakeAdbTestRule = FakeAdbTestRule()

  @get:Rule(order = 2)
  val debuggerThreadCleanupRule = DebuggerThreadCleanupRule { fakeAdbRule.server }

  @get:Rule
  val usageTrackerRule = UsageTrackerRule()

  val project
    get() = projectRule.project

  private lateinit var client: Client
  private lateinit var device: IDevice
  private lateinit var executionEnvironment: ExecutionEnvironment
  private lateinit var javaDebugger: AndroidJavaDebugger

  @Before
  fun setUp() = runTest {
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
  fun tearDown() = runTest {
    XDebuggerManager.getInstance(project).debugSessions.forEach {
      it.stop()
    }
  }

  private val onDebugProcessDestroyed: (IDevice) -> Unit = { device ->
    device.forceStop(FakeAdbTestRule.CLIENT_PACKAGE_NAME)
  }

  @Test
  fun testAllInformationForPositionManager() = runTest {
    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      TestApplicationProjectContext(FakeAdbTestRule.CLIENT_PACKAGE_NAME),
      executionEnvironment,
      javaDebugger,
      javaDebugger.createState(),
      onDebugProcessDestroyed,
      EmptyProgressIndicator()
    )

    val processHandler = session.debugProcess.processHandler
    // For AndroidPositionManager.
    assertThat(processHandler.getUserData(AndroidSessionInfo.ANDROID_DEVICE_API_LEVEL)).isEqualTo(AndroidVersion(26))
  }

  @Test
  fun testSessionCreated() = runTest {
    val stats = RunStatsService.get(project).create().also {
      executionEnvironment.putUserData(RunStats.KEY, it)
    }

    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      TestApplicationProjectContext(FakeAdbTestRule.CLIENT_PACKAGE_NAME),
      executionEnvironment,
      javaDebugger,
      javaDebugger.createState(), onDebugProcessDestroyed, EmptyProgressIndicator()
    )
    assertThat(session).isNotNull()
    assertThat(session.sessionName).isEqualTo("myConfiguration")
    stats.success()
    assertTaskPresentedInStats(usageTrackerRule.usages, "startDebuggerSession")
  }

  @Test
  fun testOnDebugProcessDestroyCallback() = runTest {
    val countDownLatch = CountDownLatch(1)
    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      TestApplicationProjectContext(FakeAdbTestRule.CLIENT_PACKAGE_NAME),
      executionEnvironment,
      javaDebugger,
      javaDebugger.createState(), destroyRunningProcess = { countDownLatch.countDown() }, EmptyProgressIndicator())

    Thread.sleep(250); // Let the virtual machine initialize. Otherwise, JDI Internal Event Handler thread is leaked.
    session.debugProcess.processHandler.destroyProcess()
    session.debugProcess.processHandler.waitFor()
    if (!countDownLatch.await(10, TimeUnit.SECONDS)) {
      fail("Callback wasn't called")
    }
  }

  @Test
  fun testSessionName() = runTest {
    val session = DebugSessionStarter.attachDebuggerToClientAndShowTab(project, client, AndroidJavaDebugger(), AndroidDebuggerState())
    assertThat(session).isNotNull()
    assertThat(client.clientData.pid).isAtLeast(0)
    assertThat(session!!.sessionName).isEqualTo("Java Only (${client.clientData.pid})")
  }

  @Test
  fun testCatchError() = runTest {
    val debuggerManagerExMock = Mockito.mock(DebuggerManagerEx::class.java)
    project.registerServiceInstance(DebuggerManager::class.java, debuggerManagerExMock)
    whenever(debuggerManagerExMock.attachVirtualMachine(any())).thenThrow(
      ExecutionException("Test execution exception in test testCatchError"))

    try {
      DebugSessionStarter.attachDebuggerToClientAndShowTab(project, client, AndroidJavaDebugger(), AndroidDebuggerState())
      fail()
    }
    catch (e: Throwable) {
      val cause = ExceptionUtil.findCause(e, ExecutionException::class.java)
      assertThat(cause.message).isEqualTo("Test execution exception in test testCatchError")
    }
  }

  @Test
  fun testKillAppOnDestroy() = runTest {
    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      TestApplicationProjectContext(FakeAdbTestRule.CLIENT_PACKAGE_NAME),
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

    Thread.sleep(250); // Let the virtual machine initialize. Otherwise, JDI Internal Event Handler thread is leaked.
    session.debugProcess.processHandler.destroyProcess()
    session.debugProcess.processHandler.waitFor()
    if (!countDownLatch.await(20, TimeUnit.SECONDS)) {
      fail("Process wasn't killed")
    }
  }

  @Test
  fun testVMExitedNotifierIsInvokedOnDetach() = runTest {
    val spyClient = Mockito.spy(client)

    val mockDeploymentAppService = mock<DeploymentApplicationService>()

    ApplicationManager.getApplication()
      .replaceService(DeploymentApplicationService::class.java, mockDeploymentAppService, projectRule.project)

    Mockito.`when`(mockDeploymentAppService.findClient(eq(device), eq(FakeAdbTestRule.CLIENT_PACKAGE_NAME))).thenReturn(listOf(spyClient))


    val session = DebugSessionStarter.attachDebuggerToStartedProcess(
      device,
      TestApplicationProjectContext(FakeAdbTestRule.CLIENT_PACKAGE_NAME),
      executionEnvironment,
      javaDebugger,
      javaDebugger.createState(),
      onDebugProcessDestroyed,
      EmptyProgressIndicator()
    )

    Thread.sleep(250); // Let the virtual machine initialize. Otherwise, JDI Internal Event Handler thread is leaked.
    session.debugProcess.processHandler.detachProcess()
    session.debugProcess.processHandler.waitFor()
    Mockito.verify(spyClient).notifyVmMirrorExited()
  }
}