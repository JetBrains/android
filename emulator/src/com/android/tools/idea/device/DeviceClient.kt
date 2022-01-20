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
package com.android.tools.idea.device

import com.android.adblib.AdbDeviceServices
import com.android.adblib.DeviceSelector
import com.android.adblib.RemoteFileMode
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.SocketSpec
import com.android.adblib.shellV2AsLines
import com.android.adblib.syncSend
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.util.StudioPathManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PluginPathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.idea.util.projectStructure.allModules
import org.jetbrains.kotlin.util.firstNotNullResult
import java.awt.Dimension
import java.io.IOException
import java.net.BindException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission

private const val MIN_PORT = 15050
private const val MAX_PORT = 15099
private const val DEVICE_PATH_BASE = "/data/local/tmp"
private const val SCREEN_SHARING_AGENT_JAR_NAME = "screen-sharing-agent.jar"
private const val SCREEN_SHARING_AGENT_SO_NAME = "libscreen-sharing-agent.so"
private const val SCREEN_SHARING_AGENT_SOURCE_PATH = "tools/adt/idea/emulator/screen-sharing-agent"

internal class DeviceClient(
  disposableParent: Disposable,
  private val deviceSerialNumber: String,
  private val deviceAbi: String,
  private val project: Project
) : Disposable {

  private val coroutineScope = AndroidCoroutineScope(this)
  private lateinit var controlChannel: SuspendingSocketChannel
  private lateinit var videoChannel: SuspendingSocketChannel
  lateinit var deviceController: DeviceController
    private set
  internal var startTime = 0L
  internal var pushTime = 0L
  internal var startAgentTime = 0L
  internal var connectionTime = 0L

  init {
    Disposer.register(disposableParent, this)
  }

  suspend fun startAgentAndConnect() {
    startTime = System.currentTimeMillis()
    val adb = AdbLibService.getSession(project).deviceServices
    val deviceSelector = DeviceSelector.fromSerialNumber(deviceSerialNumber)
    pushAgent(deviceSelector, adb)
    pushTime = System.currentTimeMillis()
    val deviceSocket = SocketSpec.LocalAbstract("screen-sharing-agent")
    createServerSocketChannel(deviceSelector, adb, deviceSocket).use { serverSocketChannel ->
      thisLogger().debug("Using port ${(serverSocketChannel.localAddress as InetSocketAddress).port}")
      startAgent(deviceSelector, adb)
      videoChannel = serverSocketChannel.accept()
      connectionTime = System.currentTimeMillis()
      controlChannel = serverSocketChannel.accept()
      controlChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
    }
    adb.reverseKillForward(deviceSelector, deviceSocket)
    deviceController = DeviceController(this, controlChannel)
  }

  fun createVideoDecoder(maxOutputSize: Dimension) : VideoDecoder =
    VideoDecoder(this, videoChannel, maxOutputSize)

  /**
   * Starts decoding of the video stream. Video decoding continues until the video socket
   * connection is closed, for example, by a [disconnect] call.
   */
  fun startVideoDecoding(decoder: VideoDecoder) {
    coroutineScope.launch { decoder.run() }
  }

  override fun dispose() {
    // Disconnect socket channels asynchronously.
    CoroutineScope(Dispatchers.Default).launch { disconnect() }
  }

  suspend fun disconnect() {
    coroutineScope {
      val videoChannelClosed = async {
        try {
          if (::videoChannel.isInitialized) {
            videoChannel.close()
          }
        }
        catch (e: IOException) {
          thisLogger().warn(e)
        }
      }
      try {
        if (::controlChannel.isInitialized) {
          controlChannel.close()
        }
      }
      catch (e: IOException) {
        thisLogger().warn(e)
      }
      videoChannelClosed.await()
    }
  }

  private suspend fun createServerSocketChannel(
      deviceSelector: DeviceSelector, adb: AdbDeviceServices, deviceSocket: SocketSpec): SuspendingServerSocketChannel {
    for (port in MIN_PORT..MAX_PORT) {
      adb.reverseForward(deviceSelector, deviceSocket, SocketSpec.Tcp(port))
      try {
        @Suppress("BlockingMethodInNonBlockingContext")
        return SuspendingServerSocketChannel(AsynchronousServerSocketChannel.open().bind(InetSocketAddress(port)))
      }
      catch (e: BindException) {
        thisLogger().info("Unable to listen on port $port - ${e.message}")
      }
      catch (e: Exception) {
        thisLogger().warn("Unable to listen on port $port", e)
        adb.reverseKillForward(deviceSelector, deviceSocket)
      }
    }

    throw IOException("No available TCP/IP ports")
  }

  private suspend fun pushAgent(deviceSelector: DeviceSelector, adb: AdbDeviceServices) {
    val soFile: Path
    val jarFile: Path
    if (StudioPathManager.isRunningFromSources()) {
      // Development environment.
      val projectDir = project.guessProjectDir()?.toNioPath()
      if (projectDir != null && projectDir.endsWith(SCREEN_SHARING_AGENT_SOURCE_PATH)) {
        // Development environment for the screen sharing agent.
        // Use the agent built by running "Build > Make Project" in Studio.
        val facet = project.allModules().firstNotNullResult { AndroidFacet.getInstance(it) }
        val buildVariant = facet?.properties?.SELECTED_BUILD_VARIANT ?: "debug"
        soFile = projectDir.resolve(
            "app/build/intermediates/stripped_native_libs/$buildVariant/out/lib/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
        val apkName = if (buildVariant == "debug") "app-debug.apk" else "app-release-unsigned.apk"
        jarFile = projectDir.resolve("app/build/outputs/apk/$buildVariant/$apkName")
      }
      else {
        // Development environment for Studio.
        // Use the agent built by running "bazel build //tools/adt/idea/emulator/screen-sharing-agent:bundle"
        val binDir = Paths.get(StudioPathManager.getBinariesRoot())
        soFile = binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/native/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
        jarFile = binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/$SCREEN_SHARING_AGENT_JAR_NAME")
      }
    }
    else {
      // Installed Studio.
      val pluginDir = PluginPathManager.getPluginHome("android").toPath()
      soFile = pluginDir.resolve("resources/screen-sharing-agent/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
      jarFile = pluginDir.resolve("resources/screen-sharing-agent/$SCREEN_SHARING_AGENT_JAR_NAME")
    }

    coroutineScope {
      val permissions = RemoteFileMode.fromPosixPermissions(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
      val nativeLibraryPushed = async {
        adb.syncSend(deviceSelector, soFile, "$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_SO_NAME", permissions)
      }
      adb.syncSend(deviceSelector, jarFile, "$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME", permissions)
      nativeLibraryPushed.await()
    }
  }

  private suspend fun startAgent(deviceSelector: DeviceSelector, adb: AdbDeviceServices) {
    startAgentTime = System.currentTimeMillis()
    val command = "CLASSPATH=$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME app_process $DEVICE_PATH_BASE" +
                  " com.android.tools.screensharing.Main --log=debug"
    // Use a coroutine scope that not linked to the lifecycle of the client to make sure that
    // the agent has a chance to terminate gracefully when the client is disposed rather than
    // be killed by adb.
    CoroutineScope(Dispatchers.Unconfined).launch {
      val log = Logger.getInstance("ScreenSharingAgent")
      adb.shellV2AsLines(deviceSelector, command).collect {
        when (it) {
          is ShellCommandOutputElement.StdoutLine -> if (it.contents.isNotBlank()) log.info(it.contents)
          is ShellCommandOutputElement.StderrLine -> if (it.contents.isNotBlank()) log.warn(it.contents)
          is ShellCommandOutputElement.ExitCode ->
              if (it.exitCode == 0) log.info("terminated") else log.warn("terminated with code ${it.exitCode}")
        }
      }
    }
  }
}
