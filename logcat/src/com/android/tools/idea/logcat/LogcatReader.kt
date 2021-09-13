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
package com.android.tools.idea.logcat

import com.android.ddmlib.IDevice
import com.android.ddmlib.logcat.LogCatMessage
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.google.common.util.concurrent.ThreadFactoryBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Starts a background thread that reads logcat messages and sends them back to the caller.
 */
class LogcatReader(
  private val device: IDevice,
  disposableParent: Disposable,
  appendMessages: suspend (List<LogCatMessage>) -> Unit
) : Disposable {

  // TODO(b/200160304): Until we switch to a coroutine friendly logcat execution function, we use the legacy version which blocks a thread.
  //  Since we can run arbitrarily many logcat windows, we can't block threads from the standard pools, especially not the seemingly
  //  appropriate IO pool which has only 4 thread. Once the coroutine version is available, threads will suspend rather than block and we
  //  can eliminate this thread pool.
  private val threadFactory = ThreadFactoryBuilder()
    .setNameFormat("Android Logcat Service Thread %s for Device Serial Number $device")
    .build()

  private val logcatReceiver = LogcatReceiver(
    device,
    this,
    object : LogcatReceiver.LogcatListener {
      val scope = AndroidCoroutineScope(this@LogcatReader, workerThread)

      override fun onLogMessagesReceived(messages: List<LogCatMessage>) {
        scope.launch {
          appendMessages(messages)
        }
      }
    })

  init {
    Disposer.register(disposableParent, this)
  }

  fun start() {
    // The thread is released on dispose() when logcatReceiver.isCanceled() returns true and executeShellCommand() aborts.
    Executors.newSingleThreadExecutor(threadFactory).execute {
      device.executeShellCommand("logcat -v long -v epoch", logcatReceiver)
    }
  }

  override fun dispose() {}
}

