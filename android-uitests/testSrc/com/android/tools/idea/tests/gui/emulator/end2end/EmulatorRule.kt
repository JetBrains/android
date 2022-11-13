/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.emulator.end2end

import com.android.emulator.control.EmulatorStatus
import com.android.prefs.AndroidLocationsSingleton
import com.android.testutils.TestUtils
import com.android.testutils.TestUtils.getSdk
import com.android.testutils.TestUtils.resolveWorkspacePath
import com.android.tools.idea.streaming.emulator.EmptyStreamObserver
import com.android.tools.idea.streaming.emulator.EmulatorController
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionState
import com.android.tools.idea.streaming.emulator.EmulatorController.ConnectionStateListener
import com.android.tools.idea.streaming.emulator.RunningEmulatorCatalog
import com.android.tools.idea.streaming.emulator.readKeyValueFile
import com.google.common.util.concurrent.SettableFuture
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.UnixProcessManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.io.FileUtil
import com.intellij.testFramework.TemporaryDirectory
import org.jetbrains.android.sdk.AndroidSdkUtils
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.IOException
import java.lang.Long.min
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

val COMMAND_PARAMETERS_EMBEDDED = listOf("-qt-hide-window", "-grpc-use-token", "-idle-grpc-timeout", "300")

/**
 * Test rule for launching a real Android emulator.
 */
class EmulatorRule(val commandParameters: List<String> = COMMAND_PARAMETERS_EMBEDDED) : TestRule {
  lateinit var avdId: String
  private var emulatorProcess: Process? = null
  private var processId = 0
  private lateinit var avdFolder: Path
  private var nullableController: EmulatorController? = null
  private val tempDirectory = TemporaryDirectory()
  private val emulatorResource = object : ExternalResource() {

    override fun before() {
      super.before()
      val adbBinary = getAdbBinary()
      check(Files.exists(adbBinary))
      check(System.getProperty(AndroidSdkUtils.ADB_PATH_PROPERTY) == null)
      System.setProperty(AndroidSdkUtils.ADB_PATH_PROPERTY, adbBinary.toString())

      val emulatorCatalog = RunningEmulatorCatalog.getInstance()
      val root = Files.createDirectories(tempDirectory.newPath())
      val registrationDirectory = Files.createDirectories(root.resolve("avd/running"))
      emulatorCatalog.overrideRegistrationDirectory(registrationDirectory)
      val homeFolder = AndroidLocationsSingleton.userHomeLocation!!
      val avdHome = Files.createDirectories(homeFolder.resolve(".android/avd"))
      createAvd(avdHome)
      val command = EmulatorLauncher().apply {
        exePath = getEmulatorBinary().toString()
        addParameter("@$avdId")
        addParameters(commandParameters)
        if (TestUtils.runningFromBazel()) {
          // Redefine the home directory of the emulator process if running under Bazel.
          environment["HOME"] = homeFolder.toString()
        }
        environment["ANDROID_AVD_HOME"] = avdHome.toString()
        environment["ANDROID_SDK_ROOT"] = getSdk().toString()
        environment["XDG_RUNTIME_DIR"] = registrationDirectory.parent.parent.toString()
      }
      val process = command.createProcess()
      emulatorProcess = process
      processId = UnixProcessManager.getProcessId(process)
    }

    override fun after() {
      emulatorProcess?.destroyForcibly() // Use forceful termination for speed.
      emulatorProcess?.waitFor(2, TimeUnit.SECONDS)
      emulatorProcess = null

      val emulatorCatalog = RunningEmulatorCatalog.getInstance()
      emulatorCatalog.overrideRegistrationDirectory(null)
      deleteAvd()

      System.clearProperty(AndroidSdkUtils.ADB_PATH_PROPERTY)
    }
  }

  override fun apply(base: Statement, description: Description): Statement {
    val testName = description.methodName ?: description.className
    avdId = FileUtil.sanitizeFileName(testName)
    return tempDirectory.apply(emulatorResource.apply(base, description), description)
  }

  private val emulatorController: EmulatorController
    @Throws(TimeoutException::class)
    get() {
      var controller = nullableController
      if (controller != null) {
        return controller
      }

      val deadline = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(10)
      while (true) {
        val catalog = RunningEmulatorCatalog.getInstance()
        val controllers = catalog.updateNow().get()
        controller = controllers.find { it.emulatorId.registrationFileName == "pid_$processId.ini" }
        if (controller != null) {
          nullableController = controller
          return controller
        }
        val timeLeft = deadline - System.currentTimeMillis()
        if (timeLeft <= 0) {
          throw TimeoutException()
        }
        Thread.sleep(min(100, timeLeft))
      }
    }

  /**
   * Waits until establishing a gRPC connection to the emulator.
   */
  @Throws(TimeoutException::class)
  fun waitUntilConnected(timeout: Long, timeUnit: TimeUnit) {
    val controller = emulatorController
    val completion = SettableFuture.create<Boolean>()
    val listener = object : ConnectionStateListener {
      override fun connectionStateChanged(emulator: EmulatorController, connectionState: ConnectionState) {
        if (connectionState == ConnectionState.CONNECTED) {
          completion.set(true)
        }
        else if (connectionState == ConnectionState.DISCONNECTED) {
          completion.setException(RuntimeException("Disconnected from the emulator"))
        }
      }
    }
    controller.addConnectionStateListener(listener)
    if (controller.connectionState == ConnectionState.CONNECTED) {
      completion.set(true)
    }

    try {
      completion.get(timeout, timeUnit)
    }
    catch (e: TimeoutException) {
      completion.cancel(true)
      throw e
    }
    finally {
      controller.removeConnectionStateListener(listener)
    }
  }

  /**
   * Waits until the emulator is booted.
   */
  @Throws(TimeoutException::class)
  fun waitUntilBooted(timeout: Long, timeUnit: TimeUnit) {
    val deadline = System.currentTimeMillis() + timeUnit.toMillis(timeout)
    waitUntilConnected(timeout, timeUnit)

    val bootTimeout = deadline - System.currentTimeMillis()
    val controller = emulatorController
    val completion = SettableFuture.create<Boolean>()
    val statusReceiver = object : EmptyStreamObserver<EmulatorStatus>() {
      override fun onNext(response: EmulatorStatus) {
        if (response.booted) {
          completion.set(true)
        }
        else if (!completion.isDone) {
          ApplicationManager.getApplication().executeOnPooledThread {
            try {
              completion.get(100, TimeUnit.MILLISECONDS)
            }
            catch (_: TimeoutException) {
              controller.getStatus(this)
            }
            catch (_: InterruptedException) {
            }
            catch (_: CancellationException) {
            }
          }
        }
      }

      override fun onError(t: Throwable) {
        completion.setException(t)
      }
    }
    controller.getStatus(statusReceiver)

    try {
      completion.get(bootTimeout, TimeUnit.MILLISECONDS)
    }
    catch (e: TimeoutException) {
      completion.cancel(true)
      throw e
    }
  }

  private fun createAvd(avdHome: Path) {
    val systemImageDir = getSystemImage()
    val propertiesFile = systemImageDir.resolve("source.properties")
    val properties = readKeyValueFile(propertiesFile, null) ?: throw IllegalStateException("Error reading $propertiesFile")
    val apiLevel = properties["AndroidVersion.ApiLevel"] ?:
                   throw IllegalStateException("Unable to determine the API level of the system image")
    val abiType = properties["SystemImage.Abi"] ?: throw IllegalStateException("Unable to determine ABI of the system image")
    avdFolder = avdHome.resolve("$avdId.avd")
    Files.createDirectories(avdFolder)

    val emuIni = """
      avd.ini.encoding=UTF-8
      path=$avdFolder
      path.rel=avd/$avdId.avd
      target=android-$apiLevel
      """.trimIndent()
    Files.write(avdHome.resolve("$avdId.ini"), emuIni.toByteArray())

    val configIni = """
      PlayStore.enabled=false
      abi.type=$abiType
      avd.ini.encoding=UTF-8
      hw.accelerometer=yes
      hw.audioInput=yes
      hw.battery=yes
      hw.cpu.arch=$abiType
      hw.dPad=no
      hw.device.hash2=MD5:524882cfa9f421413193056700a29392
      hw.device.manufacturer=Google
      hw.device.name=pixel
      hw.gps=yes
      hw.keyboard=yes
      hw.lcd.density=480
      hw.lcd.height=1920
      hw.lcd.width=1080
      hw.mainKeys=no
      hw.sdCard=yes
      hw.sensors.orientation=yes
      hw.sensors.proximity=yes
      hw.trackBall=no
      image.sysdir.1=$systemImageDir
      """.trimIndent()
    Files.write(avdFolder.resolve("config.ini"), configIni.toByteArray())
  }

  private fun deleteAvd() {
    Files.deleteIfExists(avdFolder.resolveSibling("$avdId.ini"))
    FileUtil.delete(avdFolder)
  }

  private class EmulatorLauncher : GeneralCommandLine() {
    @Throws(IOException::class)
    override fun startProcess(escapedCommands: List<String>): Process {
      val builder = ProcessBuilder(escapedCommands)
      setupEnvironment(builder.environment())
      builder.inheritIO()
      return buildProcess(builder).start()
    }
  }
}

fun getAdbBinary(): Path {
  return resolveWorkspacePath("prebuilts/studio/sdk/linux/platform-tools/adb")
}

private fun getEmulatorBinary(): Path {
  return resolveWorkspacePath("prebuilts/android-emulator/linux-x86_64/emulator")
}

private fun getSystemImage(): Path {
  if (TestUtils.runningFromBazel()) {
    // Bazel environment.
    return resolveWorkspacePath("external/system_image_latest_default_x86_64")
  }

  // IDE environment.
  val home = System.getProperty("user.home") ?: throw IllegalStateException("Failed to find user home directory")
  val homeDir = Paths.get(home)
  val systemImages = homeDir.resolve("Android/Sdk/system-images")
  check(Files.exists(systemImages)) { "The $systemImages directory doesn't exist" }
  val latestPlatform = TestUtils.getLatestAndroidPlatform()
  val latestPlatformDir = systemImages.resolve(latestPlatform)
  for (apis in arrayOf("google_apis_playstore", "google_apis", "default")) {
    val apisDir = latestPlatformDir.resolve(apis)
    for (abi in arrayOf("x86_64", "x86")) {
      val dir = apisDir.resolve(abi)
      if (Files.exists(dir)) {
        return dir
      }
    }
  }
  throw IllegalStateException("Please install the $latestPlatform x86_64 system image or run the test in Bazel")
}
