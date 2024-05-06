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
package com.android.tools.idea.streaming.device

import com.android.adblib.AdbSession
import com.android.adblib.DeviceSelector
import com.android.adblib.RemoteFileMode
import com.android.adblib.ShellCommandOutputElement
import com.android.adblib.SocketSpec
import com.android.adblib.shellAsLines
import com.android.adblib.syncSend
import com.android.annotations.concurrency.GuardedBy
import com.android.tools.analytics.UsageTracker
import com.android.tools.idea.IdeInfo
import com.android.tools.idea.adblib.AdbLibService
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.diagnostics.crash.StudioCrashReporter
import com.android.tools.idea.diagnostics.report.GenericReport
import com.android.tools.idea.downloads.AndroidProfilerDownloader
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.DeviceMirroringSettings
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.util.StudioPathManager
import com.google.wireless.android.sdk.stats.AndroidStudioEvent
import com.google.wireless.android.sdk.stats.DeviceMirroringAbnormalAgentTermination
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.project.modules
import com.intellij.openapi.util.Disposer
import com.intellij.util.IncorrectOperationException
import com.intellij.util.containers.ContainerUtil.createLockFreeCopyOnWriteList
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.jetbrains.android.facet.AndroidFacet
import java.awt.Dimension
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousServerSocketChannel
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.attribute.PosixFilePermission
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.IntFunction
import kotlin.math.min

// Predefined agent's exit codes. Other exit codes are possible.
internal const val AGENT_GENERIC_FAILURE = 1
internal const val AGENT_INVALID_COMMAND_LINE = 2
internal const val AGENT_SOCKET_CONNECTIVITY_ERROR = 10
internal const val AGENT_SOCKET_IO_ERROR = 11
internal const val AGENT_INVALID_CONTROL_MESSAGE = 12
internal const val AGENT_NULL_POINTER = 20
internal const val AGENT_CLASS_NOT_FOUND = 21
internal const val AGENT_METHOD_NOT_FOUND = 22
internal const val AGENT_CONSTRUCTOR_NOT_FOUND = 23
internal const val AGENT_FIELD_NOT_FOUND = 24
internal const val AGENT_JAVA_EXCEPTION = 25
internal const val AGENT_VIDEO_ENCODER_NOT_FOUND = 30
internal const val AGENT_VIDEO_ENCODER_INITIALIZATION_ERROR = 31
internal const val AGENT_VIDEO_ENCODER_CONFIGURATION_ERROR = 32
internal const val AGENT_WEAK_VIDEO_ENCODER = 33
internal const val AGENT_REPEATED_VIDEO_ENCODER_ERRORS = 34
internal const val AGENT_VIRTUAL_DISPLAY_CREATION_ERROR = 50
internal const val AGENT_INPUT_SURFACE_CREATION_ERROR = 51
internal const val AGENT_SERVICE_NOT_FOUND = 52
internal const val AGENT_KEY_CHARACTER_MAP_ERROR = 53
internal const val AGENT_SIGABORT = 134
internal const val AGENT_SIGKILL = 137
internal const val AGENT_SIGSEGV = 139

internal const val SCREEN_SHARING_AGENT_JAR_NAME = "screen-sharing-agent.jar"
internal const val SCREEN_SHARING_AGENT_SO_NAME = "libscreen-sharing-agent.so"
internal const val SCREEN_SHARING_AGENT_SOURCE_PATH = "tools/adt/idea/streaming/screen-sharing-agent"
internal const val DEVICE_PATH_BASE = "/data/local/tmp/.studio"
private const val MAX_BIT_RATE_EMULATOR = 2000000
private const val VIDEO_CHANNEL_MARKER = 'V'.code.toByte()
private const val CONTROL_CHANNEL_MARKER = 'C'.code.toByte()
// Flag definitions. Keep in sync with flags.h
internal const val START_VIDEO_STREAM = 0x01
internal const val TURN_OFF_DISPLAY_WHILE_MIRRORING = 0x02
internal const val B_303684492_WORKAROUND = 0x08
/** Maximum cumulative length of agent messages to remember. */
private const val MAX_TOTAL_AGENT_MESSAGE_LENGTH = 10_000
private const val MAX_ERROR_MESSAGE_AGE_MILLIS = 1000L
private const val CRASH_REPORT_TYPE = "Screen Sharing Agent termination"
private const val REPORT_FIELD_EXIT_CODE = "exitCode"
private const val REPORT_FIELD_RUN_DURATION_MILLIS = "runDurationMillis"
private const val REPORT_FIELD_AGENT_MESSAGES = "agentMessages"
private const val REPORT_FIELD_DEVICE = "device"

internal class DeviceClient(
  val deviceSerialNumber: String,
  val deviceConfig: DeviceConfiguration,
  private val deviceAbi: String
) : Disposable {

  val deviceName: String = deviceConfig.deviceName
  @Volatile var videoDecoder: VideoDecoder? = null
    private set
  @Volatile var deviceController: DeviceController? = null
    private set
  val streamingSessionTracker: DeviceStreamingSessionTracker = DeviceStreamingSessionTracker(deviceConfig)
  private val clientScope = AndroidCoroutineScope(this)
  private val connectionState = AtomicReference<CompletableDeferred<Unit>>()
  private val agentTerminationListeners = createLockFreeCopyOnWriteList<AgentTerminationListener>()
  /**
   * Contains entries for all active video streams. Keyed by display IDs. The values represent
   * maximum video resolutions requested by different video stream consumers
   */
  @GuardedBy("itself") private val videoStreams = Int2ObjectOpenHashMap<VideoStreamArbiter>()

  /**
   * Asynchronously establishes connection to the screen sharing agent without activating the video stream.
   */
  fun establishAgentConnectionWithoutVideoStreamAsync(project: Project) {
    clientScope.launch { establishAgentConnection(Dimension(), UNKNOWN_ORIENTATION, false, project) }
  }

  /**
   * Establishes connection to the screen sharing agent. If the process of establishing connection
   * has already been started, waits for it to complete.
   */
  suspend fun establishAgentConnection(
      maxVideoSize: Dimension, initialDisplayOrientation: Int, startVideoStream: Boolean, project: Project) {
    AndroidProfilerDownloader.getInstance().makeSureComponentIsInPlace()
    streamingSessionTracker.streamingStarted()
    val completion = CompletableDeferred<Unit>()
    val connection = connectionState.compareAndExchange(null, completion) ?: completion
    if (connection === completion) {
      try {
        startAgentAndConnect(maxVideoSize, initialDisplayOrientation, startVideoStream, project)
        connection.complete(Unit)
      }
      catch (e: Throwable) {
        connectionState.set(null)
        AdbLibService.getSession(project).throwIfCancellationOrDeviceDisconnected(e)
        connection.completeExceptionally(e)
      }
    }
    connection.await()

    if (connection !== completion && startVideoStream) {
      startVideoStream(project, PRIMARY_DISPLAY_ID, maxVideoSize)
    }
  }

  /**
   * Waits for the connection to the screen sharing agent to be established. Returns immediately
   * if the connection hasn't been attempted yet or the connection attempt failed.
   */
  suspend fun waitUntilConnected() {
    connectionState.get()?.await()
  }

  /**
   * Starts the screen sharing agent and connects to it.
   */
  private suspend fun startAgentAndConnect(maxVideoSize: Dimension,
                                           initialDisplayOrientation: Int,
                                           startVideoStream: Boolean,
                                           project: Project) {
    AndroidProfilerDownloader.getInstance().makeSureComponentIsInPlace()
    val adbSession = AdbLibService.getSession(project)
    val deviceSelector = DeviceSelector.fromSerialNumber(deviceSerialNumber)
    val agentPushed = coroutineScope {
      async {
        pushAgent(deviceSelector, adbSession, project)
      }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    val asyncChannel = AsynchronousServerSocketChannel.open().bind(InetSocketAddress(0))
    val port = (asyncChannel.localAddress as InetSocketAddress).port
    logger.debug("Using port $port")
    var channels: Channels? = null
    SuspendingServerSocketChannel(asyncChannel).use { serverSocketChannel ->
      val socketName = "screen-sharing-agent-$port"
      ClosableReverseForwarding(deviceSelector, adbSession, SocketSpec.LocalAbstract(socketName), SocketSpec.Tcp(port)).use {
        it.startForwarding()
        agentPushed.await()
        startAgent(deviceSelector, adbSession, socketName, maxVideoSize, initialDisplayOrientation, startVideoStream)
        channels = connectChannels(serverSocketChannel)
        // Port forwarding can be removed since the already established connections will continue to work without it.
      }
      channels?.let {
        try {
          deviceController = DeviceController(this, it.controlChannel)
        }
        catch (e: IncorrectOperationException) {
          return // Already disposed.
        }
        videoDecoder = VideoDecoder(it.videoChannel, clientScope, deviceConfig.deviceProperties, streamingSessionTracker)
            .apply { start(startVideoStream) }
      }
    }

    if (startVideoStream) {
      videoStreams[PRIMARY_DISPLAY_ID] = VideoStreamArbiter(project, PRIMARY_DISPLAY_ID, maxVideoSize)
    }
  }

  fun addAgentTerminationListener(listener: AgentTerminationListener) {
    agentTerminationListeners.add(listener)
  }

  fun removeAgentTerminationListener(listener: AgentTerminationListener) {
    agentTerminationListeners.remove(listener)
  }

  fun startVideoStream(requester: Any, displayId: Int, maxOutputSize: Dimension) {
    synchronized(videoStreams) {
      val arbiter = videoStreams.computeIfAbsent(displayId, IntFunction { d -> VideoStreamArbiter(d) })
      arbiter.startVideoStream(requester, maxOutputSize)
    }
  }

  fun stopVideoStream(requester: Any, displayId: Int) {
    synchronized(videoStreams) {
      videoStreams[displayId]?.let {
        it.stopVideoStream(requester)
        if (it.isEmpty()) {
          videoStreams.remove(displayId)
        }
      }
    }
  }

  fun setMaxVideoResolution(requester: Any, displayId: Int, maxOutputSize: Dimension) {
    synchronized(videoStreams) {
      videoStreams[displayId]?.setMaxVideoResolution(requester, maxOutputSize)
    }
  }

  private suspend fun connectChannels(serverSocketChannel: SuspendingServerSocketChannel): Channels {
    return withVerboseTimeout(getConnectionTimeout(), "Device agent is not responding") {
      val videoChannel: SuspendingSocketChannel
      val controlChannel: SuspendingSocketChannel
      val channel1 = serverSocketChannel.acceptAndEnsureClosing(this@DeviceClient)
      val channel2 = serverSocketChannel.acceptAndEnsureClosing(this@DeviceClient)
      // The channels are distinguished by single-byte markers, 'V' for video and 'C' for control.
      // Read the markers to assign the channels appropriately.
      val marker1 = async { readChannelMarker(channel1) }
      val marker2 = async { readChannelMarker(channel2) }
      val m1 = marker1.await()
      val m2 = marker2.await()
      if (m1 == VIDEO_CHANNEL_MARKER && m2 == CONTROL_CHANNEL_MARKER) {
        videoChannel = channel1
        controlChannel = channel2
      }
      else if (m1 == CONTROL_CHANNEL_MARKER && m2 == VIDEO_CHANNEL_MARKER) {
        videoChannel = channel2
        controlChannel = channel1
      }
      else {
        throw RuntimeException("Unexpected channel markers: $m1, $m2")
      }
      controlChannel.setOption(StandardSocketOptions.TCP_NODELAY, true)
      return@withVerboseTimeout Channels(videoChannel, controlChannel)
    }
  }

  private fun getConnectionTimeout(): Long {
    val timeout = StudioFlags.DEVICE_MIRRORING_CONNECTION_TIMEOUT_MILLIS.get().toLong()
    return if (timeout > 0) timeout else Long.MAX_VALUE
  }

  /** Similar to [withTimeout] but throws [TimeoutException] with the given message. */
  private suspend fun <T> withVerboseTimeout(timeMillis: Long, timeoutMessage: String, block: suspend CoroutineScope.() -> T): T {
    return try {
      withTimeout(timeMillis, block)
    }
    catch (e: TimeoutCancellationException) {
      throw TimeoutException(timeoutMessage)
    }
  }

  private suspend fun readChannelMarker(channel: SuspendingSocketChannel): Byte {
    val buf = ByteBuffer.allocate(1)
    channel.read(buf, 5, TimeUnit.SECONDS)
    buf.flip()
    return buf.get()
  }

  override fun dispose() {
    streamingSessionTracker.streamingEnded()
  }

  private suspend fun pushAgent(deviceSelector: DeviceSelector, adbSession: AdbSession, project: Project) {
    streamingSessionTracker.agentPushStarted()

    val soFile: Path
    val jarFile: Path
    if (StudioPathManager.isRunningFromSources() && IdeInfo.getInstance().isAndroidStudio) {
      // Development environment.
      val projectDir = project.guessProjectDir()?.toNioPath()
      if (projectDir != null && projectDir.endsWith(SCREEN_SHARING_AGENT_SOURCE_PATH)) {
        // Development environment for the screen sharing agent.
        // Use the agent built by running "Build > Make Project" in Studio.
        val facet = project.modules.firstNotNullOfOrNull { AndroidFacet.getInstance(it) }
        val buildVariant = facet?.properties?.SELECTED_BUILD_VARIANT ?: "debug"
        soFile = projectDir.resolve(
          "app/build/intermediates/stripped_native_libs/$buildVariant/out/lib/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
        val apkName = if (buildVariant == "debug") "app-debug.apk" else "app-release-unsigned.apk"
        jarFile = projectDir.resolve("app/build/outputs/apk/$buildVariant/$apkName")
      }
      else {
        // Development environment for Studio.
        // Use the agent built by running "bazel build //tools/adt/idea/streaming/screen-sharing-agent:bundle"
        val binDir = Paths.get(StudioPathManager.getBinariesRoot())
        soFile = binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/native/$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
        jarFile = binDir.resolve("$SCREEN_SHARING_AGENT_SOURCE_PATH/$SCREEN_SHARING_AGENT_JAR_NAME")
      }
    }
    else {
      // Installed Studio.
      val screenSharingAgentDir = AndroidProfilerDownloader.getInstance()
        .getHostDir("plugins/android/resources/screen-sharing-agent")
        .toPath()
      soFile = screenSharingAgentDir.resolve("$deviceAbi/$SCREEN_SHARING_AGENT_SO_NAME")
      jarFile = screenSharingAgentDir.resolve(SCREEN_SHARING_AGENT_JAR_NAME)
    }

    coroutineScope {
      val adb = adbSession.deviceServices
      // "chown shell:shell" ensures proper ownership of /data/local/tmp/.studio if adb is rooted.
      val command = "mkdir -p $DEVICE_PATH_BASE; chmod 755 $DEVICE_PATH_BASE; chown shell:shell $DEVICE_PATH_BASE"
      adb.shellAsLines(deviceSelector, command).collect {
        if (it is ShellCommandOutputElement.ExitCode && it.exitCode != 0) {
          logger.warn("Unable to create $DEVICE_PATH_BASE directory: ${it.exitCode}")
        }
      }
      val permissions = RemoteFileMode.fromPosixPermissions(PosixFilePermission.OWNER_READ)
      val nativeLibraryPushed = async {
        adbSession.pushFile(deviceSelector, soFile, "$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_SO_NAME", permissions)
      }
      adbSession.pushFile(deviceSelector, jarFile, "$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME", permissions)
      nativeLibraryPushed.await()
    }
    streamingSessionTracker.agentPushEnded()
  }

  private val isEmulator = deviceSerialNumber.startsWith("emulator-") || deviceConfig.deviceProperties.isVirtual == true

  private suspend fun startAgent(
      deviceSelector: DeviceSelector,
      adbSession: AdbSession,
      socketName: String,
      maxVideoSize: Dimension,
      initialDisplayOrientation: Int,
      startVideoStream: Boolean) {
    val maxSizeArg =
        if (maxVideoSize.width > 0 && maxVideoSize.height > 0) " --max_size=${maxVideoSize.width},${maxVideoSize.height}" else ""
    val orientationArg = if (initialDisplayOrientation == UNKNOWN_ORIENTATION) "" else " --orientation=$initialDisplayOrientation"
    val flags = (if (startVideoStream) START_VIDEO_STREAM else 0) or
                (if (DeviceMirroringSettings.getInstance().turnOffDisplayWhileMirroring) TURN_OFF_DISPLAY_WHILE_MIRRORING else 0) or
                (if (StudioFlags.B_303684492_WORKAROUND.get()) B_303684492_WORKAROUND else 0)
    val flagsArg = if (flags != 0) " --flags=$flags" else ""
    val maxBitRate = calculateMaxBitRate()
    val maxBitRateArg = if (maxBitRate > 0) " --max_bit_rate=$maxBitRate" else ""
    val logLevel = StudioFlags.DEVICE_MIRRORING_AGENT_LOG_LEVEL.get()
    val logLevelArg = if (logLevel.isNotBlank()) " --log=$logLevel" else ""
    val codecName = StudioFlags.DEVICE_MIRRORING_VIDEO_CODEC.get()
    val codecArg = if (codecName.isNotBlank()) " --codec=$codecName" else ""
    val command = "CLASSPATH=$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME app_process $DEVICE_PATH_BASE" +
                  " com.android.tools.screensharing.Main --socket=$socketName" +
                  "$maxSizeArg$orientationArg$flagsArg$maxBitRateArg$logLevelArg$codecArg"
    clientScope.launch {
      val log = Logger.getInstance("ScreenSharingAgent $deviceName")
      val agentStartTime = System.currentTimeMillis()
      val errors = OutputAccumulator(MAX_TOTAL_AGENT_MESSAGE_LENGTH, MAX_ERROR_MESSAGE_AGE_MILLIS)
      try {
        logger.info("Executing adb shell $command")
        adbSession.deviceServices.shellAsLines(deviceSelector, command).collect {
          when (it) {
            is ShellCommandOutputElement.StdoutLine -> if (it.contents.isNotBlank()) log.info(it.contents)
            is ShellCommandOutputElement.StderrLine -> {
              if (it.contents.isNotBlank()) {
                log.warn(it.contents)
                errors.addMessage(it.contents.trimEnd())
              }
            }
            is ShellCommandOutputElement.ExitCode -> {
              onDisconnection()
              if (it.exitCode == 0) {
                log.info("terminated")
              } else {
                log.warn("terminated with code ${it.exitCode}")
                recordAbnormalAgentTermination(it.exitCode, System.currentTimeMillis() - agentStartTime, errors)
              }
              for (listener in agentTerminationListeners) {
                listener.agentTerminated(it.exitCode)
              }
              cancel()
            }
          }
        }
      }
      catch (e: EOFException) {
        // Device disconnected. This is not an error.
        log.info("device disconnected")
        onDisconnection()
        for (listener in agentTerminationListeners) {
          listener.deviceDisconnected()
        }
      }
      catch (e: Throwable) {
        adbSession.throwIfCancellationOrDeviceDisconnected(e)
        throw RuntimeException("Command \"$command\" failed", e)
      }
    }
  }

  private fun calculateMaxBitRate(): Int {
    if (isEmulator) {
      return MAX_BIT_RATE_EMULATOR
    }
    val bitRate1 = BitRateManager.getInstance().getBitRate(deviceConfig.deviceProperties)
    val bitRate2 = StudioFlags.DEVICE_MIRRORING_MAX_BIT_RATE.get()
    return when {
      bitRate1 == 0 -> bitRate2
      bitRate2 == 0 -> bitRate1
      else -> min(bitRate1, bitRate2)
    }
  }

  private fun recordAbnormalAgentTermination(exitCode: Int, runDurationMillis: Long, errors: OutputAccumulator) {
    // Log a metrics event.
    val studioEvent = AndroidStudioEvent.newBuilder()
      .setKind(AndroidStudioEvent.EventKind.DEVICE_MIRRORING_ABNORMAL_AGENT_TERMINATION)
      .setDeviceMirroringAbnormalAgentTermination(
        DeviceMirroringAbnormalAgentTermination.newBuilder()
          .setExitCode(exitCode)
          .setRunDurationMillis(runDurationMillis)
      )
      .setDeviceInfo(deviceConfig.deviceProperties.deviceInfoProto)

    UsageTracker.log(studioEvent)

    // Create and submit a Crash report.
    val fields = mapOf(
      REPORT_FIELD_EXIT_CODE to exitCode.toString(),
      REPORT_FIELD_RUN_DURATION_MILLIS to runDurationMillis.toString(),
      REPORT_FIELD_AGENT_MESSAGES to errors.getMessages(),
      REPORT_FIELD_DEVICE to deviceConfig.deviceName,
    )
    val report = GenericReport(CRASH_REPORT_TYPE, fields)
    try {
      StudioCrashReporter.getInstance().submit(report.asCrashReport())
    }
    catch (ignore: RuntimeException) {
      // May happen due to exceeded quota.
    }
  }

  private suspend fun onDisconnection() {
    deviceController?.let { Disposer.dispose(it) }
    deviceController = null
    videoDecoder?.closeChannel()
    videoDecoder = null
    connectionState.set(null)
  }

  private suspend fun AdbSession.pushFile(device: DeviceSelector, file: Path, remoteFilePath: String, permissions: RemoteFileMode) {
    try {
      deviceServices.syncSend(device, file, remoteFilePath, permissions)
    }
    catch (e: Throwable) {
      throwIfCancellationOrDeviceDisconnected(e)
      throw RuntimeException("Failed to push ${file.fileName} to $device", e)
    }
  }

  /** Throws [CancellationException] if [throwable] is [CancellationException] or the device is disconnected. */
  private suspend fun AdbSession.throwIfCancellationOrDeviceDisconnected(throwable: Throwable) {
    when {
      throwable is CancellationException -> throw throwable
      isDeviceConnected() == false -> throw CancellationException()
    }
  }


  /** Checks if the device is connected. Returns null if it cannot be determined. */
  private suspend fun AdbSession.isDeviceConnected(): Boolean? {
    return try {
      return hostServices.devices().entries.find { it.serialNumber == deviceSerialNumber } != null
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (_: Throwable) {
      null
    }
  }

  private suspend fun SuspendingServerSocketChannel.acceptAndEnsureClosing(parentDisposable: Disposable): SuspendingSocketChannel =
      accept().also { Disposer.register(parentDisposable, DisposableCloser(it)) }

  private data class Channels(var videoChannel: SuspendingSocketChannel, var controlChannel: SuspendingSocketChannel)

  interface AgentTerminationListener {
    fun agentTerminated(exitCode: Int)
    fun deviceDisconnected()
  }

  private class DisposableCloser(private val channel: SuspendingSocketChannel) : Disposable {

    override fun dispose() {
      // Disconnect the socket channel asynchronously.
      CoroutineScope(Dispatchers.IO).launch {
        try {
          channel.close()
        }
        catch (e: IOException) {
          thisLogger().warn(e)
        }
      }
    }
  }

  private class ClosableReverseForwarding(
    val deviceSelector: DeviceSelector,
    val adbSession: AdbSession,
    val deviceSocket: SocketSpec,
    val localSocket: SocketSpec,
  ) : SuspendingCloseable {

    var opened = false

    suspend fun startForwarding() {
      adbSession.deviceServices.reverseForward(deviceSelector, deviceSocket, localSocket, rebind = true)
      opened = true
    }

    override suspend fun close() {
      if (opened) {
        opened = false
        adbSession.deviceServices.reverseKillForward(deviceSelector, deviceSocket)
      }
    }
  }

  private class OutputAccumulator(private val maxSize: Int, private val maxAgeMillis: Long) {

    private val messages = ArrayDeque<Message>()
    private var totalSize = 0

    fun addMessage(text: String) {
      val time = System.currentTimeMillis()
      prune(maxSize - text.length, time)
      messages.add(Message(time, text))
      totalSize += text.length
    }

    fun getMessages(): String {
      prune(maxSize, System.currentTimeMillis())
      return messages.joinToString("\n", transform = Message::text)
    }

    private fun prune(size: Int, time: Long) {
      val cutoff = time - maxAgeMillis
      while (totalSize > size || messages.isNotEmpty() && messages.first().timestamp < cutoff) {
        totalSize -= messages.removeFirst().text.length
      }
    }

    private data class Message(val timestamp: Long, val text: String)
  }

  /**
   * Arbitrates between video resolution and video stream start/stop between multiple video stream consumers.
   * No concurrent access is allowed.
   */
  private inner class VideoStreamArbiter(private val displayId: Int) {
    /** Keyed by the requesters of video resolutions. */
    private val requestedVideoResolutions = mutableMapOf<Any, Dimension>()
    private val currentSize = Dimension()

    constructor(requester: Any, displayId: Int, maxOutputSize: Dimension) : this(displayId) {
      requestedVideoResolutions[requester] = maxOutputSize
    }

    fun startVideoStream(requester: Any, maxOutputSize: Dimension) {
      if (requestedVideoResolutions.isEmpty()) {
        requestedVideoResolutions[requester] = maxOutputSize
        currentSize.size = maxOutputSize
        if (videoDecoder?.enableDecodingForDisplay(displayId) == true) {
          deviceController?.sendControlMessage(StartVideoStreamMessage(displayId, maxOutputSize))
        }
      }
      else {
        requestedVideoResolutions[requester] = maxOutputSize
        sendUpdatedVideoSize()
      }
    }

    fun stopVideoStream(requester: Any) {
      requestedVideoResolutions.remove(requester)
      if (requestedVideoResolutions.isEmpty()) {
        currentSize.setSize(0, 0)
        if (videoDecoder?.disableDecodingForDisplay(displayId) == true) {
          deviceController?.sendControlMessage(StopVideoStreamMessage(displayId))
          if (displayId == PRIMARY_DISPLAY_ID) {
            streamingSessionTracker.streamingEnded()
          }
        }
      }
      else {
        sendUpdatedVideoSize()
      }
    }

    fun setMaxVideoResolution(requester: Any, maxOutputSize: Dimension) {
      if (requestedVideoResolutions.put(requester, maxOutputSize) == null) {
        requestedVideoResolutions.remove(requester)
      }
      else {
        sendUpdatedVideoSize()
      }
    }

    /**
     * Updates [currentSize], which is a max of all requested sizes in both dimensions.
     * Returns true if [currentSize] changed as a result, otherwise false.
     */
    private fun sendUpdatedVideoSize() {
      var width = 0
      var height = 0
      for (size in requestedVideoResolutions.values) {
        if (width < size.width) {
          width = size.width
        }
        if (height < size.height) {
          height = size.height
        }
      }
      if (width == currentSize.width && height == currentSize.height) {
        return
      }
      currentSize.setSize(width, height)
      deviceController?.sendControlMessage(SetMaxVideoResolutionMessage(displayId, currentSize))
    }

    fun isEmpty(): Boolean =
        requestedVideoResolutions.isEmpty()
  }
}

private val logger = Logger.getInstance(DeviceClient::class.java)

internal class AgentTerminatedException(val exitCode: Int) : RuntimeException("Exit code $exitCode")