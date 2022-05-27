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

import com.android.annotations.concurrency.UiThread
import com.android.fakeadbserver.ShellV2Protocol
import com.android.tools.adtui.ImageUtils
import com.android.tools.idea.emulator.PRIMARY_DISPLAY_ID
import com.android.tools.idea.emulator.interpolate
import com.android.tools.idea.emulator.rotatedByQuadrants
import com.android.tools.idea.flags.StudioFlags
import com.android.utils.Base128InputStream
import com.android.utils.Base128OutputStream
import com.android.utils.FlightRecorder
import com.android.utils.TraceUtils
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_H264
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP8
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_VP9
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
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.channels.ClosedChannelException
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Predicate
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Fake Screen Sharing Agent for use in tests.
 */
class FakeScreenSharingAgent(val displaySize: Dimension) : Disposable {

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor("FakeScreenSharingAgent", 1)
  private val coroutineScope = CoroutineScope(executor.asCoroutineDispatcher() + Job())
  private var startTime = 0L

  private val displayId = PRIMARY_DISPLAY_ID
  private var controller: Controller? = null
  private var displayStreamer: DisplayStreamer? = null

  private val clipboardInternal = AtomicReference("")
  val clipboardSynchronizationActive = AtomicBoolean()
  var clipboard: String
    get() = clipboardInternal.get()
    set(value) {
      val oldValue = clipboardInternal.getAndSet(value)
      if (value != oldValue) {
        coroutineScope.launch {
          if (clipboardSynchronizationActive.get()) {
            sendNotification(ClipboardChangedMessage(value))
          }
        }
      }
    }

  @Volatile
  var commandLine: String? = null
  val commandLog = LinkedBlockingDeque<ControlMessage>()
  @Volatile
  var started = false
  @Volatile
  var frameNumber: Long = 0

  private var shellProtocol: ShellV2Protocol? = null

  /**
   * Starts the agent. The agent is fully initialized when the method returns.
   */
  fun start(protocol: ShellV2Protocol, command: String, hostPort: Int) {
    FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.start" }
    coroutineScope.launch {
      commandLine = command
      shellProtocol = protocol
      commandLog.clear()
      startTime = System.currentTimeMillis()

      FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.start startTime=$startTime" }
      val videoChannel = SuspendingSocketChannel.open()
      val controlChannel = SuspendingSocketChannel.open()
      val socketAddress = InetSocketAddress("localhost", hostPort)
      videoChannel.connect(socketAddress)
      controlChannel.connect(socketAddress)
      FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.start connected channels" }
      val displayStreamer = DisplayStreamer(videoChannel)
      this@FakeScreenSharingAgent.displayStreamer = displayStreamer
      val controller = Controller(controlChannel)
      this@FakeScreenSharingAgent.controller = controller
      displayStreamer.start()
      started = true
      FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.started" }
      controller.run()
    }
  }

  /**
   * Stops the agent.
   */
  suspend fun stop() {
    coroutineScope.run {
      shutdown()
      shellProtocol?.writeExitCode(0)
    }
  }

  /**
   * Simulates a crash of the agent. The agent dies without a normal shutdown
   */
  suspend fun crash() {
    coroutineScope.run {
      shutdown()
      shellProtocol?.writeExitCode(139)
    }
  }

  override fun dispose() {
    runBlocking {
      coroutineScope.run {
        shutdown()
      }
    }
  }

  private suspend fun shutdown() {
    if (startTime != 0L) {
      controller?.let {
        it.shutdown()
        Disposer.dispose(it)
        controller = null
      }
      displayStreamer?.let {
        it.shutdown()
        Disposer.dispose(it)
        displayStreamer = null
      }
      startTime = 0
      started = false
    }
  }

  suspend fun renderDisplay(flavor: Int) {
    return coroutineScope.run {
      displayStreamer?.renderDisplay(flavor)
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
      val call = commandLog.poll(waitUnit, TimeUnit.MILLISECONDS)
      if (call != null && filter.test(call)) {
        return call
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
    val colorScheme = COLOR_SCHEMES[displayId]
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
    displayStreamer?.let {
      it.displayOrientation = message.orientation
      it.renderDisplay()
    }
  }

  private suspend fun setMaxVideoResolutionMessage(message: SetMaxVideoResolutionMessage) {
    displayStreamer?.let {
      it.maxVideoResolution = Dimension(message.width, message.height)
      it.renderDisplay()
    }
  }

  private fun startClipboardSync(message: StartClipboardSyncMessage) {
    clipboard = message.text
    clipboardSynchronizationActive.set(true)
  }

  private fun stopClipboardSync() {
    clipboardSynchronizationActive.set(false)
  }

  private fun sendNotification(message: ControlMessage) {
    controller?.sendNotification(message)
  }

  private inner class DisplayStreamer(private val channel: SuspendingSocketChannel) : Disposable {

    var maxVideoResolution: Dimension? = null
    var displayOrientation: Int = 0

    private val codecName = StudioFlags.DEVICE_MIRRORING_VIDEO_CODEC.get()
    private val encoder: AVCodec
    private val packetHeader = VideoPacketHeader(displaySize)
    private var presentationTimestampOffset = 0L
    private var lastImageFlavor: Int = 0
    @Volatile var stopped = false

    init {
      // Use avcodec_find_encoder instead of avcodec_find_encoder_by_name because the names of encoders and decoders don't match.
      val codecId = when (codecName) {
        "vp8" -> AV_CODEC_ID_VP8
        "vp9" -> AV_CODEC_ID_VP9
        "h264" -> AV_CODEC_ID_H264
        else -> throw RuntimeException("$codecName encoder not found")
      }

      encoder = avcodec_find_encoder(codecId) ?: throw RuntimeException("$codecName encoder not found")
    }

    suspend fun start() {
      FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.DisplayStreamer.start" }
      // Send the channel header with the name of the codec.
      val header = ByteBuffer.allocate(CHANNEL_HEADER_LENGTH)
      header.put(codecName.toByteArray())
      while (header.hasRemaining()) {
        header.put(' '.code.toByte())
      }
      header.flip()
      channel.writeFully(header)

      // Send the initial set of frames.
      renderDisplay(0)
    }

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
      FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.DisplayStreamer.renderDisplay" }
      lastImageFlavor = imageFlavor

      val size = getScaledAndRotatedDisplaySize()
      val encoderContext = avcodec_alloc_context3(encoder)?.apply {
        bit_rate(8000000L)
        time_base(av_make_q(1, 1000))
        framerate(av_make_q(FRAME_RATE, 1))
        gop_size(2)
        max_b_frames(1)
        pix_fmt(encoder.pix_fmts().get())
        width(size.width)
        height(size.height)
      } ?: throw RuntimeException("Could not allocate encoder context")

      if (avcodec_open2(encoderContext, encoder, null as AVDictionary?) < 0) {
        throw RuntimeException("avcodec_open2 failed")
      }
      val encodingFrame = av_frame_alloc().apply {
        format(encoderContext.pix_fmt())
        width(size.width)
        height(size.height)
      }
      if (av_frame_get_buffer(encodingFrame, 0) < 0) {
        throw RuntimeException("av_frame_get_buffer failed")
      }
      if (av_frame_make_writable(encodingFrame) < 0) {
        throw RuntimeException("av_frame_make_writable failed")
      }

      val image = drawDisplayImage(size.rotatedByQuadrants(-displayOrientation), imageFlavor, displayId)
        .rotatedByQuadrants(displayOrientation)

      val rgbFrame = av_frame_alloc().apply {
        format(AV_PIX_FMT_BGR24)
        width(size.width)
        height(size.height)
      }
      if (av_frame_get_buffer(rgbFrame, 1) < 0) {
        throw RuntimeException("Could not allocate the video frame data")
      }

      // Copy the image to the frame with conversion to the destination format.
      val dataBufferByte = image.raster.dataBuffer as DataBufferByte
      val numBytes = av_image_get_buffer_size(rgbFrame.format(), rgbFrame.width(), rgbFrame.height(), 1)
      rgbFrame.data(0).asByteBufferOfSize(numBytes).put(dataBufferByte.data)
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
          FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.DisplayStreamer.sendFrame no more packets" }
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
        packetHeader.displayOrientation = displayOrientation
        packetHeader.frameNumber = ++frameNumber
        val packetSize = packet.size()
        val packetData = packet.data().asByteBufferOfSize(packetSize)
        packetHeader.packetSize = packetSize
        val buffer = ByteBuffer.allocate(VideoPacketHeader.WIRE_SIZE + packetSize).order(LITTLE_ENDIAN)
        packetHeader.serialize(buffer)
        buffer.put(packetData)
        buffer.flip()
        try {
          FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.DisplayStreamer.sendFrame sending packet ${packetHeader.frameNumber}" }
          channel.writeFully(buffer)
        }
        catch (e: IOException) {
          if (!isLostConnection(e)) { // Lost connection is not an error because it means that the other end closed the socket connection.
            throw e
          }
        }
      }
    }

    suspend fun shutdown() {
      FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.DisplayStreamer.shutdown sending packet ${packetHeader.frameNumber}" }
      stopped = true
      if (channel.isOpen) {
        channel.close()
      }
    }

    override fun dispose() {
      FlightRecorder.log { "${TraceUtils.currentTime()} FakeScreenSharingAgent.DisplayStreamer.dispose sending packet ${packetHeader.frameNumber}" }
      runBlocking {
        shutdown()
      }
    }

    private fun getScaledAndRotatedDisplaySize(): Dimension {
      val maxResolution = maxVideoResolution ?: return displaySize
      val aspectRatio = displaySize.height.toDouble() / displaySize.width
      return if (displayOrientation % 2 == 0) {
        val width = displaySize.width.coerceAtMost((maxResolution.height / aspectRatio).toInt()).roundUpToMultipleOf8()
        Dimension(width, (width * aspectRatio).roundToInt().roundToMultipleOf2().coerceAtMost(displaySize.height))
      }
      else {
        val width = displaySize.height.coerceAtMost((maxResolution.width * aspectRatio).toInt()).roundUpToMultipleOf8()
        Dimension(width, (width / aspectRatio).roundToInt().roundToMultipleOf2().coerceAtMost(displaySize.width))
      }
    }

    private fun Int.roundUpToMultipleOf8(): Int =
      (this + 7) and 7.inv()

    fun Int.roundToMultipleOf2(): Int =
      this and 1.inv()

    private fun BufferedImage.rotatedByQuadrants(quadrants: Int): BufferedImage =
      ImageUtils.rotateByQuadrants(this, quadrants)

    private fun Pointer.asByteBufferOfSize(size: Int): ByteBuffer =
      BytePointer(this).apply { capacity(size.toLong()) }.asByteBuffer()
  }

  private class VideoPacketHeader(val displaySize: Dimension) {
    var displayOrientation: Int = 0
    var packetSize: Int = 0
    var frameNumber: Long = 0
    var originationTimestampUs: Long = 0
    var presentationTimestampUs: Long = 0

    fun serialize(buffer: ByteBuffer) {
      buffer.putInt(displaySize.width)
      buffer.putInt(displaySize.height)
      buffer.putInt(displayOrientation)
      buffer.putInt(packetSize)
      buffer.putLong(frameNumber)
      buffer.putLong(originationTimestampUs)
      buffer.putLong(presentationTimestampUs)
    }

    companion object {
      const val WIRE_SIZE = 4 + 4 + 4 + 4 + 8 + 8 + 8
    }
  }

  private inner class Controller(private val channel: SuspendingSocketChannel) : Disposable {

    val input = newInputStream(channel, CONTROL_MSG_BUFFER_SIZE)
    val codedInput = Base128InputStream(input)
    val codedOutput = Base128OutputStream(newOutputStream(channel, CONTROL_MSG_BUFFER_SIZE))

    suspend fun run() {
      try {
        while (true) {
          @Suppress("BlockingMethodInNonBlockingContext") // The InputStream.available method is non-blocking.
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
          throw e
        }
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

    fun sendNotification(message: ControlMessage) {
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
        is StartClipboardSyncMessage -> startClipboardSync(message)
        is StopClipboardSyncMessage -> stopClipboardSync()
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

  companion object {
    @JvmStatic
    val defaultControlMessageFilter = ControlMessageFilter()
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

private class ColorScheme(val start1: Color, val end1: Color, val start2: Color, val end2: Color)

private val COLOR_SCHEMES = listOf(ColorScheme(Color(236, 112, 99), Color(250, 219, 216), Color(212, 230, 241), Color(84, 153, 199)),
                                   ColorScheme(Color(154, 236, 99), Color(230, 250, 216), Color(238, 212, 241), Color(188, 84, 199)),
                                   ColorScheme(Color(99, 222, 236), Color(216, 247, 250), Color(241, 223, 212), Color(199, 130, 84)),
                                   ColorScheme(Color(181, 99, 236), Color(236, 216, 250), Color(215, 241, 212), Color(95, 199, 84)))

private const val FRAME_RATE = 60
private const val CHANNEL_HEADER_LENGTH = 20
private const val CONTROL_MSG_BUFFER_SIZE = 4096