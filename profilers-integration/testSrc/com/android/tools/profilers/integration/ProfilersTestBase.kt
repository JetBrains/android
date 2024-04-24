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
package com.android.tools.profilers.integration

import com.android.tools.asdriver.tests.Adb
import com.android.tools.asdriver.tests.AndroidProject
import com.android.tools.asdriver.tests.AndroidProjectWithoutGradle
import com.android.tools.asdriver.tests.AndroidStudio
import com.android.tools.asdriver.tests.AndroidSystem
import com.android.tools.asdriver.tests.Emulator
import com.android.tools.asdriver.tests.MavenRepo
import com.android.tools.asdriver.tests.MemoryDashboardNameProviderWatcher
import org.junit.Rule
import java.util.concurrent.TimeUnit
import java.util.logging.Logger
import kotlin.time.Duration.Companion.seconds

/**
 * This is the base test class for all unit tests in this project.
 * It provides common functionality for all profiler integration tests,
 * such as setting up the android studio, starting and waiting for emulator boot-up,
 * syncing and building project and some common profiler specific reusable execute actions
 * and log verifications.
 *
 * All Profiler integration tests should extend this class.
 */
open class ProfilersTestBase {

  protected fun getLogger(): Logger {
    return Logger.getLogger(ProfilersTestBase::class.java.getName())
  }

  private val testProjectMinAppPath = "tools/adt/idea/profilers-integration/testData/minapp"
  private val testMinAppRepoManifest = "tools/adt/idea/profilers-integration/minapp_deps.manifest"
  private val testProjectApk = "tools/adt/idea/profilers-integration/testData/helloworldapk"

  @JvmField
  @Rule
  val system: AndroidSystem = AndroidSystem.standard()

  @JvmField
  @Rule
  var watcher = MemoryDashboardNameProviderWatcher()

  protected fun sessionBasedProfiling(systemImage: Emulator.SystemImage,
                                      testFunction: ((studio: AndroidStudio, adb: Adb) -> Unit)) {
    // Disabling the profiler task-based ux and verbose logs behind the flag.
    system.installation.addVmOption("-Dprofiler.task.based.ux=false")
    system.installation.addVmOption("-Dprofiler.testing.mode=true")

    // Open android project, and set a fixed distribution
    val project = AndroidProject(testProjectMinAppPath)

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo(testMinAppRepoManifest))

    system.runAdb { adb ->
      system.runEmulator(systemImage) { emulator ->
        system.runStudio(project, watcher.dashboardName) { studio ->
          // Waiting for sync and build.
          studio.waitForSync()
          studio.waitForIndex()
          // Assume project build will be triggered by `testFunction` if needed.
          // Waiting for emulator to boot up.
          emulator.waitForBoot()
          adb.waitForDevice(emulator)

          getLogger().info("Test set-up completed, invoking the test function.")
          // Test Function or test steps to be executed.
          testFunction.invoke(studio, adb)
        }
      }
    }
  }

  protected fun taskBasedProfiling(systemImage: Emulator.SystemImage,
                                   deployApp: Boolean,
                                   testFunction: ((studio: AndroidStudio, adb: Adb) -> Unit)) {
    // Enabling profiler task-based ux and verbose logs behind the flag.
    system.installation.addVmOption("-Dprofiler.task.based.ux=true")
    system.installation.addVmOption("-Dprofiler.testing.mode=true")

    // Open android project, and set a fixed distribution
    val project = AndroidProject(testProjectMinAppPath)

    // Create a maven repo and set it up in the installation and environment
    system.installRepo(MavenRepo(testMinAppRepoManifest))

    system.runAdb { adb ->
      system.runEmulator(systemImage) { emulator ->
        system.runStudio(project, watcher.dashboardName) { studio ->
          // Waiting for sync and build.
          studio.waitForSync()
          studio.waitForIndex()
          // Assume project build will be triggered by `testFunction` if needed.
          // Waiting for emulator to boot up.
          emulator.waitForBoot()
          adb.waitForDevice(emulator)

          if (deployApp) {
            deployApp(studio, adb)
            invokeProfilerToolWindow(studio)
            waitForProfilerDeviceConnection()
            getLogger().info("App Deployment is completed. Profiler tool window opened. Transport Pipeline is successful")
          }

          getLogger().info("Test set-up completed, starting the test case / invoking test function.")
          Thread.sleep(2000)
          // Test Function or test steps to be executed.
          testFunction.invoke(studio, adb)
        }
      }
    }
  }

  protected fun profileAppUsingApk(systemImage: Emulator.SystemImage,
                                   enableTaskBasedProfiling: Boolean,
                                   testFunction: ((studio: AndroidStudio, adb: Adb) -> Unit)) {
    system.installation.addVmOption("-Dprofiler.testing.mode=true")
    if (enableTaskBasedProfiling) {
      system.installation.addVmOption("-Dprofiler.task.based.ux=true")
    } else {
      system.installation.addVmOption("-Dprofiler.task.based.ux=false")
    }
    // Running an APK directly means that Android Studio won't try setting up the JDK table, and
    // even if it did, it would do so with the latest API level. Both of those issues would cause
    // failures in this test, so we force auto-creation at the API level that we expect.
    system.installation.addVmOption("-Dtesting.android.platform.to.autocreate=31")

    system.installation.setGlobalSdk(system.sdk)

    val project = AndroidProjectWithoutGradle(testProjectApk)

    system.runAdb { adb ->
      system.runEmulator(systemImage) { emulator ->
        getLogger().info("Waiting for boot")
        emulator.waitForBoot()
        getLogger().info("Waiting for device")
        adb.waitForDevice(emulator)

        system.runStudioFromApk(project) { studio ->
          studio.waitForIndex()
          getLogger().info("Finished waiting for index");
          studio.waitForProjectInit();

          getLogger().info("Test set-up completed, starting the test case / invoking test function.")
          testFunction.invoke(studio, adb)
        }
      }
    }
  }

  protected fun verifyIdeaLog(regexText: String, timeOut: Long) {
    system.installation.ideaLog.waitForMatchingLine(
      regexText,
      timeOut,
      TimeUnit.SECONDS)
  }

  protected fun invokeProfilerToolWindow(studio: AndroidStudio) {
    studio.showToolWindow("Android Profiler")
    waitForProfilerTaskBasedToolWindowToBeActivated(studio)
  }

  protected fun waitForProfilerTaskBasedToolWindowToBeActivated(studio: AndroidStudio) {
    studio.waitForComponentByClass("TaskHomeTabComponent")
  }

  protected fun waitForProfilerDeviceConnection() {
    verifyIdeaLog(".*TransportProxy\\s+successfully\\s+created\\s+for\\s+device\\:\\s+emulator\\-5554", 60)
  }

  protected fun deployApp(studio: AndroidStudio, adb: Adb) {
    studio.executeAction("Run")
    waitForAppToBeDeployed(adb, ".*Hello Minimal World!.*")
  }

  protected fun waitForAppToBeDeployed(adb: Adb, regex: String) {
    adb.runCommand("logcat") {
      waitForLog(regex, 300.seconds);
    }
  }

  protected fun profileWithCompleteData(studio: AndroidStudio, adb:Adb) {
    studio.executeAction("Android.ProfileWithCompleteData")

    adb.runCommand("logcat") {
      waitForLog(".*Hello Minimal World!.*", 300.seconds);
    }
  }

  protected fun profileWithLowOverhead(studio: AndroidStudio, adb: Adb) {
    studio.executeAction("Android.ProfileWithLowOverhead")

    adb.runCommand("logcat") {
      waitForLog(".*Hello Minimal World!.*", 180.seconds);
    }
  }

  protected fun stopProfilingSession(studio: AndroidStudio) {
    studio.executeAction("Android.StopProfilingSession")
    verifyIdeaLog(".*PROFILER\\:\\s+Session\\s+stopped.*support\\s+level\\s+\\=.*", 600)
  }

  protected fun startSystemTrace(studio: AndroidStudio) {
    studio.executeAction("Android.StartSystemTrace")
  }

  protected fun startCallstackSample(studio: AndroidStudio) {
    studio.executeAction("Android.StartCallstackSample")
  }

  protected fun stopCpuCapture(studio: AndroidStudio) {
    studio.executeAction("Android.StopCpuCapture")
  }

  protected fun startHeapDump(studio: AndroidStudio) {
    studio.executeAction("Android.StartHeapDump")
  }

  protected fun startNativeAllocations(studio: AndroidStudio) {
    studio.executeAction("Android.StartNativeAllocations")
  }

  protected fun stopNativeAllocations(studio: AndroidStudio) {
    studio.executeAction("Android.StopNativeAllocations")
  }

  protected fun selectSystemTraceTask(studio: AndroidStudio) {
    studio.executeAction("Android.SelectSystemTraceTask")
  }

  protected fun selectHeapDumpTask(studio: AndroidStudio) {
    studio.executeAction("Android.SelectHeapDumpTask")
  }

  protected fun selectCallstackSampleTask(studio: AndroidStudio) {
    studio.executeAction("Android.SelectCallstackSampleTask")
  }

  protected fun selectNativeAllocationsTask(studio: AndroidStudio) {
    studio.executeAction("Android.SelectNativeAllocationsTask")
  }

  protected fun selectLiveViewTask(studio: AndroidStudio) {
    studio.executeAction("Android.SelectLiveViewTask")
  }

  protected fun selectJavaKotlinMethodRecordingTask(studio: AndroidStudio) {
    studio.executeAction("Android.SelectJavaKotlinMethodRecording")
  }

  protected fun setRecordingTypeToSampling(studio: AndroidStudio) {
    studio.executeAction("Android.SetRecordingTypeToSampling")
  }

  protected fun selectDevice(studio: AndroidStudio) {
    studio.executeAction("Android.ProfilerSelectDevice")
  }

  protected fun selectProcess(studio: AndroidStudio) {
    studio.executeAction("Android.ProfilerSelectProcess")
  }

  protected fun startTask(studio: AndroidStudio) {
    studio.executeAction("Android.StartProfilerTask")
  }

  protected fun stopTask(studio: AndroidStudio) {
    studio.executeAction("Android.StopProfilerTask")
  }

  protected fun setProfilingStartingPointToNow(studio: AndroidStudio) {
    studio.executeAction("Android.SetProfilingStartingPointToNow")
  }

  protected fun setProfilingStartingPointToProcessStart(studio: AndroidStudio) {
    studio.executeAction("Android.SetProfilingStartingPointToProcessStart")
  }

  protected fun profileAction(studio: AndroidStudio) {
    studio.executeAction("Android.Profile")
  }
}