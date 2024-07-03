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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.sdklib.deviceprovisioner.DeviceProperties
import com.android.tools.adtui.ImageUtils
import com.android.tools.adtui.ImageUtils.ellipticalClip
import com.android.tools.idea.streaming.core.PRIMARY_DISPLAY_ID
import com.android.tools.idea.streaming.core.getUInt
import com.android.tools.idea.streaming.core.rotatedByQuadrants
import com.android.tools.idea.streaming.core.scaled
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.text.StringUtil.toHexString
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.io.toByteArray
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodec
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVCodecParserContext
import org.bytedeco.ffmpeg.avcodec.AVCodecParserContext.PARSER_FLAG_COMPLETE_FRAMES
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_PKT_FLAG_KEY
import org.bytedeco.ffmpeg.global.avcodec.av_grow_packet
import org.bytedeco.ffmpeg.global.avcodec.av_new_packet
import org.bytedeco.ffmpeg.global.avcodec.av_packet_alloc
import org.bytedeco.ffmpeg.global.avcodec.av_packet_free
import org.bytedeco.ffmpeg.global.avcodec.av_packet_unref
import org.bytedeco.ffmpeg.global.avcodec.av_parser_close
import org.bytedeco.ffmpeg.global.avcodec.av_parser_init
import org.bytedeco.ffmpeg.global.avcodec.av_parser_parse2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_close
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder_by_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avutil.AV_LOG_QUIET
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable
import org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size
import org.bytedeco.ffmpeg.global.avutil.av_log_set_level
import org.bytedeco.ffmpeg.global.swscale.SWS_BILINEAR
import org.bytedeco.ffmpeg.global.swscale.sws_freeContext
import org.bytedeco.ffmpeg.global.swscale.sws_getCachedContext
import org.bytedeco.ffmpeg.global.swscale.sws_scale
import org.bytedeco.ffmpeg.swscale.SwsContext
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.DoublePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.Pointer.memcpy
import org.jetbrains.annotations.VisibleForTesting
import java.awt.Dimension
import java.awt.Point
import java.awt.color.ColorSpace
import java.awt.image.BufferedImage
import java.awt.image.DataBuffer
import java.awt.image.DataBufferInt
import java.awt.image.DirectColorModel
import java.awt.image.Raster
import java.awt.image.SinglePixelPackedSampleModel
import java.io.EOFException
import java.lang.Long.toHexString
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import kotlin.text.Charsets.UTF_8

internal class VideoDecoder(
  private val videoChannel: SuspendingSocketChannel,
  private val decoderScope: CoroutineScope,
  private val deviceProperties: DeviceProperties,
  private val streamingSessionTracker: DeviceStreamingSessionTracker,
) {

  private val decodingContexts = ConcurrentHashMap<Int, DecodingContext>()
  private val codec = CompletableDeferred<AVCodec>()
  @Volatile
  private var endOfVideoStream = false
  private val logger
    get() = thisLogger()

  /**
   * Enables video decoding for the given display unless it is already active.
   * Returns true if video decoding was enabled after being inactive.
   */
  fun enableDecodingForDisplay(displayId: Int): Boolean {
    var decodingContextAdded = false
    decodingContexts.computeIfAbsent(displayId) {
      decodingContextAdded = true
      DecodingContext(PRIMARY_DISPLAY_ID)
    }
    if (decodingContextAdded && endOfVideoStream) {
      disableDecodingForDisplay(displayId)
      decodingContextAdded = false
    }
    return decodingContextAdded
  }

  /**
   * Disables video decoding for the given display if it is active.
   * Returns true if video decoding was disabled after being active.
   */
  fun disableDecodingForDisplay(displayId: Int): Boolean =
      decodingContexts.remove(displayId)?.closeAsynchronously() != null

  fun addFrameListener(displayId: Int, listener: FrameListener) {
    if (!endOfVideoStream) {
      decodingContexts[displayId]?.addFrameListener(listener) ?: throw IllegalStateException("Not processing video from display $displayId")
    }
  }

  fun removeFrameListener(displayId: Int, listener: FrameListener) {
    decodingContexts[displayId]?.removeFrameListener(listener)
  }

  @AnyThread
  fun consumeDisplayFrame(displayId: Int, consumer: Consumer<VideoFrame>) {
    decodingContexts[displayId]?.consumeDisplayFrame(consumer)
  }

  /**
   * Starts reading the video channel and returns. The decoder will continue to run until the video channel
   * is disconnected or [decoderScope] is cancelled. If the [enableDecodingForPrimaryDisplay] parameter
   * is true, decoding is enabled for primary display.
   */
  fun start(enableDecodingForPrimaryDisplay: Boolean) {
    if (enableDecodingForPrimaryDisplay) {
      decodingContexts[PRIMARY_DISPLAY_ID] = DecodingContext(PRIMARY_DISPLAY_ID)
    }

    decoderScope.launch {
      readChannelHeaderAndInitializeCodec()
      val packetReader = PacketReader()
      try {
        while (true) {
          packetReader.readAndProcessPacket()
        }
      }
      catch (_: ClosedChannelException) {
      }
      catch (_: EOFException) {
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Throwable) {
        logger.error(e)
      }
      finally {
        endOfVideoStream = true
        for (decodingContext in decodingContexts.values) {
          decodingContext.close()
        }
        decodingContexts.clear()
        packetReader.close()
      }
    }
  }

  suspend fun closeChannel() {
    videoChannel.close()
  }

  private suspend fun readChannelHeaderAndInitializeCodec() {
    val header = ByteBuffer.allocate(CHANNEL_HEADER_LENGTH)
    videoChannel.readFully(header)
    val codecName = String(header.array(), UTF_8).trim()
    logger.debug { "Receiving $codecName video stream" }
    val ffmpegCodecName = when (codecName) {
      "av01" -> "av1"
      "avc" -> "h264"
      else -> codecName
    }
    codec.complete(avcodec_find_decoder_by_name(ffmpegCodecName) ?: throw VideoDecoderException("$ffmpegCodecName decoder not found"))
  }

  interface FrameListener {
    fun onNewFrameAvailable()
    fun onEndOfVideoStream()
    /** Called when the decoder rejected a video frame. */
    fun onInvalidFrame(e: InvalidFrameException)
  }

  internal class VideoFrame(
      val image: BufferedImage,
      val displaySize: Dimension,
      val orientation: Int,
      val orientationCorrection: Int,
      val round: Boolean,
      val frameNumber: UInt,
      val originationTime: Long)

  private inner class PacketReader : AutoCloseable {

    private val headerBuffer: ByteBuffer = VideoPacketHeader.createBuffer()
    private val packet: AVPacket = av_packet_alloc()

    suspend fun readAndProcessPacket() {
      // Each video packet contains a 44-byte header followed by the packet data.
      videoChannel.readFully(headerBuffer)
      headerBuffer.rewind()
      val header = VideoPacketHeader.deserialize(headerBuffer)
      val presentationTimestampUs = header.presentationTimestampUs
      val packetSize = header.packetSize
      if (presentationTimestampUs < 0 || packetSize <= 0) {
        throw VideoDecoderException("Invalid packet header: ${toHexString(headerBuffer.rewind().toByteArray())}")
      }
      headerBuffer.clear()

      try {
        if (av_new_packet(packet, packetSize) != 0) {
          throw VideoDecoderException("Display ${header.displayId}: could not allocate packet of $packetSize bytes")
        }

        videoChannel.readFully(packet.data().asByteBufferOfSize(packetSize))

        packet.pts(if (presentationTimestampUs == 0L) AV_NOPTS_VALUE else presentationTimestampUs)
        decodingContexts[header.displayId]?.processPacket(packet, header)
      }
      finally {
        av_packet_unref(packet)
      }
    }

    override fun close() {
      av_packet_free(packet)
    }
  }

  private inner class DecodingContext(val displayId: Int) : AutoCloseable {

    @GuardedBy("imageLock") var displayFrame: VideoFrame? = null
      private set
    private val imageLock = Any()
    @GuardedBy("this") private lateinit var codecContext: AVCodecContext
    @GuardedBy("this") private lateinit var decodingFrame: AVFrame
    @GuardedBy("this") private var renderingFrame: AVFrame? = null
    @GuardedBy("this") private var swsContext: SwsContext? = null
    @GuardedBy("this") private lateinit var parserContext: AVCodecParserContext
    @GuardedBy("this") private val pendingPacket: AVPacket = av_packet_alloc()
    @GuardedBy("this") private var hasPendingPacket = false
    @GuardedBy("this") private var framesAtBitRate: Int = 0 // Used for primary display only.
    @GuardedBy("this") private var initialized: Boolean? = false // Set to null by the close method.
    private val frameListeners = ContainerUtil.createLockFreeCopyOnWriteList<FrameListener>()

    init {
      // Prevent avcodec_send_packet from returning -1094995529.
      av_log_set_level(AV_LOG_QUIET) // Suggested in https://github.com/mpromonet/webrtc-streamer/issues/89.

      decoderScope.launch {
        ensureInitialized(codec.await())
      }
    }

    @Synchronized
    private fun ensureInitialized(codec: AVCodec): Boolean {
      when (initialized) {
        true -> return true
        null -> return false
        else -> {}
      }
      var codecContext: AVCodecContext? = null
      var parserContext: AVCodecParserContext? = null
      try {
        codecContext = avcodec_alloc_context3(codec) ?:
            throw VideoDecoderException("Display $displayId: could not allocate decoder context")
        parserContext = av_parser_init(codec.id())?.apply {
          flags(flags() or PARSER_FLAG_COMPLETE_FRAMES)
        } ?: throw VideoDecoderException("Display $displayId: could not initialize parser")
        if (avcodec_open2(codecContext, codec, null as AVDictionary?) < 0) {
          throw VideoDecoderException("Display $displayId: could not open codec ${codec.name()}")
        }
      }
      catch (e: VideoDecoderException) {
        av_parser_close(parserContext)
        avcodec_free_context(codecContext)
        throw e
      }

      this.codecContext = codecContext
      this.parserContext = parserContext
      decodingFrame = av_frame_alloc()
      initialized = true
      return true
    }

    fun addFrameListener(listener: FrameListener) {
      frameListeners.add(listener)
    }

    fun removeFrameListener(listener: FrameListener) {
      frameListeners.remove(listener)
    }

    fun consumeDisplayFrame(consumer: Consumer<VideoFrame>) {
      synchronized(imageLock) {
        displayFrame?.let { consumer.accept(it) }
      }
    }

    fun closeAsynchronously() {
      ApplicationManager.getApplication().executeOnPooledThread {
        close()
      }
    }

    @Synchronized
    override fun close() {
      onEndOfVideoStream()
      if (initialized == true) {
        av_parser_close(parserContext)
        avcodec_close(codecContext)
        avcodec_free_context(codecContext)
        av_frame_free(decodingFrame)
        renderingFrame?.let { av_frame_free(it) }
        swsContext?.let { sws_freeContext(it) }
        av_packet_free(pendingPacket)
      }
      initialized = null
    }

    @Synchronized
    fun processPacket(packet: AVPacket, header: VideoPacketHeader) { // stream_push_packet
      @Suppress("OPT_IN_USAGE")
      if (!ensureInitialized(codec.getCompleted())) {
        return
      }

      val isConfig = packet.pts() == AV_NOPTS_VALUE

      var packetToProcess = packet
      // A config packet cannot not be decoded immediately since it contains no frame.
      // It must be combined with the following data packet.
      if (hasPendingPacket || isConfig) {
        val offset: Int
        if (hasPendingPacket) {
          offset = pendingPacket.size()
          if (av_grow_packet(pendingPacket, packet.size()) != 0) {
            throw VideoDecoderException("Display $displayId: could not grow packet")
          }
        } else {
          offset = 0
          if (av_new_packet(pendingPacket, packet.size()) != 0) {
            throw VideoDecoderException("Display $displayId: could not create packet for display $displayId")
          }
          hasPendingPacket = true
        }

        memcpy(pendingPacket.data().position(offset.toLong()), packet.data(), packet.size().toLong())

        if (!isConfig) {
          // Prepare the concatenated packet to send to the decoder.
          pendingPacket.pts(packet.pts())
          pendingPacket.dts(packet.dts())
          pendingPacket.flags(packet.flags())
          packetToProcess = pendingPacket
        }
      }

      if (!isConfig) {
        // Data packet.
        if (displayId == PRIMARY_DISPLAY_ID) {
          streamingSessionTracker.videoFrameArrived()
        }

        try {
          processDataPacket(packetToProcess, header)
        }
        catch (e: InvalidFrameException) {
          onInvalidFrame(e)
        }
        finally {
          if (hasPendingPacket) {
            // The pending packet must be discarded.
            hasPendingPacket = false
            if (pendingPacket != packet) {
              av_packet_unref(pendingPacket)
            }
          }
        }
      }
    }

    private fun processDataPacket(packet: AVPacket, header: VideoPacketHeader) {
      val outData = BytePointer()
      val outLen = IntPointer(0)
      val ret =
          av_parser_parse2(parserContext, codecContext, outData, outLen, packet.data(), packet.size(), AV_NOPTS_VALUE, AV_NOPTS_VALUE, -1)
      assert(ret == packet.size()) // Due to PARSER_FLAG_COMPLETE_FRAMES.
      assert(outLen.get() == packet.size())
      if (parserContext.key_frame() == 1) {
        packet.flags(packet.flags() or AV_PKT_FLAG_KEY)
      }

      processFrame(packet, header)
    }

    private fun processFrame(packet: AVPacket, header: VideoPacketHeader) {
      val ret = avcodec_send_packet(codecContext, packet)
      if (ret < 0) {
        throw InvalidFrameException(
            "Display $displayId: video packet was rejected by the decoder: $ret ${packet.toDebugString()} header: $header")
      }

      if (avcodec_receive_frame(codecContext, decodingFrame) != 0) {
        throw VideoDecoderException("Display $displayId: could not receive video frame")
      }

      val frameWidth = decodingFrame.width()
      val frameHeight = decodingFrame.height()
      var renderingFrame = renderingFrame
      if (renderingFrame == null || renderingFrame.width() != frameWidth || renderingFrame.height() != frameHeight) {
        renderingFrame?.let { av_frame_free(it) }
        renderingFrame = createRenderingFrame(frameWidth, frameHeight).also { this.renderingFrame = it }
        if (av_frame_get_buffer(renderingFrame, 4) < 0) {
          throw RuntimeException("av_frame_get_buffer failed")
        }
      }
      if (av_frame_make_writable(renderingFrame) < 0) {
        throw RuntimeException("av_frame_make_writable failed")
      }

      sws_scale(getSwsContext(renderingFrame), decodingFrame.data(), decodingFrame.linesize(), 0, frameHeight,
                renderingFrame.data(), renderingFrame.linesize())

      val numBytes = av_image_get_buffer_size(renderingFrame.format(), frameWidth, frameHeight, 1)
      val framePixels = renderingFrame.data().get().asByteBufferOfSize(numBytes).asIntBuffer()
      // Due to video size alignment requirements, the video frame may contain black strips at the top and at the bottom.
      // These black strips have to be excluded from the rendered image.
      val rotatedDisplaySize = header.displaySize.rotatedByQuadrants(header.displayOrientation - header.displayOrientationCorrection)
      val imageHeight = frameWidth.scaled(rotatedDisplaySize.height.toDouble() / rotatedDisplaySize.width).coerceAtMost(frameHeight)
      val startY = (frameHeight - imageHeight) / 2
      framePixels.position(startY * frameWidth) // Skip the potential black strip at the top of the frame.

      val displayIsRound = header.isDisplayRound && header.displaySize.width == header.displaySize.height
      synchronized(imageLock) {
        var image = displayFrame?.image
        if (image?.width == frameWidth && image.height == imageHeight &&
            displayFrame?.orientationCorrection == 0 && header.displayOrientationCorrection == 0 && !displayIsRound) {
          val imagePixels = (image.raster.dataBuffer as DataBufferInt).data
          framePixels.get(imagePixels, 0, imageHeight * frameWidth)
        }
        else {
          val imagePixels = IntArray(frameWidth * imageHeight)
          framePixels.get(imagePixels, 0, imageHeight * frameWidth)
          val buffer = DataBufferInt(imagePixels, imagePixels.size)
          val sampleModel = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, frameWidth, imageHeight, SAMPLE_MODEL_BIT_MASKS)
          val raster = Raster.createWritableRaster(sampleModel, buffer, ZERO_POINT)
          image = ImageUtils.rotateByQuadrants(BufferedImage(COLOR_MODEL, raster, false, null), header.displayOrientationCorrection)
          if (displayIsRound) {
            image = ellipticalClip(image, null)
          }
        }

        displayFrame = VideoFrame(image, header.displaySize, header.displayOrientation, header.displayOrientationCorrection,
                                  displayIsRound, header.frameNumber, header.originationTimestampUs / 1000)
      }

      onNewFrameAvailable()

      if (displayId == PRIMARY_DISPLAY_ID && deviceProperties.isVirtual == false) {
        if (header.isBitRateReduced) {
          BitRateManager.getInstance().bitRateReduced(header.bitRate, deviceProperties)
          framesAtBitRate = 1
          logger.info("${deviceProperties.title} bit rate: ${header.bitRate}")
        }
        else {
          if (++framesAtBitRate % BIT_RATE_STABILITY_FRAME_COUNT == 0) {
            BitRateManager.getInstance().bitRateStable(header.bitRate, deviceProperties)
          }
        }
      }
    }

    private fun getSwsContext(renderingFrame: AVFrame): SwsContext {
      val context = sws_getCachedContext(swsContext, decodingFrame.width(), decodingFrame.height(), decodingFrame.format(),
                                         renderingFrame.width(), renderingFrame.height(), renderingFrame.format(),
                                         SWS_BILINEAR, null, null, null as DoublePointer?) ?:
          throw VideoDecoderException("Display $displayId: could not allocate SwsContext")
      swsContext = context
      return context
    }

    private fun createRenderingFrame(width: Int, height: Int): AVFrame {
      return av_frame_alloc().apply {
        width(width)
        height(height)
        format(AV_PIX_FMT_BGRA)
      }
    }

    private fun onNewFrameAvailable() {
      for (listener in frameListeners) {
        listener.onNewFrameAvailable()
      }
    }

    private fun onEndOfVideoStream() {
      for (listener in frameListeners) {
        listener.onEndOfVideoStream()
      }
    }

    private fun onInvalidFrame(e: InvalidFrameException) {
      for (listener in frameListeners) {
        listener.onInvalidFrame(e)
      }
    }
  }

  private class VideoPacketHeader private constructor(
    val displayId: Int,
    val displaySize: Dimension,
    val displayOrientation: Int,
    /** The difference between [displayOrientation] and the orientation according to the DisplayInfo Android data structure. */
    val displayOrientationCorrection: Int,
    private val flags: Int,
    val bitRate: Int,
    val frameNumber: UInt,
    val originationTimestampUs: Long,
    val presentationTimestampUs: Long,
    val packetSize: Int,
  ) {

    val isDisplayRound: Boolean
      get() = (flags and FLAG_DISPLAY_ROUND) != 0
    val isBitRateReduced: Boolean
      get() = (flags and FLAG_BIT_RATE_REDUCED) != 0

    companion object {
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

      fun deserialize(buffer: ByteBuffer): VideoPacketHeader {
        val displayId = buffer.getInt()
        val width = buffer.getInt()
        val height = buffer.getInt()
        val displayOrientation = buffer.get().toInt()
        val displayOrientationCorrection = buffer.get().toInt()
        val flags = buffer.getShort().toInt()
        val bitRate = buffer.getInt()
        val frameNumber = buffer.getUInt()
        val originationTimestampUs = buffer.getLong()
        val presentationTimestampUs = buffer.getLong()
        val packetSize = buffer.getInt()
        return VideoPacketHeader(displayId, Dimension(width, height), displayOrientation, displayOrientationCorrection, flags,
                                 bitRate, frameNumber, originationTimestampUs, presentationTimestampUs, packetSize)
      }

      fun createBuffer(): ByteBuffer =
          ByteBuffer.allocate(WIRE_SIZE).order(LITTLE_ENDIAN)
    }

    override fun toString(): String {
      return "PacketHeader(" +
             "displayId=$displayId, " +
             "displaySize=$displaySize, " +
             "displayOrientation=$displayOrientation, " +
             "displayOrientationCorrection=$displayOrientationCorrection, " +
             "flags=$flags, " +
             "frameNumber=$frameNumber, " +
             "packetSize=$packetSize)"
    }
  }
}

internal open class VideoDecoderException(message: String) : RuntimeException(message)
internal class InvalidFrameException(message: String) : VideoDecoderException(message)

private fun Pointer.asByteBufferOfSize(size: Int): ByteBuffer =
  BytePointer(this).apply { capacity(size.toLong()) }.asByteBuffer()

private fun AVPacket.toDebugString(): String =
  "packet size=${size()}, flags=0x${Integer.toHexString(flags())} pts=0x${toHexString(pts())} dts=${toHexString(dts())}"

private const val CHANNEL_HEADER_LENGTH = 20
/** Number of frames to be received before considering bit rate to be stable. */
@VisibleForTesting // Visible and mutable for testing.
internal var BIT_RATE_STABILITY_FRAME_COUNT = 1000

private val ZERO_POINT = Point()
private const val ALPHA_MASK = 0xFF shl 24
private val SAMPLE_MODEL_BIT_MASKS = intArrayOf(0xFF0000, 0xFF00, 0xFF, ALPHA_MASK)
private val COLOR_MODEL = DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                           32, 0xFF0000, 0xFF00, 0xFF, ALPHA_MASK, false, DataBuffer.TYPE_INT)
