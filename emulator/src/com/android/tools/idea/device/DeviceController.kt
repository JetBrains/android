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

import com.android.utils.Base128OutputStream
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.TimeUnit

/**
 * Controls the device by sending control messages to it.
 */
class DeviceController(
  disposableParent: Disposable,
  val controlChannel: SuspendingSocketChannel
) : Disposable {

  private val executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(javaClass.simpleName, 1)
  private val outputStream = Base128OutputStream(newOutputStream(controlChannel, CONTROL_MSG_BUFFER_SIZE))

  init {
    Disposer.register(disposableParent, this)
  }

  fun sendControlMessage(message: ControlMessage) {
    executor.submit {
      send(message)
    }
  }

  private fun send(message:ControlMessage) {
    message.serialize(outputStream)
    outputStream.flush()
  }

  override fun dispose() {
    executor.shutdown()
    executor.awaitTermination(2, TimeUnit.SECONDS)
  }
}

const val CONTROL_MSG_BUFFER_SIZE = 4096