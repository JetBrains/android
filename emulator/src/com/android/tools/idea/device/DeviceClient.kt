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
import com.android.adblib.shellV2AsLines
import com.android.adblib.syncSend
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.sdk.IdeSdks
import com.android.tools.idea.util.StudioPathManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.PathManager
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
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

private const val MIN_PORT = 15050
private const val MAX_PORT = 15099
private const val DEVICE_PATH_BASE = "/data/local/tmp"
private const val SCREEN_SHARING_AGENT_APK_NAME = "screen-sharing-agent.jar"
private const val SCREEN_SHARING_AGENT_SO_NAME = "libscreen-sharing-agent.so"

internal class DeviceClient(
  disposableParent: Disposable,
  private val deviceSerialNumber: String,
  private val deviceAbi: String,
  private val project: Project
) : Disposable {

  private val coroutineScope = AndroidCoroutineScope(this)
  private lateinit var controlChannel: SuspendingSocketChannel
  private lateinit var videoChannel: SuspendingSocketChannel
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
    createServerSocketChannel().use { serverSocketChannel ->
      thisLogger().debug("Using port ${(serverSocketChannel.localAddress as InetSocketAddress).port}")
      startAgent(deviceSelector, adb)
      videoChannel = serverSocketChannel.accept()
      connectionTime = System.currentTimeMillis()
      controlChannel = serverSocketChannel.accept()
      controlChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
    }
    removeHostPortForwarding()
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

  private fun createServerSocketChannel(): SuspendingServerSocketChannel {
    for (port in MIN_PORT..MAX_PORT) {
      forwardHostPort(port)
      try {
        return SuspendingServerSocketChannel(AsynchronousServerSocketChannel.open().bind(InetSocketAddress(port)))
      }
      catch (e: BindException) {
        thisLogger().info("Unable to listen on port $port - ${e.message}")
      }
      catch (e: Exception) {
        thisLogger().warn("Unable to listen on port $port", e)
        removeHostPortForwarding()
      }
    }

    throw IOException("No available TCP/IP ports")
  }

  private suspend fun pushAgent(deviceSelector: DeviceSelector, adb: AdbDeviceServices) {
    val permissions = RemoteFileMode.fromPosixPermissions(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)
    val soFile: Path
    val apkFile: Path
    if (project.name == "Screen Sharing Agent") {
      // Development environment.
      val projectDir = project.guessProjectDir()?.toNioPath() ?:
                       Paths.get(StudioPathManager.getSourcesRoot()).resolve("tools/adt/idea/emulator/screen-sharing-agent")
      val facet = project.allModules().firstNotNullResult { AndroidFacet.getInstance(it) }
      val buildVariant = facet?.properties?.SELECTED_BUILD_VARIANT ?: "debug"
      val apkName = if (buildVariant == "debug") "app-debug.apk" else "app-releaze-unsigned.apk"
      soFile = projectDir.resolve(
          "app/build/intermediates/stripped_native_libs/$buildVariant/out/lib/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
      apkFile = projectDir.resolve("app/build/outputs/apk/$buildVariant/$apkName")
    }
    else {
      // Installed Studio.
      val studioHome = Paths.get(PathManager.getHomePath())
      soFile = studioHome.resolve("plugins/android/screen-sharing-agent/$deviceAbi/libscreen-sharing-agent.so")
      apkFile = studioHome.resolve("plugins/android/screen-sharing-agent/screen-sharing-agent.jar")
    }

    // Owner read and write.
    coroutineScope {
      val nativeLibraryPushed = async {
        adb.syncSend(deviceSelector, soFile, "$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_SO_NAME", permissions)
      }
      adb.syncSend(deviceSelector, apkFile, "$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_APK_NAME", permissions)
      nativeLibraryPushed.await()
    }
  }

  private suspend fun startAgent(deviceSelector: DeviceSelector, adb: AdbDeviceServices) {
    startAgentTime = System.currentTimeMillis()
    val command = "CLASSPATH=$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_APK_NAME app_process $DEVICE_PATH_BASE" +
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

  private fun forwardHostPort(port: Int) {
    // TODO: Use AdbLib when it starts supporting port forwarding.
    runAdbCommand("-s", deviceSerialNumber, "reverse", "localabstract:screen-sharing-agent", "tcp:$port")
  }

  private fun removeHostPortForwarding() {
    // TODO: Use AdbLib when it starts supporting port forwarding.
    runAdbCommand("-s", deviceSerialNumber, "reverse", "--remove", "localabstract:screen-sharing-agent")
  }
}

@Throws(ExecutionException::class, InterruptedException::class, ExecutionException::class)
private fun runAdbCommand(vararg parameters: String, async: Boolean = false) {
  val command = GeneralCommandLine(adbPath.toString())
  command.addParameters(*parameters)
  val processBuilder =
      command.toProcessBuilder().redirectOutput(ProcessBuilder.Redirect.INHERIT).redirectError(ProcessBuilder.Redirect.INHERIT)
  val process = processBuilder.start()
  if (async) {
    return
  }
  process.waitFor(15, TimeUnit.SECONDS)
  val exitCode = process.exitValue()
  if (exitCode != 0) {
    throw ProcessTerminatedException(exitCode)
  }
}

private val adbPath: Path by lazy {
  val sdkPath = IdeSdks.getInstance().androidSdkPath?.toPath()
  return@lazy sdkPath?.resolve("platform-tools/adb") ?: Paths.get("adb")
}

private class ProcessTerminatedException(val exitCode: Int) : ExecutionException("Exit code : $exitCode")
