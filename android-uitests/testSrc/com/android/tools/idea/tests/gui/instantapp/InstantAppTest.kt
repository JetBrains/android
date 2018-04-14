/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.instantapp

import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.Client
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.EmulatorConsole
import com.android.ddmlib.IDevice
import com.android.ddmlib.NullOutputReceiver
import com.android.ddmlib.SplitApkInstaller
import com.android.ddmlib.TimeoutException
import com.android.repository.testframework.FakeProgressIndicator
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.TestUtils
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.SystemImageDescription
import com.android.tools.idea.tests.gui.framework.GuiTests
import com.android.tools.idea.tests.gui.framework.RunIn
import com.android.tools.idea.tests.gui.framework.TestGroup
import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.io.FileUtil
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.io.File
import java.lang.Exception
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
class InstantAppTest {
  @Rule @JvmField val tmpDir = object : TemporaryFolder() {
    override fun after() {
      if (!deleteWithoutFollowingSymlinks(root)) {
        // failed to delete!
        throw RuntimeException("Unable to delete file!")
      }
      super.after()
    }

    private fun deleteWithoutFollowingSymlinks(fileToDelete: File): Boolean {
      // If fileToDelete is a symlink, don't iterate through its children.
      // The symlink can point outside of the temporary directory's tree,
      // which can cause us to delete files we don't want deleted!
      if (!Files.isSymbolicLink(fileToDelete.toPath())) {
        fileToDelete.listFiles()?.forEach {
          deleteWithoutFollowingSymlinks(it)
        }
      }
      return fileToDelete.delete()
    }
  }

  /**
   * The sanity UI tests are moving away from using real emulators. This
   * test ensures that we are still able to deploy instant apps to an AVD
   * without using the UI.
   */
  @Test
  @RunIn(TestGroup.SANITY_NO_UI)
  fun deployToRealEmulator() {
    val instantAppProject = File(GuiTests.getTestProjectsRootDirPath(), "TopekaInstantApp")
    val projectTmpDir = tmpDir.newFolder("TopekaInstantApp")
    FileUtil.copyDir(instantAppProject, projectTmpDir)
    val prebuiltApks = File(projectTmpDir, "prebuilt").listFiles().asList()

    val sdkLocation = TestUtils.getSdk()
    val adb = setupAdb(sdkLocation)
    val avdInfo = setupAvd(sdkLocation)

    val emulatorBinary = File(sdkLocation, "emulator/emulator")
    val emuProc = startAvd(emulatorBinary, avdInfo)
    try {
      val device = waitForBootComplete(adb, avdInfo, 2, TimeUnit.MINUTES)

      SplitApkInstaller.create(device, prebuiltApks, true, listOf("-t", "--ephemeral")).install(1, TimeUnit.MINUTES)
      device.executeShellCommand(
        "am start -a android.intent.action.VIEW "
        + "-c android.intent.category.BROWSABLE "
        + "-d http://topeka.samples.androidinstantapps.com/",
        NullOutputReceiver())

      assertThat(waitForAppClient(device, "com.google.samples.apps.topeka", 10, TimeUnit.SECONDS))
        .isNotNull()
    }
    finally {
      stopAvd(emuProc)
      AndroidDebugBridge.disconnectBridge()
      AndroidDebugBridge.terminate()
    }
  }

  private fun setupAdb(sdkLocation: File): AndroidDebugBridge {
    AndroidDebugBridge.initIfNeeded(true)
    val adbBinary = File(sdkLocation, "platform-tools/adb")
    val adbNullable = AndroidDebugBridge.createBridge(adbBinary.absolutePath, false)
    assertThat(adbNullable).isNotNull()
    return adbNullable!!
  }

  // TODO: move this to an AVD setup target?
  private fun setupAvd(sdkLocation: File): AvdInfo {

    // Create a real AVD:
    val sdkManager = AndroidSdkHandler.getInstance(sdkLocation)
    val avdMan = AvdManagerConnection.getAvdManagerConnection(sdkManager)

    avdMan.getAvds(true).forEach {
      avdMan.deleteAvd(it)
    }

    val deviceBuilder = Device.Builder()
    deviceBuilder.setName("Oreo-26-x86")
    deviceBuilder.setManufacturer("Google")

    val softwareConfig = Software()
    softwareConfig.minSdkLevel = 26
    softwareConfig.maxSdkLevel = 26
    deviceBuilder.addSoftware(softwareConfig)

    val screen = Screen()
    screen.xDimension = 1080
    screen.yDimension = 1920

    val hardware = Hardware()
    hardware.screen = screen

    val deviceState = State()
    deviceState.isDefaultState = true
    deviceState.orientation = ScreenOrientation.PORTRAIT
    deviceState.hardware = hardware
    deviceBuilder.addState(deviceState)

    val deviceConfig = deviceBuilder.build()
    val systemImageMan = sdkManager.getSystemImageManager(FakeProgressIndicator())

    val api26Images = systemImageMan.images.filter {
      it.androidVersion.apiLevel == 26
    }
    assertThat(api26Images).isNotEmpty()

    val systemImage = api26Images.first()
    avdMan.getAvds(true)
    val avdInfoNullable = avdMan.createOrUpdateAvd(
      null,
      "Oreo-26-x86",
      deviceConfig,
      SystemImageDescription(systemImage),
      ScreenOrientation.PORTRAIT,
      false,
      null,
      null,
      HashMap<String, String>(),
      false,
      true
    )
    assertThat(avdInfoNullable).isNotNull()
    return avdInfoNullable!!
  }

  private fun startAvd(emulatorBinary: File, avdInfo: AvdInfo): Process {
    val pb = ProcessBuilder()
    pb.command(listOf(emulatorBinary.absolutePath, "-no-window", "-avd", avdInfo.name))
    return pb.start()
  }

  private fun stopAvd(process: Process) {
    process.destroy()
    try {
      process.waitFor(30, TimeUnit.SECONDS)
    }
    catch (interrupt: InterruptedException) {
      process.destroyForcibly()
      Thread.currentThread().interrupt()
    }
  }

  /**
   * Waits for an AVD to finish booting.
   *
   * @throws InterruptedException if this thread is interrupted while waiting for boot to complete
   * @return an IDevice representing an AVD that has finished booting
   */
  private fun waitForBootComplete(adb: AndroidDebugBridge, avdInfo: AvdInfo, timeout: Long, timeUnit: TimeUnit): IDevice {
    val endtime = System.currentTimeMillis() + timeUnit.toMillis(timeout)

    val avd: IDevice = waitForDeviceConnected(adb, avdInfo, endtime)

    while (System.currentTimeMillis() < endtime) {
      val outputLatch = CountDownLatch(1)
      val receiver = CollectingOutputReceiver(outputLatch)

      val bootcomplete = try {
        avd.executeShellCommand("getprop dev.bootcomplete", receiver)
        outputLatch.await()
        receiver.output.trim()
      }
      catch (e: Exception) {
        when (e) {
          is TimeoutException,
          is AdbCommandRejectedException -> {
            // Ignore. Do nothing and try again
          }
          else -> throw e
        }
      }

      if (bootcomplete != "1") {
        Thread.sleep(1000)
      }
      else {
        break
      }
    }

    if (System.currentTimeMillis() >= endtime) {
      throw java.util.concurrent.TimeoutException("AVD was not ready within the given timeout")
    }

    return avd
  }

  private fun waitForDeviceConnected(adb: AndroidDebugBridge, avdInfo: AvdInfo, endtime: Long): IDevice {
    val knownDevices = LinkedBlockingQueue<IDevice>()

    // Store a reference to the listener so we can unregister it later to avoid memory leaks
    val deviceListener = object : AndroidDebugBridge.IDeviceChangeListener {
      override fun deviceConnected(device: IDevice) {
        knownDevices.put(device)
      }

      override fun deviceDisconnected(device: IDevice) {}
      override fun deviceChanged(device: IDevice, changeMask: Int) {}
    }

    // Collect all connected devices. It's okay to have some devices duplicated in our queue
    AndroidDebugBridge.addDeviceChangeListener(deviceListener)
    for (device in adb.devices) {
      knownDevices.offer(device)
    }

    var ourAvdDevice: IDevice? = null
    try {
      var currentTime = System.currentTimeMillis()
      while (ourAvdDevice == null && currentTime < endtime) {
        val device = knownDevices.poll(endtime - currentTime, TimeUnit.MILLISECONDS)
        if (device != null && device.isEmulator) {
          val emuConsole = EmulatorConsole.getConsole(device)
          if (avdInfo.name == emuConsole?.avdName) {
            ourAvdDevice = device
          }
        }
        currentTime = System.currentTimeMillis()
      }
    }
    finally {
      // Unregister the listener to avoid memory leaks
      AndroidDebugBridge.removeDeviceChangeListener(deviceListener)
    }

    return ourAvdDevice ?: throw java.util.concurrent.TimeoutException("AVD did not connect to ADB in the timeout given")
  }

  private fun waitForAppClient(device: IDevice, appId: String, timeout: Long, unit: TimeUnit): Client? {
    val endtime = System.currentTimeMillis() + unit.toMillis(timeout)

    var client: Client? = device.getClient(appId)
    while (client == null && System.currentTimeMillis() < endtime) {
      try {
        Thread.sleep(10)
      }
      catch (interrupted: InterruptedException) {
        // Exit loop early. We've been interrupted so we need to get out quick!
        Thread.currentThread().interrupt()
        return null
      }

      client = device.getClient(appId)
    }

    return client
  }
}