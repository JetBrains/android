/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.annotations.concurrency.GuardedBy
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avcodec.AVCodecParserContext
import org.bytedeco.ffmpeg.avcodec.AVCodecParserContext.PARSER_FLAG_COMPLETE_FRAMES
import org.bytedeco.ffmpeg.avcodec.AVPacket
import org.bytedeco.ffmpeg.avutil.AVDictionary
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.bytedeco.ffmpeg.global.avcodec.AV_CODEC_ID_OPUS
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
import org.bytedeco.ffmpeg.global.avcodec.avcodec_find_decoder
import org.bytedeco.ffmpeg.global.avcodec.avcodec_free_context
import org.bytedeco.ffmpeg.global.avcodec.avcodec_open2
import org.bytedeco.ffmpeg.global.avcodec.avcodec_receive_frame
import org.bytedeco.ffmpeg.global.avcodec.avcodec_send_packet
import org.bytedeco.ffmpeg.global.avutil.AV_CH_LAYOUT_STEREO
import org.bytedeco.ffmpeg.global.avutil.AV_NOPTS_VALUE
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_FLTP
import org.bytedeco.ffmpeg.global.avutil.AV_SAMPLE_FMT_S16
import org.bytedeco.ffmpeg.global.avutil.av_frame_alloc
import org.bytedeco.ffmpeg.global.avutil.av_frame_free
import org.bytedeco.ffmpeg.global.avutil.av_opt_set_int
import org.bytedeco.ffmpeg.global.avutil.av_opt_set_sample_fmt
import org.bytedeco.ffmpeg.global.swresample.swr_alloc
import org.bytedeco.ffmpeg.global.swresample.swr_close
import org.bytedeco.ffmpeg.global.swresample.swr_convert_frame
import org.bytedeco.ffmpeg.global.swresample.swr_init
import org.bytedeco.javacpp.BytePointer
import org.bytedeco.javacpp.IntPointer
import org.bytedeco.javacpp.Pointer
import org.bytedeco.javacpp.Pointer.memcpy
import java.io.EOFException
import java.lang.Long.toHexString
import java.nio.ByteBuffer
import java.nio.ByteOrder.LITTLE_ENDIAN
import java.nio.channels.ClosedChannelException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFormat

internal class AudioDecoder(
  private val audioChannel: SuspendingSocketChannel,
  private val decoderScope: CoroutineScope,
) {

  private val decodingContext = AtomicReference<DecodingContext?>()
  @Volatile private var endOfAudioStream = false

  /**
   * Deactivated audio playback if it is active. Returns true if audio playback was not already active.
   */
  fun mute(): Boolean =
    decodingContext.getAndSet(null)?.closeAsynchronously() != null

  /**
   * Activates audio playback unless it is already active. Returns true if audio playback was not already inactive.
   */
  fun unmute(): Boolean =
      decodingContext.getAndUpdate { context -> context ?: DecodingContext() } == null

  /**
   * Starts reading the audio channel and returns. The decoder will continue to run until the audio channel
   * is disconnected or [decoderScope] is cancelled. The value of the [playAudio] parameter determines whether
   * audio playback will be enabled or not.
   */
  fun start(playAudio: Boolean) {
    if (playAudio) {
      decodingContext.set(DecodingContext())
    }

    decoderScope.launch {
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
      finally {
        endOfAudioStream = true
        decodingContext.getAndSet(null)?.close()
        packetReader.close()
      }
    }
  }

  suspend fun closeChannel() {
    audioChannel.close()
  }

  private inner class PacketReader : AutoCloseable {

    private val headerBuffer: ByteBuffer = AudioPacketHeader.createBuffer()
    private val packet: AVPacket = av_packet_alloc()

    suspend fun readAndProcessPacket() {
      // Each audio packet contains a 12-byte header followed by the packet data.
      audioChannel.readFully(headerBuffer)
      headerBuffer.rewind()
      val header = AudioPacketHeader.deserialize(headerBuffer)
      val packetSize = header.packetSize
      headerBuffer.clear()

      try {
        if (av_new_packet(packet, packetSize) != 0) {
          throw AudioDecoderException("Could not allocate packet of $packetSize bytes")
        }

        audioChannel.readFully(packet.data().asByteBufferOfSize(packetSize))

        packet.pts(if (header.isConfig) AV_NOPTS_VALUE else 0)
        decodingContext.get()?.processPacket(packet)
      }
      catch (e: AudioDecoderException) {
        thisLogger().error(e)
      }
      finally {
        av_packet_unref(packet)
      }
    }

    override fun close() {
      av_packet_free(packet)
    }
  }

  private inner class DecodingContext : AutoCloseable {

    @GuardedBy("this")
    private var codecContext: AVCodecContext
    @GuardedBy("this")
    private val decodingFrame: AVFrame = av_frame_alloc()
    @GuardedBy("this")
    private val outFrame: AVFrame = av_frame_alloc()
    @GuardedBy("this")
    private var parserContext: AVCodecParserContext
    @GuardedBy("this")
    private val pendingPacket: AVPacket = av_packet_alloc()
    @GuardedBy("this")
    private var hasPendingPacket = false
    @GuardedBy("this")
    private val swrContext = swr_alloc()
    @GuardedBy("this")
    private var player: AudioPlayer? = null

    init {
      val codec = avcodec_find_decoder(AV_CODEC_ID_OPUS) ?: throw AudioDecoderException("Opus decoder not found")

      var codecContext: AVCodecContext? = null
      var parserContext: AVCodecParserContext? = null
      try {
        codecContext = avcodec_alloc_context3(codec) ?: throw AudioDecoderException("Could not allocate decoder context")
        //codecContext.request_sample_fmt(AV_SAMPLE_FMT_S16) //TODO: Figure out why it has no effect.
        //codecContext.sample_fmt(AV_SAMPLE_FMT_S16) //TODO: Figure out why it has no effect.
        parserContext = av_parser_init(codec.id())?.apply { flags(flags() or PARSER_FLAG_COMPLETE_FRAMES) } ?:
            throw AudioDecoderException("Could not initialize parser")
        //parserContext.format(AV_SAMPLE_FMT_S16) //TODO: Figure out why it has no effect.
        if (avcodec_open2(codecContext, codec, null as AVDictionary?) < 0) {
          throw AudioDecoderException("Could not open codec ${codec.name()}")
        }
      }
      catch (e: AudioDecoderException) {
        av_parser_close(parserContext)
        avcodec_free_context(codecContext)
        throw e
      }

      this.codecContext = codecContext
      this.parserContext = parserContext

      initializeSwrContext(AV_SAMPLE_FMT_FLTP, 48000)
    }

    private fun initializeSwrContext(inputFormat: Int, sampleRate: Long) {
      av_opt_set_int(swrContext, "in_channel_layout", AV_CH_LAYOUT_STEREO, 0)
      av_opt_set_int(swrContext, "out_channel_layout", AV_CH_LAYOUT_STEREO, 0)
      av_opt_set_int(swrContext, "in_sample_rate", sampleRate, 0)
      av_opt_set_int(swrContext, "out_sample_rate", sampleRate, 0)
      av_opt_set_sample_fmt(swrContext, "in_sample_fmt", inputFormat, 0)
      av_opt_set_sample_fmt(swrContext, "out_sample_fmt", AV_SAMPLE_FMT_S16, 0)
      swr_init(swrContext)
    }

    fun closeAsynchronously() {
      ApplicationManager.getApplication().executeOnPooledThread {
        close()
      }
    }

    @Synchronized
    override fun close() {
      player?.stop()
      swr_close(swrContext)
      av_parser_close(parserContext)
      avcodec_close(codecContext)
      avcodec_free_context(codecContext)
      av_frame_free(decodingFrame)
      av_frame_free(outFrame)
      av_packet_free(pendingPacket)
    }

    @Synchronized
    fun processPacket(packet: AVPacket) { // stream_push_packet
      val isConfig = packet.pts() == AV_NOPTS_VALUE

      var packetToProcess = packet
      // A config packet cannot not be decoded immediately since it contains no frame.
      // It must be combined with the following data packet.
      if (hasPendingPacket || isConfig) {
        val offset: Int
        if (hasPendingPacket) {
          offset = pendingPacket.size()
          if (av_grow_packet(pendingPacket, packet.size()) != 0) {
            throw AudioDecoderException("Could not grow packet")
          }
        }
        else {
          offset = 0
          if (av_new_packet(pendingPacket, packet.size()) != 0) {
            throw AudioDecoderException("Could not create audio packet")
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
          processDataPacket(packetToProcess)
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

    private fun processDataPacket(packet: AVPacket) {
      val outData = BytePointer()
      val outLen = IntPointer(0)
      val ret =
          av_parser_parse2(parserContext, codecContext, outData, outLen, packet.data(), packet.size(), AV_NOPTS_VALUE, AV_NOPTS_VALUE, -1)
      if (outLen.get() > 0) {
        assert(ret == packet.size()) // Due to PARSER_FLAG_COMPLETE_FRAMES.
        assert(outLen.get() == packet.size())
        if (parserContext.key_frame() == 1) {
          packet.flags(packet.flags() or AV_PKT_FLAG_KEY)
        }

        processFrame(packet)
      }
    }

    private fun processFrame(packet: AVPacket) {
      val ret = avcodec_send_packet(codecContext, packet)
      if (ret < 0) {
        throw AudioDecoderException("Audio packet was rejected by the decoder: $ret ${packet.toDebugString()}")
      }

      if (avcodec_receive_frame(codecContext, decodingFrame) != 0) {
        throw AudioDecoderException("Could not receive audio frame")
      }

      outFrame.ch_layout(decodingFrame.ch_layout())
      outFrame.sample_rate(decodingFrame.sample_rate())
      outFrame.format(AV_SAMPLE_FMT_S16)
      val r = swr_convert_frame(swrContext, outFrame, decodingFrame)
      if (r < 0) {
        throw AudioDecoderException("Could not convert audio frame to 16-bit format")
      }

      val numChannels = outFrame.ch_layout().nb_channels()
      val sampleRate = outFrame.sample_rate()
      val bufferSize = outFrame.nb_samples() * BYTES_PER_SAMPLE_FMT_S16 * numChannels
      var player = player
      if (player == null || numChannels != player.numChannels || sampleRate != player.sampleRate || bufferSize != player.bufferSize) {
        player?.stop()
        player = AudioPlayer(numChannels, sampleRate, bufferSize).also { this.player = it }
        player.start()
      }

      val buffer = player.getBuffer()
      outFrame.extended_data(0).get(buffer)
      player.play(buffer)
    }
  }

  private class AudioPlayer(val numChannels: Int, val sampleRate: Int, val bufferSize: Int) : Runnable {
    private val playQueue = ArrayBlockingQueue<ByteArray>(NUM_BUFFERS)
    private val recycleBin = ArrayBlockingQueue<ByteArray>(NUM_BUFFERS)
    @Volatile var stopped = false

    init {
      for (i in 0 until NUM_BUFFERS) {
        recycleBin.put(ByteArray(bufferSize))
      }
    }

    fun start() {
      Thread(this, javaClass.simpleName).start()
    }

    /** Puts an audio buffer in the player's queue. */
    fun play(data: ByteArray) {
      playQueue.put(data)
    }

    fun stop() {
      stopped = true
      playQueue.drainTo(recycleBin)
    }

    fun getBuffer() : ByteArray {
      return recycleBin.take()
    }

    override fun run() {
      val audioFormat = AudioFormat(sampleRate.toFloat(), 16, numChannels, true, false)
      val sourceLine = service<AudioSystemService>().getSourceDataLine(audioFormat)
      sourceLine.open(audioFormat)
      sourceLine.start()

      while (!stopped) {
        val buffer = playQueue.take()
        sourceLine.write(buffer, 0, buffer.size)
        recycleBin.put(buffer)
      }

      sourceLine.flush()
      sourceLine.close()
    }
  }

  private class AudioPacketHeader private constructor(private val encodedPacketSize: Int) {

    val isConfig: Boolean
      get() = encodedPacketSize < 0

    val packetSize: Int
      get() = encodedPacketSize and 0x7FFFFFFF

    override fun toString(): String =
        "AudioPacketHeader(isConfig=$isConfig, packetSize=$packetSize)"

    companion object {

      private const val WIRE_SIZE = 4

      fun deserialize(buffer: ByteBuffer): AudioPacketHeader {
        val encodedPacketSize = buffer.getInt()
        return AudioPacketHeader(encodedPacketSize)
      }

      fun createBuffer(): ByteBuffer =
          ByteBuffer.allocate(WIRE_SIZE).order(LITTLE_ENDIAN)
    }
  }
}

internal class AudioDecoderException(message: String) : RuntimeException(message)

private fun Pointer.asByteBufferOfSize(size: Int): ByteBuffer =
    BytePointer(this).apply { capacity(size.toLong()) }.asByteBuffer()

private fun AVPacket.toDebugString(): String =
    "packet size=${size()}, flags=0x${Integer.toHexString(flags())} pts=0x${toHexString(pts())} dts=${toHexString(dts())}"

private const val NUM_BUFFERS = 2
private const val BYTES_PER_SAMPLE_FMT_S16 = 2