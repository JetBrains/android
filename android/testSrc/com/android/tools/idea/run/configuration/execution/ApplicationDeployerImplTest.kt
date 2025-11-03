package com.android.tools.idea.run.configuration.execution

import com.android.adblib.ddmlibcompatibility.testutils.InitAndroidDebugBridgeRule
import com.android.adblib.ddmlibcompatibility.testutils.waitForOnlineDevice
import com.android.adblib.testingutils.CoroutineTestUtils.runBlockingWithTimeout
import com.android.adblib.testingutils.FakeAdbServerRule
import com.android.ddmlib.AndroidDebugBridge
import com.android.fakeadbserver.DeviceState
import com.android.sdklib.AndroidApiLevel
import com.android.testutils.AssumeUtil
import com.android.testutils.TestUtils
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.execution.common.DeployOptions
import com.android.tools.idea.execution.common.stats.RunStatsService
import com.android.tools.idea.run.ApkInfo
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.testing.TestProjectPaths.TEST_DATA_PATH
import com.google.common.truth.Truth.assertThat
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.testFramework.RuleChain
import com.intellij.testFramework.common.ThreadLeakTracker
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test


class ApplicationDeployerImplTest {

  private val fakeAdbRule = FakeAdbServerRule()
  private val initAndroidDebugBridgeRule =
    InitAndroidDebugBridgeRule(alsoCreateBridge = true) { fakeAdbRule.adbServer.port }

  private val tracker = TestUsageTracker(VirtualTimeScheduler())

  private val projectRule = AndroidProjectRule.onDisk()

  @get:Rule
  val rule = RuleChain(projectRule, fakeAdbRule, initAndroidDebugBridgeRule)

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(tracker)
    // InnocuousThread- is needed because adblib's AsynchronousChannelGroup is reusing IJ's background threads.
    ThreadLeakTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "Deployment Service", "InnocuousThread-")
  }

  @After
  fun cleanUp() {
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun fillStats() {
    // b/415866691
    AssumeUtil.assumeNotWindows()

    val deviceState = fakeAdbRule.connectDevice(
        deviceId = "device_id",
        manufacturer = "mfg",
        deviceModel = "model",
        release = "10.0.0",
        sdk = AndroidApiLevel(30),
        hostConnectionType = DeviceState.HostConnectionType.USB)
      .also { it.deviceStatus = DeviceState.DeviceStatus.ONLINE }
    runBlockingWithTimeout {
      deviceState.waitForOnlineDevice()
    }
    val device = AndroidDebugBridge.getBridge()!!.devices.single()

    val runStat = RunStatsService.get(projectRule.project).create()

    val deployer = ApplicationDeployerImpl(projectRule.project, runStat)

    // apkWithDefaultActivity.apk contains simple project with basic activity `com.example.myapplication.MainActivity`.
    val apk = TestUtils.resolveWorkspacePathUnchecked("$TEST_DATA_PATH/configurations/activity/apkWithDefaultActivity.apk")

    val apkInfo = ApkInfo(apk.toFile(), "com.example.myapplication")

    val deployOptions = DeployOptions(emptyList(), "", true, true, false)
    deployer.fullDeploy(device, apkInfo, deployOptions, true, EmptyProgressIndicator())

    runStat.success()

    val runEvent = tracker.usages.single { it.studioEvent.kind == AndroidStudioEvent.EventKind.RUN_EVENT }.studioEvent.runEvent
    val launchTaskDetailList = runEvent.launchTaskDetailList
    assertThat(launchTaskDetailList).isNotEmpty()
    val mainTask = launchTaskDetailList.find { it.id == "DEPLOY" }!!
    assertThat(mainTask.artifactCount).isEqualTo(1) // We installed 1 apk

    val subtasks = launchTaskDetailList.filter { it.id.startsWith("DEPLOY.") }
    assertThat(subtasks).isNotEmpty()
  }
}
