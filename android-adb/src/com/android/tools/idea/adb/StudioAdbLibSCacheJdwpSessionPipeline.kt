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
package com.android.tools.idea.adb

import com.android.adblib.AdbSession
import com.android.adblib.ConnectedDevice
import com.android.adblib.adbLogger
import com.android.adblib.tools.debugging.JdwpSessionPipeline
import com.android.adblib.tools.debugging.SharedJdwpSessionMonitor
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.writeToBuffer
import com.android.adblib.tools.debugging.sendPacket
import com.android.adblib.tools.debugging.utils.SynchronizedChannel
import com.android.adblib.tools.debugging.utils.SynchronizedReceiveChannel
import com.android.adblib.tools.debugging.utils.SynchronizedSendChannel
import com.android.adblib.tools.debugging.utils.logIOCompletionErrors
import com.android.adblib.tools.debugging.utils.receiveAll
import com.android.adblib.utils.ResizableBuffer
import com.android.adblib.withPrefix
import com.android.jdwpscache.SCacheResponse
import com.android.jdwpscache.SuspendingSCache
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

internal class StudioAdbLibSCacheJdwpSessionPipeline(
  private val device: ConnectedDevice,
  pid: Int,
  scacheLogger: StudioSCacheLogger,
  private val sessionMonitor: SharedJdwpSessionMonitor?,
  private val debuggerPipeline: JdwpSessionPipeline,
) : JdwpSessionPipeline {

  private val logger = adbLogger(session).withPrefix("$session - $device - pid=$pid - ")

  private val session: AdbSession
    get() = device.session

  /**
   * The actual SCache implementation.
   *
   * Note: In [SuspendingSCache] terminology,
   * * "upstream" is equivalent to `debuggee`
   * * "downstream" is equivalent to `debugger`
   */
  private val scache = SuspendingSCache(true, scacheLogger)

  private val sendChannelImpl = SynchronizedChannel<JdwpPacketView>()

  private val receiveChannelImpl = SynchronizedChannel<JdwpPacketView>()

  override val scope: CoroutineScope
    get() = debuggerPipeline.scope

  override val sendChannel: SynchronizedSendChannel<JdwpPacketView>
    get() = sendChannelImpl

  override val receiveChannel: SynchronizedReceiveChannel<JdwpPacketView>
    get() = receiveChannelImpl

  init {
    val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
      logger.logIOCompletionErrors(throwable)
    }

    // Note: We use a custom exception handler because we handle exceptions, and we don't
    // want them to go to the parent scope handler as "unhandled" exceptions in a `launch` job.
    // Note: We cancel both channels on completion so that we never leave one of the two
    // coroutine running if the other completed.
    scope.launch(session.ioDispatcher + exceptionHandler) {
      forwardSendChannelToDebugger()
    }.invokeOnCompletion {
      cancelChannels(it)
    }

    scope.launch(session.ioDispatcher + exceptionHandler) {
      forwardDebuggerToReceiveChannel()
    }.invokeOnCompletion {
      cancelChannels(it)
    }
  }

  private fun cancelChannels(throwable: Throwable?) {
    logger.debug { "Scope completion: closing SCache and monitor" }

    scache.close()
    sessionMonitor?.close()

    // Ensure exception is propagated to channels so that
    // 1) callers (i.e. consumer of 'send' and 'receive' channels) get notified of errors
    // 2) both forwarding coroutines always complete together
    val cancellationException = (throwable as? CancellationException)
                                ?: CancellationException("SCache Debugger pipeline for JDWP session has completed", throwable)
    sendChannelImpl.cancel(cancellationException)
    receiveChannelImpl.cancel(cancellationException)
  }

  override fun toString(): String {
    return "${this::class.simpleName} -> $debuggerPipeline"
  }

  private suspend fun forwardSendChannelToDebugger() {
    logger.debug { "Forwarding packets from 'send' channel to scache" }
    val workBuffer = ResizableBuffer()
    // Note: 'receiveAll' is a terminal operator, throws exception when completed or cancelled
    sendChannelImpl.receiveAll { packet ->
      logger.verbose { "Sending packet to debugger: $packet" }
      val buffer = packet.writeToBuffer(workBuffer)
      val response = scache.onDownstreamPacket(buffer)
      processSCacheResponse(response)
    }
  }

  private suspend fun forwardDebuggerToReceiveChannel() {
    logger.debug { "Forwarding packets from debugger pipeline to scache" }
    val workBuffer = ResizableBuffer()
    // Note: Throws an EOFException exception when channel is closed
    debuggerPipeline.receiveChannel.receiveAll { packet ->
      logger.verbose { "Sending packet to channel: $packet" }
      val buffer = packet.writeToBuffer(workBuffer)
      val response = scache.onUpstreamPacket(buffer)
      processSCacheResponse(response)
    }
  }

  private suspend fun processSCacheResponse(response: SCacheResponse) {
    logger.verbose { "Processing SCache response: " +
                     "edict.toUpstream size=${response.edict.toUpstream.size}, " +
                     "edict.toDownstream size=${response.edict.toDownstream.size}, " +
                     "journal.toUpstream size=${response.journal.toUpstream.size}, " +
                     "journal.toDownstream size=${response.journal.toUpstream.size}" }

    // Packets from the debuggee (device) to the debugger
    response.edict.toDownstream
      .map { it.duplicate() }
      .forEach { packetBuffer ->
        val packet = JdwpPacketView.wrapByteBuffer(packetBuffer)
        logger.verbose { "Sending packet from SCache to debugger endpoint '$debuggerPipeline': $packet" }
        debuggerPipeline.sendPacket(packet)
      }

    // Packets from the debugger to the debuggee (device)
    response.edict.toUpstream
      .map { it.duplicate() }
      .forEach { packetBuffer ->
        val packet = JdwpPacketView.wrapByteBuffer(packetBuffer)
        logger.verbose { "Emitting packet from SCache to receive flow: $packet" }
        receiveChannelImpl.sendPacket(packet)
     }

    // Forward journaling packets to the session monitor
    sessionMonitor?.also { monitor ->
      response.journal.toUpstream
        .map { it.duplicate() }
        .forEach { packetBuffer ->
          val sendPacket = JdwpPacketView.wrapByteBuffer(packetBuffer)
          logger.verbose { "monitor.onSendPacket: $sendPacket" }
          monitor.onSendPacket(sendPacket)
        }
      response.journal.toDownstream
        .map { it.duplicate() }
        .forEach { packetBuffer ->
          val receivePacket = JdwpPacketView.wrapByteBuffer(packetBuffer)
          logger.verbose { "monitor.onReceivePacket: $receivePacket" }
          monitor.onReceivePacket(receivePacket)
        }
    }
  }
}
