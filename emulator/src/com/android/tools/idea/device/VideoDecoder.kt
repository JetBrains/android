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

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.emulator.coerceAtMost
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.containers.ContainerUtil
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
import org.bytedeco.ffmpeg.global.avcodec.av_parser_init
import org.bytedeco.ffmpeg.global.avcodec.av_parser_parse2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_alloc_context3
import org.bytedeco.ffmpeg.global.avcodec.avcodec_close
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder_by_name
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_PIX_FMT_BGRA
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_frame_get_buffer
import org.bytedeco.ffmpeg.global.avutil.av_frame_make_writable
import org.bytedeco.ffmpeg.global.avutil.av_image_get_buffer_size
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
import java.util.function.Consumer
import kotlin.text.Charsets.UTF_8

internal class VideoDecoder(private val videoChannel: SuspendingSocketChannel, @Volatile var maxOutputSize: Dimension) {

  private val imageLock = Any()
  @GuardedBy("imageLock")
  private var displayFrame: VideoFrame? = null
  private val frameListeners = ContainerUtil.createLockFreeCopyOnWriteList<FrameListener>()

  fun addFrameListener(listener: FrameListener) {
    frameListeners.add(listener)
  }

  fun removeFrameListener(listener: FrameListener) {
    frameListeners.remove(listener)
  }

  @AnyThread
  fun consumeDisplayFrame(consumer: Consumer<VideoFrame>) {
    synchronized(imageLock) {
      displayFrame?.let { consumer.accept(it) }
    }
  }

  /**
   * Starts the decoder and returns. The decoder will continue to run until the video channel
   * is disconnected or [coroutineScope] is cancelled.
   */
  fun start(coroutineScope: CoroutineScope) {
    firstPacketArrival = 0L
    coroutineScope.launch {
      val header = ByteBuffer.allocate(CHANNEL_HEADER_LENGTH)
      videoChannel.readFully(header)
      val codecName = String(header.array(), UTF_8).trim()
      val decodingContext = DecodingContext(codecName)
      try {
        while (true) {
          decodingContext.readAndProcessPacket()
        }
      }
      catch (_: ClosedChannelException) {
      }
      catch (_: EOFException) {
      }
      finally {
        decodingContext.close()
        onEndOfVideoStream()
      }
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

  interface FrameListener {
    fun onNewFrameAvailable()
    fun onEndOfVideoStream()
  }

  internal class VideoFrame(
      val image: BufferedImage,
      val displaySize: Dimension,
      val orientation: Int,
      val orientationCorrection: Int,
      val frameNumber: Int,
      val originationTime: Long)

  private inner class DecodingContext(codecName: String) : AutoCloseable {

    private val codec: AVCodec
    private val codecContext: AVCodecContext
    private val decodingFrame: AVFrame
    private var renderingFrame: AVFrame? = null
    private var swsContext: SwsContext? = null
    private val parserContext: AVCodecParserContext
    private val headerBuffer: ByteBuffer = PacketHeader.createBuffer()
    private val packet: AVPacket = av_packet_alloc()
    private val pendingPacket: AVPacket = av_packet_alloc()
    private var hasPendingPacket = false

    private val renderingSize: Dimension
      get() {
        val videoSize = Dimension(decodingFrame.width(), decodingFrame.height())
        val maximumSize = maxOutputSize
        if (maximumSize.width == 0 || maximumSize.height == 0) {
          return videoSize
        }
        return videoSize.coerceAtMost(maximumSize)
      }

    init {
      thisLogger().debug { "Receiving $codecName video stream" }
      val ffmpegCodecName = if (codecName == "avc") "h264" else codecName
      codec = avcodec_find_decoder_by_name(ffmpegCodecName) ?: throw VideoDecoderException("$ffmpegCodecName decoder not found")
      codecContext = avcodec_alloc_context3(codec) ?: throw VideoDecoderException("Could not allocate decoder context")
      parserContext = av_parser_init(codec.id())?.apply {
        flags(flags() or PARSER_FLAG_COMPLETE_FRAMES)
      } ?: throw VideoDecoderException("Could not initialize parser")

      if (avcodec_open2(codecContext, codec, null as AVDictionary?) < 0) {
        avcodec_free_context(codecContext)
        throw VideoDecoderException("Could not open codec ${codec.name()}")
      }

      decodingFrame = av_frame_alloc()
    }

    suspend fun readAndProcessPacket() {
      // Each video packet contains a 40-byte header followed by the packet data.
      videoChannel.readFully(headerBuffer)
      if (firstPacketArrival == 0L) {
        firstPacketArrival = System.currentTimeMillis()
      }
      headerBuffer.rewind()
      val header = PacketHeader.deserialize(headerBuffer)
      headerBuffer.clear()
      val presentationTimestampUs = header.presentationTimestampUs
      val packetSize = header.packetSize
      if (presentationTimestampUs < 0 || packetSize <= 0) {
        throw VideoDecoderException("Invalid packet header: $headerBuffer")
      }

      try {
        if (av_new_packet(packet, packetSize) != 0) {
          throw VideoDecoderException("Could not allocate packet")
        }

        videoChannel.readFully(packet.data().asByteBufferOfSize(packetSize))

        packet.pts(if (presentationTimestampUs == 0L) AV_NOPTS_VALUE else presentationTimestampUs)
        processPacket(packet, header)
      }
      catch (e: VideoDecoderException) {
        thisLogger().error(e)
      }
      finally {
        av_packet_unref(packet)
      }
    }

    override fun close() {
      avcodec_close(codecContext)
      avcodec_free_context(codecContext)
      av_frame_free(decodingFrame)
      renderingFrame?.let { av_frame_free(it) }
      swsContext?.let { sws_freeContext(it) }
      av_packet_free(packet)
      av_packet_free(pendingPacket)
    }

    private fun processPacket(packet: AVPacket, header: PacketHeader) { // stream_push_packet
      val isConfig = packet.pts() == AV_NOPTS_VALUE

      var packetToProcess = packet
      // A config packet must not be decoded immediately (it contains no frame).
      // Instead, it must be concatenated with the future data packet.
      if (hasPendingPacket || isConfig) {
        val offset: Int
        if (hasPendingPacket) {
          offset = pendingPacket.size()
          if (av_grow_packet(pendingPacket, packet.size()) != 0) {
            throw VideoDecoderException("Could not grow packet")
          }
        } else {
          offset = 0
          if (av_new_packet(pendingPacket, packet.size()) != 0) {
            throw VideoDecoderException("Could not create packet")
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
        try {
          processDataPacket(packetToProcess, header)
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

    private fun processDataPacket(packet: AVPacket, header: PacketHeader) {
      val outData = BytePointer()
      val outLen = IntPointer(0)
      val r = av_parser_parse2(parserContext, codecContext, outData, outLen, packet.data(), packet.size(), AV_NOPTS_VALUE, AV_NOPTS_VALUE, -1)
      assert(r == packet.size()) // Due to PARSER_FLAG_COMPLETE_FRAMES.
      assert(outLen.get() == packet.size())
      if (parserContext.key_frame() == 1) {
        packet.flags(packet.flags() or AV_PKT_FLAG_KEY)
      }

      processFrame(packet, header)
    }

    private fun processFrame(packet: AVPacket, header: PacketHeader) {
      val ret = avcodec_send_packet(codecContext, packet)
      if (ret < 0) {
        throw VideoDecoderException("Video packet was rejected by the decoder: $ret")
      }

      if (avcodec_receive_frame(codecContext, decodingFrame) != 0) {
        throw VideoDecoderException("Could not receive video frame")
      }

      val size = renderingSize
      var renderingFrame = renderingFrame
      if (renderingFrame == null || renderingFrame.width() != size.width || renderingFrame.height() != size.height) {
        renderingFrame?.let { av_frame_free(it) }
        renderingFrame = createRenderingFrame(size).also { this.renderingFrame = it }
        if (av_frame_get_buffer(renderingFrame, 4) < 0) {
          throw RuntimeException("av_frame_get_buffer failed")
        }
      }
      if (av_frame_make_writable(renderingFrame) < 0) {
        throw RuntimeException("av_frame_make_writable failed")
      }

      sws_scale(getSwsContext(renderingFrame), decodingFrame.data(), decodingFrame.linesize(), 0, decodingFrame.height(),
                renderingFrame.data(), renderingFrame.linesize())

      val numBytes = av_image_get_buffer_size(renderingFrame.format(), renderingFrame.width(), renderingFrame.height(), 1)
      val framePixels = renderingFrame.data().get().asByteBufferOfSize(numBytes).asIntBuffer()
      val imageSize = Dimension(renderingFrame.width(), renderingFrame.height())

      synchronized(imageLock) {
        var image = displayFrame?.image
        if (image?.width == imageSize.width && image.height == imageSize.height) {
          val imagePixels = (image.raster.dataBuffer as DataBufferInt).data
          framePixels.get(imagePixels)
        }
        else {
          val imagePixels = IntArray(imageSize.width * imageSize.height)
          framePixels.get(imagePixels)
          val buffer = DataBufferInt(imagePixels, imagePixels.size)
          val sampleModel = SinglePixelPackedSampleModel(DataBuffer.TYPE_INT, imageSize.width, imageSize.height, SAMPLE_MODEL_BIT_MASKS)
          val raster = Raster.createWritableRaster(sampleModel, buffer, ZERO_POINT)
          image = BufferedImage(COLOR_MODEL, raster, false, null)
        }

        displayFrame = VideoFrame(image, header.displaySize, header.displayOrientation, header.displayOrientationCorrection,
                                  header.frameNumber.toInt(), header.originationTimestampUs / 1000)
      }

      onNewFrameAvailable()
    }

    private fun getSwsContext(renderingFrame: AVFrame): SwsContext {
      val context = sws_getCachedContext(swsContext, decodingFrame.width(), decodingFrame.height(), decodingFrame.format(),
                                         renderingFrame.width(), renderingFrame.height(), renderingFrame.format(),
                                         SWS_BILINEAR, null, null, null as DoublePointer?) ?:
             throw VideoDecoderException("Could not allocate SwsContext")
      swsContext = context
      return context
    }

    private fun createRenderingFrame(size: Dimension): AVFrame {
      return av_frame_alloc().apply {
        width(size.width)
        height(size.height)
        format(AV_PIX_FMT_BGRA)
      }
    }
  }

  private class PacketHeader private constructor(
    val displaySize: Dimension,
    val displayOrientation: Int,
    /** The difference between [displayOrientation] and the orientation according to the DisplayInfo Android data structure. */
    val displayOrientationCorrection: Int,
    val packetSize: Int,
    val frameNumber: Long,
    val originationTimestampUs: Long,
    val presentationTimestampUs: Long
  ) {

    companion object {
      @Suppress("UsePropertyAccessSyntax")
      fun deserialize(buffer: ByteBuffer): PacketHeader {
        val width = buffer.getInt()
        val height = buffer.getInt()
        val displayOrientation = buffer.getShort().toInt()
        val displayOrientationCorrection = buffer.getShort().toInt()
        val packetSize = buffer.getInt()
        val frameNumber = buffer.getLong()
        val originationTimestampUs = buffer.getLong()
        val presentationTimestampUs = buffer.getLong()
        return PacketHeader(Dimension(width, height), displayOrientation, displayOrientationCorrection, packetSize, frameNumber,
                            originationTimestampUs, presentationTimestampUs)
      }

      fun createBuffer(): ByteBuffer =
        ByteBuffer.allocate(WIRE_SIZE).order(LITTLE_ENDIAN)

      private const val WIRE_SIZE = 4 + 4 + 4 + 4 + 8 + 8 + 8
    }
  }
}

internal class VideoDecoderException(message: String) : RuntimeException(message)

private fun Pointer.asByteBufferOfSize(size: Int): ByteBuffer =
  BytePointer(this).apply { capacity(size.toLong()) }.asByteBuffer()

private fun AVPacket.toDebugString(): String =
  "packet size=${size()}, flags=0x${Integer.toHexString(flags())} pts=0x${toHexString(pts())} dts=${toHexString(dts())}"

private const val CHANNEL_HEADER_LENGTH = 20

private val ZERO_POINT = Point()
private const val ALPHA_MASK = 0xFF shl 24
private val SAMPLE_MODEL_BIT_MASKS = intArrayOf(0xFF0000, 0xFF00, 0xFF, ALPHA_MASK)
private val COLOR_MODEL = DirectColorModel(ColorSpace.getInstance(ColorSpace.CS_sRGB),
                                           32, 0xFF0000, 0xFF00, 0xFF, ALPHA_MASK, false, DataBuffer.TYPE_INT)

internal var firstPacketArrival = 0L