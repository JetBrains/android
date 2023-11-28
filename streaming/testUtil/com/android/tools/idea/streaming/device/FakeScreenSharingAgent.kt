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

import com.android.annotations.concurrency.UiThread
import com.android.fakeadbserver.DeviceState
import com.android.fakeadbserver.ShellV2Protocol
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.concurrency.AndroidExecutors
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.streaming.core.DisplayDescriptor
import com.android.tools.idea.streaming.core.DisplayType
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.core.interpolate
import com.android.tools.idea.streaming.core.putUInt
import com.android.tools.idea.streaming.core.rotatedByQuadrants
import com.android.utils.Base128InputStream
import com.android.utils.Base128OutputStream
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.Strings.nullize
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import it.unimi.dsi.fastutil.ints.Int2ObjectFunction
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_AV1
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_HEVC
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP8
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP9
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VVC
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_encoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_packet
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_frame
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EAGAIN
import org.bytedeco.ffmpeg.global.avutil.AVERROR_EOF
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGR24
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable
import org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size
import org.bytedeco.ffmpeg.global.avutil.av_make_q
import org.bytedeco.ffmpeg.global.swscale.SWS_BICUBIC
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.Pointer
import java.awt.Color
import java.awt.Dimension
import java.awt.RenderingHints
import java.awt.geom.Path2D
import java.awt.image.BufferedImage
import java.awt.image.DataBufferByte
import java.io.EOFException
import java.io.IOException
import java.net.InetSocketAddress
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.channels.ClosedChannelException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fake Screen Sharing Agent for use in tests.
 */
class FakeScreenSharingAgent(
  val displaySize: Dimension,
  private val deviceState: DeviceState,
  private val roundDisplay: Boolean = false,
  private val foldedSize: Dimension? = null,
) : Disposable {

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
     "FakeScreenSharingAgent", AndroidExecutors.getInstance().workerThreadExecutor, 1)
  private val singleThreadedDispatcher = executor.asCoroutineDispatcher()
  private val agentsScope = CoroutineScope(singleThreadedDispatcher + Job())
  private var startTime = 0L

  private var videoChannel: SuspendingSocketChannel? = null
  private var controller: Controller? = null
  private val displayStreamers = Int2ObjectOpenHashMap<DisplayStreamer>()

  private val codecName = nullize(StudioFlags.DEVICE_MIRRORING_VIDEO_CODEC.get()) ?: "vp8"
  private val videoEncoder: AVCodec by lazy {
    // Use avcodec_find_encoder instead of avcodec_find_encoder_by_name because the names of encoders and decoders don't match.
    val codecId = when (codecName) {
      "vp8" -> AV_CODEC_ID_VP8
      "vp9" -> AV_CODEC_ID_VP9
      "av01" -> AV_CODEC_ID_AV1
      "avc" -> AV_CODEC_ID_H264
      "hevc" -> AV_CODEC_ID_HEVC
      "vvc" -> AV_CODEC_ID_VVC
      else -> throw RuntimeException("$codecName encoder not found")
    }

    avcodec_find_encoder(codecId) ?: throw RuntimeException("$codecName encoder not found")
  }

  private val clipboardInternal = AtomicReference("")
  private val clipboardSynchronizationActive = AtomicBoolean()
  var clipboard: String
    get() = clipboardInternal.get()
    set(value) {
      val oldValue = clipboardInternal.getAndSet(value)
      if (value != oldValue) {
        agentsScope.launch {
          if (clipboardSynchronizationActive.get()) {
            sendNotificationOrResponse(ClipboardChangedNotification(value))
          }
        }
      }
    }
  @Volatile
  private var foldingState: FoldingState? = foldedSize?.let { FoldingState.OPEN }

  @Volatile
  var maxVideoEncoderResolution = 2048 // Many phones, for example Galaxy Z Fold3, have VP8 encoder limited to 2048x2048 resolution.
  @Volatile
  var commandLine: String? = null
    private set
  val commandLog = LinkedBlockingDeque<ControlMessage>()
  @Volatile
  var isRunning: Boolean = false
    private set
  val videoStreamActive: Boolean
      get() = displayStreamers.isNotEmpty()
  @Volatile
  var crashOnStart: Boolean = false
  @Volatile
  var startDelayMillis: Long = 0
  @Volatile
  var bitRate: Int = DEFAULT_BIT_RATE
    set(value) {
      field = value
      agentsScope.launch {
        for (displayStreamer in displayStreamers.values) {
          displayStreamer.bitRate = value
        }
      }
    }
  @Volatile
  var darkMode = false

  private var maxVideoResolution = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
  private var startVideoStream = false
  private var deviceOrientation = 0
  private var displays = listOf(DisplayDescriptor(PRIMARY_DISPLAY_ID, displaySize, 0, DisplayType.INTERNAL))

  private var shellProtocol: ShellV2Protocol? = null

  /** Runs the agent. Returns when the agent terminates. */
  suspend fun run(protocol: ShellV2Protocol, command: String, hostPort: Int) {
    withContext(singleThreadedDispatcher) {
      runInternal(protocol, command, hostPort)
    }
  }

  private suspend fun runInternal(protocol: ShellV2Protocol, command: String, hostPort: Int) {
    commandLine = command
    shellProtocol = protocol
    commandLog.clear()
    startTime = System.currentTimeMillis()

    parseArgs(command)
    val videoChannel = SuspendingSocketChannel.open()
    this.videoChannel = videoChannel
    val controlChannel = SuspendingSocketChannel.open()
    ChannelClosingSynchronizer(listOf(videoChannel, controlChannel)).start()
    val socketAddress = InetSocketAddress("localhost", hostPort)
    videoChannel.connect(socketAddress)
    controlChannel.connect(socketAddress)
    if (crashOnStart) {
      terminateAgent(139)
      return
    }
    if (startDelayMillis > 0) {
      delay(startDelayMillis)
    }
    sendVideoChannelHeader(videoChannel)
    controlChannel.write(ByteBuffer.wrap("C".toByteArray()))
    if (startVideoStream) {
      val displayStreamer = DisplayStreamer(PRIMARY_DISPLAY_ID, maxVideoResolution, true, bitRate, videoChannel)
      this.displayStreamers.put(displayStreamer.displayId, displayStreamer)
      displayStreamer.renderDisplay()
    }
    val controller = Controller(controlChannel)
    this.controller = controller
    deviceState.deleteFile("$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_SO_NAME")
    deviceState.deleteFile("$DEVICE_PATH_BASE/$SCREEN_SHARING_AGENT_JAR_NAME")
    if (startTime == 0L) {
      // Shutdown has been triggered - abort run.
      shutdownChannels()
    }
    else {
      isRunning = true
      try {
        controller.run()
      }
      finally {
        isRunning = false
      }
    }
  }

  private suspend fun sendVideoChannelHeader(videoChannel: SuspendingSocketChannel) {
    // Send the channel header with the name of the codec.
    val header = ByteBuffer.allocate(CHANNEL_HEADER_LENGTH + 1)
    header.put('V'.code.toByte())
    header.put(codecName.toByteArray())
    while (header.hasRemaining()) {
      header.put(' '.code.toByte())
    }
    header.flip()
    videoChannel.writeFully(header)
  }

  /**
   * Stops the agent.
   */
  suspend fun stop(exitCode: Int = 0) {
    withContext(singleThreadedDispatcher) {
      terminateAgent(exitCode)
    }
  }

  /**
   * Simulates a crash of the agent. The agent dies without a normal shutdown.
   */
  suspend fun crash() {
    stop(AGENT_SIGSEGV)
  }

  /** Adds a device display. */
  fun addDisplay(displayId: Int, width: Int, height: Int, displayType: DisplayType) {
    executor.execute {
      if (displays.find { it.displayId == displayId } == null) {
        displays = (displays + DisplayDescriptor(displayId, width, height, 0, displayType)).sortedBy { it.displayId }
        sendNotificationOrResponse(DisplayAddedNotification(displayId))
      }
      else {
        thisLogger().error("Display $displayId already exists")
      }
    }
  }

  /** Removes a device display. */
  fun removeDisplay(displayId: Int) {
    require(displayId != PRIMARY_DISPLAY_ID)
    executor.execute {
      val displayCount = displays.size
      displays = displays.filter { it.displayId != displayId }
      if (displays.size != displayCount) {
        sendNotificationOrResponse(DisplayRemovedNotification(displayId))
      }
    }
  }

  suspend fun writeToStderr(message: String) {
    withContext(singleThreadedDispatcher) {
      shellProtocol?.writeStderr(message)
    }
  }

  fun getFrameNumber(displayId: Int = PRIMARY_DISPLAY_ID): UInt {
    return displayStreamers[displayId]?.frameNumber ?: 0u
  }

  private fun parseArgs(command: String) {
    val args = command.split(Regex("\\s+"))
    for (arg in args) {
      when {
        arg.startsWith("--max_size=") -> {
          val dimensions = arg.substring("--max_size=".length).split(",")
          if (dimensions.size == 2) {
            maxVideoResolution = Dimension(dimensions[0].toInt(), dimensions[1].toInt())
          }
        }

        arg.startsWith("--orientation=") -> {
          deviceOrientation = arg.substring("--orientation=".length).toInt()
        }

        arg.startsWith("--flags=") -> {
          startVideoStream = (arg.substring("--flags=".length).toInt() and START_VIDEO_STREAM) != 0
        }
      }
    }
  }

  private suspend fun terminateAgent(exitCode: Int) {
    try {
      shellProtocol?.writeExitCode(exitCode)
    }
    catch (_: SocketException) {
      // Can happen if the shellProtocol's socket is already closed.
    }
    catch(_: ClosedChannelException) {
      // Can happen if the shellProtocol's socket is already closed.
    }
    shellProtocol = null
    shutdown()
  }

  override fun dispose() {
    runBlocking {
      withContext(singleThreadedDispatcher) {
        shutdown()
      }
    }
    executor.shutdown()
  }

  private suspend fun shutdown() {
    if (startTime != 0L) {
      startTime = 0
      shutdownChannels()
    }
  }

  private suspend fun shutdownChannels() {
    controller?.let {
      it.shutdown()
      Disposer.dispose(it)
      controller = null
    }
    displayStreamers.clear()
  }

  suspend fun renderDisplay(displayId: Int) {
    return withContext(singleThreadedDispatcher) {
      displayStreamers[displayId]?.renderDisplay()
    }
  }

  suspend fun renderDisplay(displayId: Int, flavor: Int) {
    return withContext(singleThreadedDispatcher) {
      displayStreamers[displayId]?.renderDisplay(flavor)
    }
  }

  suspend fun setDisplayOrientationCorrection(displayId: Int, value: Int) {
    withContext(singleThreadedDispatcher) {
      displayStreamers[displayId]?.apply {
        displayOrientationCorrection = value
        renderDisplay()
      }
    }
  }

  /**
   * Waits for the next control message to be received by the agent while dispatching UI events.
   * Returns the next control message and removes it from the queue of recorded messages. Throws
   * TimeoutException if the control message is not received within the specified timeout.
   */
  @UiThread
  @Throws(TimeoutException::class)
  fun getNextControlMessage(timeout: Long, unit: TimeUnit,
                            filter: Predicate<ControlMessage> = defaultControlMessageFilter): ControlMessage {
    val timeoutMillis = unit.toMillis(timeout)
    val deadline = System.currentTimeMillis() + timeoutMillis
    var waitUnit = ((timeoutMillis + 9) / 10).coerceAtMost(10)
    while (waitUnit > 0) {
      UIUtil.dispatchAllInvocationEvents()
      val command = commandLog.poll(waitUnit, TimeUnit.MILLISECONDS)
      if (command != null && filter.test(command)) {
        return command
      }
      waitUnit = waitUnit.coerceAtMost(deadline - System.currentTimeMillis())
    }
    throw TimeoutException()
  }

  /**
   * Clears the command log.
   */
  @UiThread
  fun clearCommandLog() {
    commandLog.clear()
  }

  private fun drawDisplayImage(size: Dimension, imageFlavor: Int, displayId: Int): BufferedImage {
    val image = BufferedImage(size.width, size.height, BufferedImage.TYPE_3BYTE_BGR)
    val g = image.createGraphics()
    g.paint = Color.WHITE
    g.fillRect(0, 0, size.width, size.height) // Create white background so that antialiasing is done against that background.
    val hints = RenderingHints(
      mapOf(
        RenderingHints.KEY_ANTIALIASING to RenderingHints.VALUE_ANTIALIAS_ON,
        RenderingHints.KEY_RENDERING to RenderingHints.VALUE_RENDER_QUALITY
      )
    )
    g.setRenderingHints(hints)
    val n = 10
    val m = 10
    val w = size.width.toDouble() / n
    val h = size.height.toDouble() / m
    val colorScheme = COLOR_SCHEMES[displayId % COLOR_SCHEMES.size]
    val startColor1 = colorScheme.start1
    val endColor1 = colorScheme.end1
    val startColor2 = colorScheme.start2
    val endColor2 = colorScheme.end2
    for (i in 0 until n) {
      for (j in 0 until m) {
        val x = w * i
        val y = h * j
        val triangle1 = Path2D.Double().apply {
          moveTo(x, y)
          lineTo(x + w, y)
          lineTo(x, y + h)
          closePath()
        }
        val triangle2 = Path2D.Double().apply {
          moveTo(x + w, y + h)
          lineTo(x + w, y)
          lineTo(x, y + h)
          closePath()
        }
        g.paint = interpolate(startColor1, endColor1, i.toDouble() / (n - 1))
        g.fill(triangle1)
        g.paint = interpolate(startColor2, endColor2, j.toDouble() / (m - 1))
        g.fill(triangle2)
      }
    }

    // Draw a while square in the center of the display. The size of the square is determined
    // by the value of the flavor parameter.
    g.paint = Color.WHITE
    val s = imageFlavor * 10 % (min(size.width, size.height) / 2)
    if (s != 0) {
      val centerX = size.width / 2
      val centerY = size.height / 2
      g.fillRect(centerX - s, centerY - s, centerX + s, centerY + s)
    }

    g.dispose()
    return image
  }

  private suspend fun setDeviceOrientation(message: SetDeviceOrientationMessage) {
    deviceOrientation = message.orientation
    for (display in displays) {
      if (display.type == DisplayType.INTERNAL) {
        displayStreamers[display.displayId]?.renderDisplay()
      }
    }
  }

  private suspend fun setMaxVideoResolutionMessage(message: SetMaxVideoResolutionMessage) {
    if (message.displayId == PRIMARY_DISPLAY_ID) {
      maxVideoResolution = message.maxVideoSize
    }
    val displayStreamer = displayStreamers[message.displayId] ?: return
    displayStreamer.maxVideoResolution = message.maxVideoSize
    displayStreamer.renderDisplay()
  }

  private suspend fun startVideoStream(message: StartVideoStreamMessage) {
    val displayId = message.displayId
    if (displayId == PRIMARY_DISPLAY_ID) {
      maxVideoResolution = message.maxVideoSize
    }
    val display = displays.find { it.displayId == displayId } ?: return
    val displayStreamer = displayStreamers.computeIfAbsent(
        displayId,
        Int2ObjectFunction {
          dispId -> DisplayStreamer(dispId, message.maxVideoSize, rotatedWithDevice = display.type == DisplayType.INTERNAL,
                                    bitRate, videoChannel!!)
        })
    displayStreamer.renderDisplay()
    assert(videoStreamActive)
  }

  private fun stopVideoStream(message: StopVideoStreamMessage) {
    displayStreamers.remove(message.displayId)
  }

  private fun startClipboardSync(message: StartClipboardSyncMessage) {
    clipboardInternal.set(message.text)
    clipboardSynchronizationActive.set(true)
  }

  private fun stopClipboardSync() {
    clipboardSynchronizationActive.set(false)
  }

  private fun requestDeviceState(message: RequestDeviceStateMessage) {
    checkNotNull(foldedSize) { "The device is not foldable" }
    if (foldingState?.ordinal != message.state) {
      foldingState = FoldingState.values()[message.state]
      sendDeviceStateNotification()
      agentsScope.launch {
        for (displayStreamer in displayStreamers.values) {
          displayStreamer.renderDisplay()
        }
      }
    }
  }

  private fun sendDeviceStateNotification() {
    sendNotificationOrResponse(DeviceStateNotification(foldingState!!.ordinal))
  }

  private fun sendDisplayConfigurations(message: DisplayConfigurationRequest) {
    sendNotificationOrResponse(DisplayConfigurationResponse(message.requestId, displays.withDeviceOrientation(deviceOrientation)))
  }

  private fun sendUiSettingsResponse(message: UiSettingsRequest) {
    sendNotificationOrResponse(UiSettingsResponse(message.requestId, darkMode))
  }

  private fun setDarkMode(message: SetDarkModeMessage) {
    darkMode = message.darkMode
  }

  private fun sendNotificationOrResponse(message: ControlMessage) {
    controller?.sendNotificationOrResponse(message)
  }

  private inner class DisplayStreamer(
    val displayId: Int,
    var maxVideoResolution: Dimension,
    private val rotatedWithDevice: Boolean,
    initialBitRate: Int,
    private val channel: SuspendingSocketChannel,
  ) {

    private val packetHeader = VideoPacketHeader(displayId, displaySize, initialBitRate, roundDisplay)
    var bitRate: Int
      get() = packetHeader.bitRate
      set(value) { packetHeader.bitRate = value }
    private var presentationTimestampOffset = 0L
    private var lastImageFlavor: Int = 0
    var displayOrientationCorrection: Int = 0
    @Volatile var frameNumber: UInt = 0u
      private set

    /**
     * Renders display content using the last used image flavor and sends all produced video frames.
     */
    suspend fun renderDisplay() {
      renderDisplay(lastImageFlavor)
    }

    /**
     * Renders display content for the given [imageFlavor] and sends all produced video frames.
     */
    suspend fun renderDisplay(imageFlavor: Int) {
      lastImageFlavor = imageFlavor

      val size = computeDisplayImageSize()
      val videoSize = Dimension(size.width, size.height.roundUpToMultipleOf8())
      val encoderContext = avcodec_alloc_context3(videoEncoder)?.apply {
        bit_rate(8000000L)
        time_base(av_make_q(1, 1000))
        framerate(av_make_q(FRAME_RATE, 1))
        gop_size(2)
        max_b_frames(1)
        pix_fmt(videoEncoder.pix_fmts().get())
        width(videoSize.width)
        height(videoSize.height)
      } ?: throw RuntimeException("Could not allocate encoder context")

      if (avcodec_open2(encoderContext, videoEncoder, null as AVDictionary?) < 0) {
        throw RuntimeException("avcodec_open2 failed")
      }
      val encodingFrame = av_frame_alloc().apply {
        format(encoderContext.pix_fmt())
        width(videoSize.width)
        height(videoSize.height)
      }
      if (av_frame_get_buffer(encodingFrame, 0) < 0) {
        throw RuntimeException("av_frame_get_buffer failed")
      }
      if (av_frame_make_writable(encodingFrame) < 0) {
        throw RuntimeException("av_frame_make_writable failed")
      }

      val orientation = if (rotatedWithDevice) deviceOrientation else 0
      val image = drawDisplayImage(size.rotatedByQuadrants(-orientation), imageFlavor, displayId).rotatedByQuadrants(orientation)

      val rgbFrame = av_frame_alloc().apply {
        format(AV_PIX_FMT_BGR24)
        width(videoSize.width)
        height(videoSize.height)
      }
      if (av_frame_get_buffer(rgbFrame, 1) < 0) {
        throw RuntimeException("Could not allocate the video frame data")
      }

      // Copy the image to the frame with conversion to the destination format.
      val dataBufferByte = image.raster.dataBuffer as DataBufferByte
      val numBytes = av_image_get_buffer_size(rgbFrame.format(), rgbFrame.width(), rgbFrame.height(), 1)
      val byteBuffer = rgbFrame.data(0).asByteBufferOfSize(numBytes)
      val y = (videoSize.height - size.height) / 2
      // Fill the extra strip at the top with black three bytes per pixel.
      byteBuffer.fill(0.toByte(), y * rgbFrame.width() * 3)
      byteBuffer.put(dataBufferByte.data)
      // Fill the extra strip at the bottom with black three bytes per pixel.
      byteBuffer.fill(0.toByte(), (videoSize.height - y - size.height) * rgbFrame.width() * 3)
      val swsContext = sws_getContext(rgbFrame.width(), rgbFrame.height(), rgbFrame.format(),
                                      encodingFrame.width(), encodingFrame.height(), encodingFrame.format(),
                                      SWS_BICUBIC, null, null, null as DoublePointer?)!!
      sws_scale(swsContext, rgbFrame.data(), rgbFrame.linesize(), 0, rgbFrame.height(), encodingFrame.data(), encodingFrame.linesize())
      sws_freeContext(swsContext)
      av_frame_free(rgbFrame)

      val timestamp = System.currentTimeMillis()
      encodingFrame.pts(timestamp)

      val packet = av_packet_alloc()

      try {
        sendFrame(encoderContext, encodingFrame, packet)
        sendFrame(encoderContext, null, packet) // Process delayed frames.
      }
      finally {
        av_packet_free(packet)
        av_frame_free(encodingFrame)
        avcodec_free_context(encoderContext)
      }
    }

    /**
     * Sends the given frame or, if [frame] is null, sends the delayed frames.
     */
    private suspend fun sendFrame(encoderContext: AVCodecContext, frame: AVFrame?, packet: AVPacket) {
      if (avcodec_send_frame(encoderContext, frame) < 0) {
        throw RuntimeException("avcodec_send_frame failed")
      }

      while (true) {
        val ret = avcodec_receive_packet(encoderContext, packet)
        if (ret != 0) {
          if (ret != AVERROR_EAGAIN() && ret != AVERROR_EOF()) {
            throw RuntimeException("avcodec_receive_packet returned $ret")
          }
          break
        }

        val pts = packet.pts()
        if (pts == AV_NOPTS_VALUE) {
          packetHeader.presentationTimestampUs = 0
        }
        else {
          val ptsUs = pts * 1000
          if (presentationTimestampOffset == 0L) {
            presentationTimestampOffset = ptsUs - 1
          }
          packetHeader.presentationTimestampUs = ptsUs - presentationTimestampOffset
        }
        packetHeader.originationTimestampUs = System.currentTimeMillis() * 1000
        packetHeader.displaySize.size = getFoldedDisplaySize()
        packetHeader.displayOrientation = deviceOrientation
        packetHeader.displayOrientationCorrection = displayOrientationCorrection
        packetHeader.frameNumber = ++frameNumber
        val packetSize = packet.size()
        val packetData = packet.data().asByteBufferOfSize(packetSize)
        packetHeader.packetSize = packetSize
        val buffer = VideoPacketHeader.createBuffer(packetSize)
        packetHeader.serialize(buffer)
        buffer.put(packetData)
        buffer.flip()
        try {
          channel.writeFully(buffer)
        }
        catch (e: IOException) {
          if (!isLostConnection(e)) { // Lost connection is not an error because it means that the other end closed the socket connection.
            throw e
          }
        }
      }
    }

    private fun computeDisplayImageSize(): Dimension {
      // The same logic as in ComputeVideoSize in display_streamer.cc except for rounding of height.
      val rotatedDisplaySize = getFoldedDisplaySize().rotatedByQuadrants(deviceOrientation)
      val displayWidth = rotatedDisplaySize.width.toDouble()
      val displayHeight = rotatedDisplaySize.height.toDouble()
      val maxResolutionWidth = min(max(maxVideoResolution.width, rotatedDisplaySize.width / 2), maxVideoEncoderResolution)
      val maxResolutionHeight = min(max(maxVideoResolution.height, rotatedDisplaySize.height / 2), maxVideoEncoderResolution)
      val scale = max(min(1.0, min(maxResolutionWidth / displayWidth, maxResolutionHeight / displayHeight)),
                      max(MIN_VIDEO_RESOLUTION / displayWidth, MIN_VIDEO_RESOLUTION / displayHeight))
      val width = (displayWidth * scale).roundToInt().roundUpToMultipleOf8()
      val height = (width * displayHeight / displayWidth).roundToInt().roundUpToMultipleOf2()
      return Dimension(width, height)
    }

    private fun getFoldedDisplaySize(): Dimension {
      return when (foldingState) {
        FoldingState.CLOSED, FoldingState.TENT -> foldedSize ?: displaySize
        else -> displaySize
      }
    }

    private fun Int.roundUpToMultipleOf8(): Int =
      (this + 7) and 7.inv()

    private fun Int.roundUpToMultipleOf2(): Int =
      (this + 1) and 1.inv()

    private fun BufferedImage.rotatedByQuadrants(quadrants: Int): BufferedImage =
      ImageUtils.rotateByQuadrants(this, quadrants)

    private fun Pointer.asByteBufferOfSize(size: Int): ByteBuffer =
      BytePointer(this).apply { capacity(size.toLong()) }.asByteBuffer()
  }

  private class VideoPacketHeader(val displayId: Int, val displaySize: Dimension, bitRate: Int, val roundDisplay: Boolean = false) {

    var displayOrientation: Int = 0
    var displayOrientationCorrection: Int = 0
    var bitRateReduced: Boolean = false
    var bitRate: Int = bitRate
      set(value) {
        if (value < field) {
          bitRateReduced = true
        }
        field = value
      }
    var frameNumber: UInt = 0u
    var originationTimestampUs: Long = 0
    var presentationTimestampUs: Long = 0
    var packetSize: Int = 0

    fun serialize(buffer: ByteBuffer) {
      buffer.putInt(displayId)
      buffer.putInt(displaySize.width)
      buffer.putInt(displaySize.height)
      buffer.put(displayOrientation.toByte())
      buffer.put(displayOrientationCorrection.toByte())
      buffer.putShort(((if (roundDisplay) FLAG_DISPLAY_ROUND else 0) or (if (bitRateReduced) FLAG_BIT_RATE_REDUCED else 0)).toShort())
      buffer.putInt(bitRate)
      buffer.putUInt(frameNumber)
      buffer.putLong(originationTimestampUs)
      buffer.putLong(presentationTimestampUs)
      buffer.putInt(packetSize)

      bitRateReduced = false
    }

    companion object {
      fun createBuffer(packetSize: Int): ByteBuffer =
          ByteBuffer.allocate(WIRE_SIZE + packetSize).order(LITTLE_ENDIAN)

      // Flag definitions from video_packet_header.h.
      /** Device display is round. */
      private const val FLAG_DISPLAY_ROUND = 0x01
      /** Bit rate reduced compared to the previous frame or, for the very first flame, to the initial value. */
      private const val FLAG_BIT_RATE_REDUCED = 0x02

      private const val WIRE_SIZE =
          4 + // displayId
          4 + // width
          4 + // height
          1 + // displayOrientation
          1 + // displayOrientationCorrection
          2 + // flags
          4 + // bitRate
          4 + // frameNumber
          8 + // originationTimestampUs
          8 + // presentationTimestampUs
          4   // packetSize
    }
  }

  private inner class Controller(private val channel: SuspendingSocketChannel) : Disposable {

    val input = newInputStream(channel, CONTROL_MSG_BUFFER_SIZE)
    val codedInput = Base128InputStream(input)
    val codedOutput = Base128OutputStream(newOutputStream(channel, CONTROL_MSG_BUFFER_SIZE))

    suspend fun run() {
      var exitCode = 0
      try {
        if (foldedSize != null) {
          val supportedStates = """
              Supported states: [
                DeviceState{identifier=0, name='CLOSE', app_accessible=true},
                DeviceState{identifier=1, name='TENT'},
                DeviceState{identifier=2, name='HALF_FOLDED', app_accessible=true},
                DeviceState{identifier=3, name='OPEN', app_accessible=true},
                DeviceState{identifier=4, name='REAR_DISPLAY_STATE', app_accessible=true},
                DeviceState{identifier=5, name='CONCURRENT_INNER_DEFAULT', app_accessible=true, cancel_when_requester_not_on_top=true},
                DeviceState{identifier=6, name='FLIPPED', app_accessible=true},
              ]
              """.trimIndent()
          sendNotificationOrResponse(SupportedDeviceStatesNotification(supportedStates))
          sendDeviceStateNotification()
        }

        while (true) {
          if (codedInput.available() == 0) {
            input.waitForData(1)
          }
          val message = ControlMessage.deserialize(codedInput)
          processControlMessage(message)
        }
      }
      catch (_: EOFException) {
      }
      catch (e: IOException) {
        if (!isLostConnection(e)) {
          exitCode = 139
          throw e
        }
      }
      finally {
        terminateAgent(exitCode)
      }
    }

    suspend fun shutdown() {
      if (channel.isOpen) {
        channel.close()
      }
    }

    override fun dispose() {
      runBlocking {
        shutdown()
      }
    }

    fun sendNotificationOrResponse(message: ControlMessage) {
      try {
        message.serialize(codedOutput)
        codedOutput.flush()
      }
      catch (_: ClosedChannelException) {
      }
    }

    private suspend fun processControlMessage(message: ControlMessage) {
      when (message) {
        is SetDeviceOrientationMessage -> setDeviceOrientation(message)
        is SetMaxVideoResolutionMessage -> setMaxVideoResolutionMessage(message)
        is StartVideoStreamMessage -> startVideoStream(message)
        is StopVideoStreamMessage -> stopVideoStream(message)
        is StartClipboardSyncMessage -> startClipboardSync(message)
        is StopClipboardSyncMessage -> stopClipboardSync()
        is RequestDeviceStateMessage -> requestDeviceState(message)
        is DisplayConfigurationRequest -> sendDisplayConfigurations(message)
        is UiSettingsRequest -> sendUiSettingsResponse(message)
        is SetDarkModeMessage -> setDarkMode(message)
        else -> {}
      }
      commandLog.add(message)
    }
  }

  class ControlMessageFilter(private vararg val messageTypesToIgnore: Int) : Predicate<ControlMessage> {
    override fun test(message: ControlMessage): Boolean {
      return message.type !in messageTypesToIgnore
    }

    fun or(vararg moreMethodNamesToIgnore: Int): ControlMessageFilter {
      return ControlMessageFilter(*messageTypesToIgnore + intArrayOf(*moreMethodNamesToIgnore))
    }
  }

  /**
   * Makes sure that when one of the given channels is closed, other channels are closed too.
   */
  private class ChannelClosingSynchronizer(private val channels: List<SuspendingSocketChannel>) {

    fun start() {
      CoroutineScope(Dispatchers.IO).launch {
        while (true) {
          for (channel1 in channels) {
            if (!channel1.isOpen) {
              for (channel2 in channels) {
                if (channel2 != channel1 && channel2.isOpen) {
                  try {
                    channel2.close()
                  }
                  catch (_: IOException) {
                  }
                }
              }
              return@launch
            }
          }
          delay(100)
        }
      }
    }
  }

  private enum class FoldingState { CLOSED, TENT, HALF_FOLDED, OPEN, REAR_DISPLAY_STATE, CONCURRENT_INNER_DEFAULT, FLIPPED }

  companion object {
    @JvmStatic
    val defaultControlMessageFilter = ControlMessageFilter(DisplayConfigurationRequest.TYPE)
  }
}

private fun isLostConnection(exception: IOException): Boolean {
  var ex: Throwable? = exception
  while (ex is IOException) {
    if (ex is ClosedChannelException || ex.message == "Broken pipe") {
      return true
    }
    ex = ex.cause
  }
  return false
}

private fun ByteBuffer.fill(b: Byte, count: Int) {
  for (i in 0 until count) {
    put(b)
  }
}

private fun DisplayDescriptor.withDeviceOrientation(orientation: Int): DisplayDescriptor {
  return if (type != DisplayType.INTERNAL || orientation == this.orientation) this
         else DisplayDescriptor(displayId, size, orientation, type)
}

private fun List<DisplayDescriptor>.withDeviceOrientation(orientation: Int) =
    map { it.withDeviceOrientation(orientation) }

private class ColorScheme(val start1: Color, val end1: Color, val start2: Color, val end2: Color)

private val COLOR_SCHEMES = listOf(ColorScheme(Color(236, 112, 99), Color(250, 219, 216), Color(212, 230, 241), Color(84, 153, 199)),
                                   ColorScheme(Color(154, 236, 99), Color(230, 250, 216), Color(238, 212, 241), Color(188, 84, 199)),
                                   ColorScheme(Color(99, 222, 236), Color(216, 247, 250), Color(241, 223, 212), Color(199, 130, 84)),
                                   ColorScheme(Color(181, 99, 236), Color(236, 216, 250), Color(215, 241, 212), Color(95, 199, 84)))

private const val FRAME_RATE = 60
private const val DEFAULT_BIT_RATE = 10000000
private const val CHANNEL_HEADER_LENGTH = 20
private const val CONTROL_MSG_BUFFER_SIZE = 4096
private const val MIN_VIDEO_RESOLUTION = 128.0
