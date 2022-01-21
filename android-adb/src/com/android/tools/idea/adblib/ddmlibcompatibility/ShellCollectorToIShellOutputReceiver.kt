/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.adblib.ddmlibcompatibility

import com.android.adblib.ShellCollector
import com.android.ddmlib.IShellOutputReceiver
import kotlinx.coroutines.flow.FlowCollector
import java.nio.ByteBuffer

/**
 * Implementation of `adblib` [ShellCollector] that forwards to a `ddmlib` [IShellOutputReceiver] implementation
 */
class ShellCollectorToIShellOutputReceiver(private val receiver: IShellOutputReceiver) : ShellCollector<Unit> {
  val buf = ByteArrayFromByteBuffer()

  override suspend fun start(collector: FlowCollector<Unit>) {
    // Nothing to do
  }

  override suspend fun collect(collector: FlowCollector<Unit>, stdout: ByteBuffer) {
    if (receiver.isCancelled) {
      cancelCoroutine("IShellOutputReceiver was cancelled during shell command execution")
    }
    buf.convert(stdout)
    receiver.addOutput(buf.bytes, buf.offset, buf.count)
  }

  override suspend fun end(collector: FlowCollector<Unit>) {
    receiver.flush()
    collector.emit(Unit)
  }

  class ByteArrayFromByteBuffer {
    var bytes = ByteArray(0)
    var offset = 0
    var count = 0

    fun convert(buffer: ByteBuffer) {
      if (buffer.hasArray()) {
        bytes = buffer.array()
        offset = buffer.position()
        count = buffer.remaining()
      }
      else {
        offset = 0
        count = buffer.remaining()
        val bytes = ByteArray(count)
        buffer.get(bytes)
        this.bytes = bytes
      }
    }
  }
}
