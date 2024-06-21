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
package com.android.tools.idea.streaming.device

import com.android.adblib.ConnectedDevice
import com.android.adblib.DeviceInfo
import com.android.adblib.DeviceState.ONLINE
import com.android.ddmlib.testing.FakeAdbRule
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.FakeAdbServer
import com.android.fakeadbserver.ShellV2Protocol
import com.android.fakeadbserver.devicecommandhandlers.DeviceCommandHandler
import com.android.sdklib.deviceprovisioner.DeviceHandle
import com.android.sdklib.deviceprovisioner.DeviceId
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.sdklib.deviceprovisioner.DeviceType
import com.android.sdklib.deviceprovisioner.Resolution
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.idea.adb.FakeAdbServiceRule
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.ex.ProjectEx
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import icons.StudioIcons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP8
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder
import org.junit.rules.ExternalResource
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.awt.Dimension
import java.net.Socket
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import com.android.sdklib.deviceprovisioner.DeviceState as ProvisionerDeviceState

/**
 * Allows tests to use [FakeScreenSharingAgent] instead of the real one.
 */
class FakeScreenSharingAgentRule : TestRule {
  private var deviceCounter = 0
  private val devices = mutableListOf<FakeDevice>()
  private val projectRule = ProjectRule()
  private val fakeAdbRule: FakeAdbRule = createFakeAdbRule()
  private val fakeAdbServiceRule = FakeAdbServiceRule(projectRule::project, fakeAdbRule)
  private val testEnvironment = object : ExternalResource() {

    override fun before() {
      val binDir = Paths.get(StudioPathManager.getBinariesRoot())
      // Create fake screen-sharing-agent.jar and libscreen-sharing-agent.so files if they don't exist.
      createEmptyFileIfNotExists(binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/$SCREEN_SHARING_AGENT_JAR_NAME"))
      for (deviceAbi in listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")) {
        createEmptyFileIfNotExists(binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/native/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME"))
      }
    }

    override fun after() {
      while (devices.isNotEmpty()) {
        disconnectDevice(devices.last())
      }
    }
  }

  val disposable: Disposable
    get() = projectRule.disposable

  val project: ProjectEx
    get() = projectRule.project

  init {
    // Preload FFmpeg codec native libraries upfront to avoid a race condition when unpacking them.
    avcodec_find_encoder(AV_CODEC_ID_VP8).close()
  }

  override fun apply(base: Statement, description: Description): Statement {
    return projectRule.apply(
        fakeAdbRule.apply(
            fakeAdbServiceRule.apply(
                testEnvironment.apply(base, description),
                description),
            description),
        description)
  }

  private fun createFakeAdbRule(): FakeAdbRule {
    return FakeAdbRule().apply {
      withDeviceCommandHandler(object : DeviceCommandHandler("shell,v2") {
        override fun invoke(server: FakeAdbServer, socketScope: CoroutineScope, socket: Socket, device: DeviceState, args: String) {
          if (args.contains("$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME")) {
            val fakeDevice = devices.find { it.serialNumber == device.deviceId }!!
            val shellProtocol = ShellV2Protocol(socket)
            writeOkay(socket.outputStream)
            runBlocking { fakeDevice.agent.run(shellProtocol, args, fakeDevice.hostPort!!) }
          }
          else if (args.startsWith("mkdir ")) {
            writeOkay(socket.outputStream)
            ShellV2Protocol(socket).writeExitCode(0)
          }
          else if (args.startsWith("logcat ")) {
            writeOkay(socket.outputStream)
            val shellProtocol = ShellV2Protocol(socket)
            shellProtocol.writeStdout("--------- beginning of crash\n")
            shellProtocol.writeStdout("06-20 17:54:11.642 14782 14782 F libc: Fatal signal 11 (SIGSEGV), code 1 (SEGV_MAPERR)\n")
            shellProtocol.writeExitCode(0)
          }
          else {
            throw NextHandlerException()
          }
        }
      })
      withDeviceCommandHandler(object : DeviceCommandHandler("reverse") {
        override fun invoke(server: FakeAdbServer, socketScope: CoroutineScope, socket: Socket, device: DeviceState, args: String) {
          val fakeDevice = devices.find { it.serialNumber == device.deviceId }!!
          if (args.startsWith("forward:")) {
            val parts = args.split(';')
            val hostParts = parts[1].split(':')
            fakeDevice.hostPort = hostParts[1].toInt()
          }
          else if (args.startsWith("killforward:")) {
            fakeDevice.hostPort = null
          }
          val stream = socket.outputStream
          writeOkay(stream)
          writeOkay(stream)
        }
      })
    }
  }

  fun connectDevice(model: String,
                    apiLevel: Int,
                    displaySize: Dimension,
                    foldedSize: Dimension? = null,
                    roundDisplay: Boolean = false,
                    screenDensity: Int? = null,
                    abi: String = "arm64-v8a",
                    additionalDeviceProperties: Map<String, String> = emptyMap(),
                    manufacturer: String = "Google",
                    hostConnectionType: DeviceState.HostConnectionType = DeviceState.HostConnectionType.USB): FakeDevice {
    val serialNumber = (++deviceCounter).toString()
    val release = "Sweet dessert"
    val deviceState = fakeAdbRule.attachDevice(serialNumber, manufacturer, model, release, apiLevel.toString(), abi,
                                               additionalDeviceProperties, hostConnectionType)
    val device = FakeDevice(serialNumber, displaySize, deviceState, roundDisplay = roundDisplay, foldedSize = foldedSize,
                            screenDensity = screenDensity)
    devices.add(device)
    return device
  }

  fun disconnectDevice(device: FakeDevice) {
    fakeAdbRule.disconnectDevice(device.serialNumber)
    Disposer.dispose(device.agent)
    devices.remove(device)
  }

  private fun createEmptyFileIfNotExists(file: Path) {
    if (Files.notExists(file)) {
      Files.createDirectories(file.parent)
      Files.createFile(file)
      // Set very old modified time to make sure that the file gets replaced by the build.
      Files.setLastModifiedTime(file, FileTime.fromMillis(0))
    }
  }

  class FakeDevice(val serialNumber: String,
                   val displaySize: Dimension,
                   val deviceState: DeviceState,
                   val roundDisplay: Boolean = false,
                   foldedSize: Dimension? = null,
                   private val screenDensity: Int? = null,
  ) {
    val agent: FakeScreenSharingAgent =
      FakeScreenSharingAgent(displaySize, deviceState, roundDisplay = roundDisplay, foldedSize = foldedSize)
    var hostPort: Int? = null
    val configuration: DeviceConfiguration = DeviceConfiguration(createDeviceProperties())
    val handle: DeviceHandle = FakeDeviceHandle(this)

    private fun createDeviceProperties(): DeviceProperties {
      return DeviceProperties.build {
        readCommonProperties(deviceState.properties)
        populateDeviceInfoProto("FakeDevicePlugin", serialNumber, deviceState.properties, "fakeConnectionId")
        readAdbSerialNumber(serialNumber)
        icon = when (deviceType) {
          DeviceType.WEAR -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_WEAR
          DeviceType.TV -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_TV
          DeviceType.AUTOMOTIVE -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_CAR
          else -> StudioIcons.DeviceExplorer.PHYSICAL_DEVICE_PHONE
        }
        resolution = Resolution(displaySize.width, displaySize.height)
        density = screenDensity
      }
    }
  }

  private class FakeDeviceHandle(private val device: FakeDevice) : DeviceHandle {
    override val id = DeviceId("Fake", false, device.serialNumber)

    override val scope = AndroidCoroutineScope(device.agent)

    override val stateFlow = MutableStateFlow(createConnectedDeviceState())

    fun createConnectedDeviceState(): ProvisionerDeviceState.Connected {
      val deviceProperties = device.configuration.deviceProperties
      val connectedDevice = mock<ConnectedDevice>().apply {
        whenever(deviceInfoFlow).thenReturn(MutableStateFlow(DeviceInfo(device.serialNumber, ONLINE)))
      }
      return ProvisionerDeviceState.Connected(deviceProperties, connectedDevice)
    }
  }
}

