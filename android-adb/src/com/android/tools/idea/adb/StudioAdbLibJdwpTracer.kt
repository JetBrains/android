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
package com.android.tools.idea.adb

import com.android.adblib.AdbSession
import com.android.adblib.CoroutineScopeCache
import com.android.adblib.tools.debugging.SharedJdwpSession
import com.android.adblib.tools.debugging.SharedJdwpSessionMonitor
import com.android.adblib.tools.debugging.SharedJdwpSessionMonitorFactory
import com.android.adblib.tools.debugging.addSharedJdwpSessionMonitorFactory
import com.android.adblib.tools.debugging.packets.JdwpPacketView
import com.android.adblib.tools.debugging.packets.appendJdwpPacket
import com.android.adblib.utils.ResizableBuffer
import com.android.jdwptracer.JDWPTracer
import com.android.tools.idea.flags.StudioFlags
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class StudioAdbLibJdwpTracerFactory : SharedJdwpSessionMonitorFactory {
  private var enabled: () -> Boolean = { true }

  override fun create(session: SharedJdwpSession): SharedJdwpSessionMonitor? {
    return if (enabled()) {
      StudioAdbLibJdwpTracer()
    } else {
      null
    }
  }

  companion object {
    private val key = CoroutineScopeCache.Key<StudioAdbLibJdwpTracerFactory>("StudioAdbLibJdwpTracerFactory session cache key")

    @JvmStatic
    fun install(session: AdbSession, enabled: () -> Boolean) {
      val factory = session.cache.getOrPut(key) {
        StudioAdbLibJdwpTracerFactory().also {
          session.addSharedJdwpSessionMonitorFactory(it)
        }
      }
      factory.enabled = enabled
    }
  }
}

class StudioAdbLibJdwpTracer : SharedJdwpSessionMonitor {
  private val tracer = JDWPTracer(StudioFlags.JDWP_TRACER.get())
  private val sendMutex = Mutex()
  private val sendBuffer = ResizableBuffer()
  private val receiveMutex = Mutex()
  private val receiveBuffer = ResizableBuffer()

  override suspend fun onSendPacket(packet: JdwpPacketView) {
    sendMutex.withLock {
      sendBuffer.clear()
      sendBuffer.appendJdwpPacket(packet)
      tracer.addPacket(sendBuffer.afterChannelRead(0))
    }
  }

  override suspend fun onReceivePacket(packet: JdwpPacketView) {
    receiveMutex.withLock {
      receiveBuffer.clear()
      receiveBuffer.appendJdwpPacket(packet)
      tracer.addPacket(receiveBuffer.afterChannelRead(0))
    }
  }

  override fun close() {
    tracer.close()
  }
}
