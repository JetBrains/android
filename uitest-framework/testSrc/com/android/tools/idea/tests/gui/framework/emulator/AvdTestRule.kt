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
package com.android.tools.idea.tests.gui.framework.emulator

import com.android.ddmlib.AdbCommandRejectedException
import com.android.ddmlib.AdbInitOptions
import com.android.ddmlib.AndroidDebugBridge
import com.android.ddmlib.CollectingOutputReceiver
import com.android.ddmlib.DdmPreferences
import com.android.ddmlib.EmulatorConsole
import com.android.ddmlib.IDevice
import com.android.ddmlib.TimeoutException
import com.android.prefs.AndroidLocationsSingleton
import com.android.repository.testframework.FakeProgressIndicator
import com.android.resources.ScreenOrientation
import com.android.sdklib.devices.Device
import com.android.sdklib.devices.Hardware
import com.android.sdklib.devices.Screen
import com.android.sdklib.devices.Software
import com.android.sdklib.devices.State
import com.android.sdklib.internal.avd.AvdInfo
import com.android.sdklib.repository.AndroidSdkHandler
import com.android.testutils.TestUtils.getSdk
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.testutils.TestUtils.runningFromBazel
import com.android.tools.idea.avdmanager.AvdManagerConnection
import com.android.tools.idea.avdmanager.SystemImageDescription
import com.android.utils.FileUtils
import com.google.common.base.CharMatcher
import org.junit.Assert
import org.junit.rules.ExternalResource
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Meant to help set up an a running emulator in a Bazel sandbox when running UI tests
 * with Bazel.
 */
class AvdTestRule(private val avdSpec: AvdSpec) : ExternalResource() {
  companion object {
    fun buildAvdTestRule(specBuilder: () -> AvdSpec.Builder): AvdTestRule {
      val avdBuilder = specBuilder()

      // Sanitize name. The naming scheme here is stricter than normally allowed
      // since emulator binary has a strict naming convention for AVDs, and
      // the name will be easier to think about.
      avdBuilder.setAvdName(sanitizeString(avdBuilder.build().avdName))
      return AvdTestRule(avdBuilder.build())
    }

    private fun sanitizeString(dirty: String): String {
      val allowedCharMatcher = CharMatcher.inRange('a', 'z')
        .or(CharMatcher.inRange('A', 'Z'))
        .or(CharMatcher.inRange('0', '9'))

      val newname = allowedCharMatcher.retainFrom(dirty)
      return newname
    }

  }

  var myAvd: AvdInfo? = null
  var generatedSdkLocation: File? = null
    private set
  private var avdDevice: IDevice? = null
  private var emulatorProcess: Process? = null
  private var homeDirectory: File? = null

  override fun before() {
    super.before()

    var sdkLocation = getSdk().toFile()
    if (runningFromBazel()) {
      // ADB will try to make a .android directory under the user's home directory.
      // Mount a tmpfs on the home directory so ADB will succeed while running in the sandbox
      // Start by checking if we are the (fake) root user:
      System.getProperty("user.name", "notroot").let {
        if (it != "root") {
          throw IllegalStateException("Running as ${it} rather than root. Is \"requires-fakeroot\" a tag in the BUILD target?")
        }
      }

      // Check if we're underneath the directory we're about to mount over:
      val rootHomeDir = File(System.getProperty("user.home") ?: throw IllegalStateException("No home directory available!"))
      if (checkFileAncestry(rootHomeDir, File(System.getProperty("user.dir")))) {
        throw IllegalStateException("We were about to mount a tmpfs over a directory we were working under.")
      }

      val mountPb = ProcessBuilder("mount", "-t", "tmpfs", "none", rootHomeDir.canonicalPath).inheritIO()
      val mountProc = mountPb.start()
      if (!mountProc.waitFor(10, TimeUnit.SECONDS)) {
        // failed to mount
        throw RuntimeException("Unable to mount tmpfs to /root")
      }

      homeDirectory = rootHomeDir

      // copy the SDK to a writable area
      val tmpDir = File(System.getenv("TEST_TMPDIR"))
      val newSdkLocation = copySdkToTmp(sdkLocation, tmpDir)
      copyEmuAndImages(newSdkLocation)
      sdkLocation = newSdkLocation
    }
    // If we're not running in a Bazel environment, just assume the system images
    // and emulator are already available. This is the current behavior of tests
    // that use the emulator that are not running within Bazel.
    generatedSdkLocation = sdkLocation

    val sdkManager = AndroidSdkHandler.getInstance(AndroidLocationsSingleton, sdkLocation.toPath())
    val avdMan = AvdManagerConnection.getAvdManagerConnection(sdkManager)

    var avd: AvdInfo?
    avd = avdMan.getAvds(true).find {
      if (it.name == avdSpec.avdName) {
        if (it.abiType == avdSpec.systemImageSpec.abiType
          && it.androidVersion.apiLevel == avdSpec.systemImageSpec.apiLevel.toInt()) {
          return@find true
        } else {
          avdMan.deleteAvd(it)
        }
      }
      return@find false
    }
    if (avd == null) {
      avd = createAvd(sdkManager, avdMan) ?: throw IllegalArgumentException("Unable to create AVD with ${avdSpec}")
    }
    myAvd = avd

    val emulatorBinary = File(sdkLocation, "emulator/emulator")
    emulatorProcess = startAvd(emulatorBinary, avd)

    avdDevice = waitForBootComplete(setupAdb(sdkLocation), avd, 10, TimeUnit.MINUTES)

    // TODO: create snapshots?

    AndroidDebugBridge.terminate()
    AndroidDebugBridge.disconnectBridge()
  }

  private fun checkFileAncestry(possibleAncestor: File, descendant: File): Boolean {
    val resolvedAncestor = possibleAncestor.canonicalFile
    var fileTreeWalker: File? = descendant.canonicalFile

    if (resolvedAncestor.equals(fileTreeWalker)) {
      return true
    }

    while (fileTreeWalker != null && !resolvedAncestor.equals(fileTreeWalker)) {
      fileTreeWalker = fileTreeWalker.parentFile?.canonicalFile
    }

    return resolvedAncestor.equals(fileTreeWalker)
  }

  private fun copySdkToTmp(sdk: File, tmpDir: File): File {
    FileUtils.copyDirectoryToDirectory(sdk, tmpDir)
    return File(tmpDir, sdk.name)
  }

  /**
   * Pre-condition: running within Bazel
   */
  private fun copyEmuAndImages(sdkLocation: File) {
    val sysImg = resolveWorkspacePath("external/externsdk/system-images")
    val emu = resolveWorkspacePath("external/externsdk/emulator")

    Assert.assertTrue(Files.isDirectory(sysImg))
    Assert.assertTrue(Files.isDirectory(emu))

    FileUtils.copyDirectoryToDirectory(sysImg.toFile(), sdkLocation)
    FileUtils.copyDirectoryToDirectory(emu.toFile(), sdkLocation)
  }

  private fun createAvd(sdkManager: AndroidSdkHandler, avdManager: AvdManagerConnection): AvdInfo? {
    val deviceBuilder = Device.Builder()
    deviceBuilder.setName(avdSpec.avdName)
    deviceBuilder.setManufacturer("Google")

    val softwareConfig = Software()
    val apiLevel = avdSpec.systemImageSpec.apiLevel.toInt()
    softwareConfig.minSdkLevel = apiLevel
    softwareConfig.maxSdkLevel = apiLevel
    deviceBuilder.addSoftware(softwareConfig)

    val screen = Screen()
    screen.xDimension = 450
    screen.yDimension = 800

    val hardware = Hardware()
    hardware.screen = screen

    val deviceState = State()
    deviceState.isDefaultState = true
    deviceState.orientation = ScreenOrientation.PORTRAIT
    deviceState.hardware = hardware
    deviceBuilder.addState(deviceState)

    val deviceConfig = deviceBuilder.build()
    val systemImageMan = sdkManager.getSystemImageManager(FakeProgressIndicator())

    val availableApiImages = systemImageMan.images.filter {
      it.androidVersion.apiLevel == apiLevel && it.primaryAbiType.lowercase() == avdSpec.systemImageSpec.abiType.lowercase()
    }
    if (availableApiImages.isEmpty()) {
      throw IllegalArgumentException("No available system images for API level ${apiLevel}")
    }

    val systemImage = availableApiImages.first()
    avdManager.getAvds(true)
    return avdManager.createOrUpdateAvd(
      null,
      avdSpec.avdName,
      deviceConfig,
      SystemImageDescription(systemImage),
      ScreenOrientation.PORTRAIT,
      false,
      null,
      null,
      HashMap<String, String>(),
      null,
      true
    )
  }

  private fun startAvd(emulatorBinary: File, avdInfo: AvdInfo): Process {
    val pb = ProcessBuilder()
    pb.command(listOf(emulatorBinary.absolutePath, "-no-window", "-avd", avdInfo.name))
    val env = pb.environment()
    env["HOME"] = AndroidLocationsSingleton.userHomeLocation!!.toString()
    env["ANDROID_AVD_HOME"] = AndroidLocationsSingleton.avdLocation.toString()
//    env["DISPLAY"]  = ":0"
    pb.inheritIO()
    return pb.start()
  }

  private fun setupAdb(sdkLocation: File): AndroidDebugBridge {
    AndroidDebugBridge.init(AdbInitOptions.builder()
                              .withEnv("HOME", AndroidLocationsSingleton.userHomeLocation!!.toString())
                              .build())
    val adbBinary = File(sdkLocation, "platform-tools/adb")

    // Wait for ADB to start with 30 seconds. Slow machine can cause ADB to take very long time to start
    DdmPreferences.setTimeOut(30000)
    return AndroidDebugBridge.createBridge(adbBinary.absolutePath, false)!!
  }

  @Throws(InterruptedException::class, IOException::class, java.util.concurrent.TimeoutException::class)
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
      } catch (e: Exception) {
        when (e) {
          is TimeoutException,
          is AdbCommandRejectedException,
          is ConnectException -> {
            // Ignore. Do nothing and try again
          }
          else -> throw e
        }
      }

      if (bootcomplete != "1") {
        Thread.sleep(1000)
      } else {
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
        while (knownDevices.isNotEmpty()) {
          val device = knownDevices.poll()
          if (device != null && device.isEmulator) {
            val console = EmulatorConsole.getConsole(device)
            val avdName = console?.avdName
            console?.close()
            if (avdInfo.name == avdName) {
              ourAvdDevice = device
            }
          }
        }
        currentTime = System.currentTimeMillis()
        Thread.sleep(100)
        if(knownDevices.isEmpty()) {
          knownDevices.addAll(adb.devices)
        }
      }
    }
    finally {
      // Unregister the listener to avoid memory leaks
      AndroidDebugBridge.removeDeviceChangeListener(deviceListener)
    }

    return ourAvdDevice ?: throw java.util.concurrent.TimeoutException("AVD did not connect to ADB in the timeout given")
  }

  override fun after() {
    try {
      // Emulator was started after mounting tmpfs on /root, so we should unmount tmpfs from
      // the home directory only after stopping the emulator.
      emulatorProcess?.destroy()
      emulatorProcess?.waitFor(30, TimeUnit.SECONDS)
      val mountedHomeDir = homeDirectory
      if (runningFromBazel() && mountedHomeDir != null) {
        ProcessBuilder("umount", mountedHomeDir.canonicalPath).inheritIO().start().waitFor(10, TimeUnit.SECONDS)
      }
    } catch (interrupted: InterruptedException) {
      Thread.currentThread().interrupt()
    } finally {
      emulatorProcess?.destroyForcibly()
    }
  }
}
