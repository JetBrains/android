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
package com.android.tools.idea.run.deployment.liveedit

import com.android.ddmlib.Client
import com.android.ddmlib.IDevice
import com.android.sdklib.AndroidVersion
import com.android.testutils.MockitoKt
import com.android.testutils.MockitoKt.any
import com.android.testutils.waitForCondition
import com.android.testutils.VirtualTimeScheduler
import com.android.tools.analytics.TestUsageTracker
import com.android.tools.analytics.UsageTracker
import com.android.tools.deployer.AdbClient
import com.android.tools.deployer.Installer
import com.android.tools.deployer.TestLogger
import com.android.tools.deployer.tasks.LiveUpdateDeployer
import com.android.testutils.MockitoKt.whenever
import com.android.tools.deploy.proto.Deploy
import com.android.tools.idea.editors.liveedit.LiveEditApplicationConfiguration
import com.android.tools.idea.editors.liveedit.LiveEditService
import com.android.tools.idea.run.deployment.liveedit.LiveEditProjectMonitor.NUM_RECOMPOSITION_STATUS_POLLS_PER_EDIT
import com.android.tools.idea.run.deployment.liveedit.analysis.createKtFile
import com.android.tools.idea.run.deployment.liveedit.analysis.directApiCompileIr
import com.android.tools.idea.testing.AndroidProjectRule
import com.google.wireless.android.sdk.stats.LiveEditEvent
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import junit.framework.Assert
import junit.framework.Assert.assertTrue
import org.jetbrains.kotlin.psi.KtFile
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.spy
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@RunWith(JUnit4::class)
class LiveEditProjectMonitorTest {
  private lateinit var myProject: Project
  private val usageTracker = TestUsageTracker(VirtualTimeScheduler())

  private fun hasMetricStatus(status : LiveEditEvent.Status) = usageTracker.usages.any() {
    it.studioEvent.liveEditEvent.status == status
  }

  @get:Rule
  var projectRule = AndroidProjectRule.inMemory().withKotlin()

  @Before
  fun setUp() {
    UsageTracker.setWriterForTest(usageTracker)
    myProject = projectRule.project
    setUpComposeInProjectFixture(projectRule)
  }

  @After
  fun tearDown() {
    Assert.assertFalse(hasMetricStatus(LiveEditEvent.Status.UNKNOWN))
    UsageTracker.cleanAfterTesting()
  }

  @Test
  fun manualModeCompileError() {
    var monitor = LiveEditProjectMonitor(
      LiveEditService.getInstance(myProject), myProject);
    val file = projectRule.createKtFile("A.kt", "fun foo() : String { return 1}")

    val device: IDevice = MockitoKt.mock()
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    whenever(device.isEmulator).thenReturn(false)
    monitor.notifyAppDeploy("app", device, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }

    monitor.handleChangedMethods(myProject, listOf(file))
    monitor.doOnManualLETrigger()
    Assert.assertEquals(1, monitor.numFilesWithCompilationErrors())
  }

  @Test
  fun autoModeCompileSuccess() {
    val monitor = LiveEditProjectMonitor(
      LiveEditService.getInstance(myProject), myProject);
    val file = projectRule.fixture.configureByText("A.kt", "fun foo() : Int { return 1}") as KtFile

    // Fake a UpToDate Physical Device
    val device1: IDevice = MockitoKt.mock()
    MockitoKt.whenever(device1.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    MockitoKt.whenever(device1.isEmulator).thenReturn(false)
    monitor.notifyAppDeploy("app", device1, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }

    // Push A.class into the class cache and pretend we already modified it once already.
    monitor.irClassCache.update(projectRule.directApiCompileIr(file).values.first())
    monitor.liveEditDevices.update(device1, LiveEditStatus.UpToDate)

    monitor.processChangesForTest(myProject, listOf(file), LiveEditEvent.Mode.AUTO)
    Assert.assertEquals(0, monitor.numFilesWithCompilationErrors())

    val hasPhysicalDevice = usageTracker.usages.any() {
      it.studioEvent.liveEditEvent.targetDevice == LiveEditEvent.Device.PHYSICAL
    }

    Assert.assertTrue(hasPhysicalDevice)
  }

  @Test
  fun autoModeCompileError() {
    var monitor = LiveEditProjectMonitor(
      LiveEditService.getInstance(myProject), myProject);
    val file = projectRule.createKtFile("A.kt", "fun foo() : String { return 1}")

    val device: IDevice = MockitoKt.mock()
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    whenever(device.isEmulator).thenReturn(false)
    monitor.notifyAppDeploy("app", device, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }

    monitor.processChangesForTest(myProject, listOf(file), LiveEditEvent.Mode.AUTO)
    Assert.assertEquals(1, monitor.numFilesWithCompilationErrors())
  }

  @Test
  fun autoModeCompileErrorInOtherFile() {
    var monitor = LiveEditProjectMonitor(
      LiveEditService.getInstance(myProject), myProject);
    val file = projectRule.createKtFile("A.kt", "fun foo() : String { return 1}")

    val device: IDevice = MockitoKt.mock()
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    whenever(device.isEmulator).thenReturn(false)
    monitor.notifyAppDeploy("app", device, LiveEditApp(emptySet(), 32), listOf(file.virtualFile)) { true }

    monitor.processChangesForTest(myProject, listOf(file), LiveEditEvent.Mode.AUTO)

    var file2 = projectRule.fixture.configureByText("B.kt", "fun foo2() {}")
    monitor.updatePsiSnapshot(file2.virtualFile)
    monitor.processChangesForTest(myProject, listOf(file2), LiveEditEvent.Mode.AUTO)
    Assert.assertEquals(1, monitor.numFilesWithCompilationErrors())
  }

  /**
   * This test needs to be updated when multi-deploy is supported (its failure will signify so).
   */
  @Test
  fun testMultiDeploy() {
    val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject)
    val device1: IDevice = MockitoKt.mock()
    MockitoKt.whenever(device1.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    val device2: IDevice = MockitoKt.mock()
    MockitoKt.whenever(device2.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))

    val manager = monitor.liveEditDevices
    manager.addDevice(device1, LiveEditStatus.UpToDate)
    assertFalse(manager.isDisabled())

    // Make sure that adding a second device doesn't affect the state.
    manager.addDevice(device2, LiveEditStatus.DebuggerAttached)
    assertEquals(manager.getInfo(device1)!!.status, LiveEditStatus.UpToDate)
    assertEquals(manager.getInfo(device2)!!.status, LiveEditStatus.DebuggerAttached)

    // Ensure running on one device makes the other device's status NoMultiDeploy.
    monitor.notifyExecution(listOf(device2))
    assertEquals(manager.getInfo(device1)!!.status, LiveEditStatus.NoMultiDeploy)
    assertEquals(manager.getInfo(device2)!!.status, LiveEditStatus.DebuggerAttached)

    // Make sure running on the other device will force the status of other device to Disabled,
    // since showing NoMultiDeploy would be weird for the device we're deploying to. Ensure the
    // first device (that we're NOT deploying to) is now set to NoMultiDeploy.
    monitor.notifyExecution(listOf(device1))
    assertEquals(manager.getInfo(device1)!!.status, LiveEditStatus.Disabled)
    assertEquals(manager.getInfo(device2)!!.status, LiveEditStatus.NoMultiDeploy)

    // Make sure if we're running on both devices, then they preserve the previous state.
    manager.update(LiveEditStatus.UpToDate)
    monitor.notifyExecution(listOf(device1, device2))
    assertEquals(manager.getInfo(device1)!!.status, LiveEditStatus.UpToDate)
    assertEquals(manager.getInfo(device2)!!.status, LiveEditStatus.UpToDate)
  }

  @Test
  fun nonKotlin() {
    var monitor = LiveEditProjectMonitor(
      LiveEditService.getInstance(myProject), myProject);
    var file = projectRule.fixture.configureByText("A.java", "class A() { }")
    monitor.processChangesForTest(myProject, listOf(file), LiveEditEvent.Mode.AUTO)
    Assert.assertTrue(hasMetricStatus(LiveEditEvent.Status.NON_KOTLIN))
  }

  @Test
  fun recomposition() {
    val taskFinished = CountDownLatch(1)
    var monitor = LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject);
    val device: IDevice = MockitoKt.mock()
    MockitoKt.whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))

    val installer: Installer = LiveEditProjectMonitor.newInstaller(device)
    val adb = AdbClient(device, TestLogger())
    val deployer: LiveUpdateDeployer = MockitoKt.mock()

    MockitoKt.whenever(deployer.retrieveComposeStatus(any(), any(), any())).then {
      taskFinished.countDown()
      throw IOException("Fake IO Exception")
    }

    // Fake Deployment
    monitor.notifyAppDeploy("some.app", device, LiveEditApp(emptySet(), 32), emptyList()) { true }
    monitor.liveEditDevices.update(LiveEditStatus.UpToDate)
    monitor.scheduleErrorPolling(deployer, installer, adb, "some.app")
    taskFinished.await()

    // scheduleErrorPolling() fire off the first check 2 seconds after and continue in 2 seconds intervals.
    waitForCondition(2.toDuration(DurationUnit.SECONDS)) {
      val status = monitor.status(device)
      status.description.contains("IOException")
    }
  }

  /**
   * Make sure that 10 recomposition status retrieval request all at once doesn't trigger 10+ round trip to the device
   */
  @Test
  @Ignore("b/326255667")
  fun recompositionCheckRate() {
    // Force the test env to queue up 10 Live Edit recomposition status retrieval
    val numRecompositionRequested = NUM_RECOMPOSITION_STATUS_POLLS_PER_EDIT * 2
    val recompositionStatusRequestFinished = CountDownLatch(numRecompositionRequested)

    // We should only get a single failure.
    val taskComposeStatusFinished = CountDownLatch(1)

    val monitor = spy(LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject))
    val device: IDevice = MockitoKt.mock()
    MockitoKt.whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))

    val installer: Installer = LiveEditProjectMonitor.newInstaller(device)
    val adb = AdbClient(device, TestLogger())
    val deployer: LiveUpdateDeployer = MockitoKt.mock()

    var totalStatusRetrieve = 0
    MockitoKt.whenever(deployer.retrieveComposeStatus(any(), any(), any())).then {
      recompositionStatusRequestFinished.await() // Wait until all 10 request has been queued up before we return the one status.
      taskComposeStatusFinished.countDown()
      totalStatusRetrieve++
      throw IOException("Fake IO Exception")
    }

    // Fake Deployment
    monitor.notifyAppDeploy("some.app", device, LiveEditApp(emptySet(), 32), emptyList()) { true }
    monitor.liveEditDevices.update(LiveEditStatus.UpToDate)

    for (i in 1..numRecompositionRequested) {
      monitor.scheduleErrorPolling(deployer, installer, adb, "some.app")
      recompositionStatusRequestFinished.countDown()
    }

    recompositionStatusRequestFinished.await()
    taskComposeStatusFinished.await()

    assertEquals(1, totalStatusRetrieve)
  }

  /**
   * Make sure we get 5 recomposition status update.
   */
  @Test
  fun recompositionCheckCount() {
    // Force the test env to queue up 10 Live Edit recomposition status retrieval
    val numRecompositionRequested = NUM_RECOMPOSITION_STATUS_POLLS_PER_EDIT * 2
    val recompositionStatusRequestFinished = CountDownLatch(numRecompositionRequested)

    // We should only get 5 status update.
    val taskComposeStatusFinished = CountDownLatch(NUM_RECOMPOSITION_STATUS_POLLS_PER_EDIT)

    var totalStatusRetrieve = 0
    val monitor = object : LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject) {
      override fun updateEditableStatus(newStatus: LiveEditStatus) {
        recompositionStatusRequestFinished.await()
        totalStatusRetrieve++
        taskComposeStatusFinished.countDown()
      }
    }

    val device: IDevice = MockitoKt.mock()
    MockitoKt.whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))

    val installer: Installer = LiveEditProjectMonitor.newInstaller(device)
    val adb = AdbClient(device, TestLogger())
    val deployer: LiveUpdateDeployer = MockitoKt.mock()

    MockitoKt.whenever(deployer.retrieveComposeStatus(any(), any(), any())).thenReturn(listOf(Deploy.ComposeException.newBuilder().build()))
    // Fake Deployment
    monitor.notifyAppDeploy("some.app", device, LiveEditApp(emptySet(), 32), emptyList()) { true }
    monitor.liveEditDevices.update(LiveEditStatus.UpToDate)

    for (i in 1..numRecompositionRequested) {
      monitor.scheduleErrorPolling(deployer, installer, adb, "some.app")
      recompositionStatusRequestFinished.countDown()
    }

    recompositionStatusRequestFinished.await()
    taskComposeStatusFinished.await()

    assertEquals(NUM_RECOMPOSITION_STATUS_POLLS_PER_EDIT, totalStatusRetrieve)
  }

  @Test
  fun nonKotlinClassDiffer() {
    LiveEditApplicationConfiguration.getInstance().leTriggerMode = LiveEditService.Companion.LiveEditTriggerMode.ON_HOTKEY
    val appId = "com.test.app"
    val monitor = LiveEditProjectMonitor(LiveEditService.getInstance(myProject), myProject)
    val file = projectRule.fixture.configureByText("A.java", "class A() { }")

    val device: IDevice = MockitoKt.mock()
    val client: Client = MockitoKt.mock()
    whenever(device.version).thenReturn(AndroidVersion(AndroidVersion.VersionCodes.R))
    whenever(device.getClient(appId)).thenReturn(client)

    val ready = CountDownLatch(2)
    val needUpdate = CountDownLatch(4)
    val done = CountDownLatch(6)
    val statuses = mutableListOf<LiveEditStatus>()
    monitor.liveEditDevices.addListener {
      statuses.add(it[device]!!)
      ready.countDown()
      needUpdate.countDown()
      done.countDown()
    }

    monitor.notifyAppDeploy(appId, device, LiveEditApp(emptySet(), 30), listOf()) { true }
    assertTrue(ready.await(5000, TimeUnit.MILLISECONDS))

    ReadAction.run<Throwable> {
      monitor.fileChanged(file.virtualFile)
    }

    assertTrue(needUpdate.await(5000, TimeUnit.MILLISECONDS))

    monitor.onManualLETrigger()
    assertTrue(done.await(5000, TimeUnit.MILLISECONDS))
    assertTrue(statuses.last().description.startsWith(LiveEditUpdateException.Error.NON_KOTLIN.message))
  }
}
