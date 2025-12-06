/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.streaming.emulator

import com.android.annotations.concurrency.AnyThread
import com.android.annotations.concurrency.UiThread
import com.android.tools.idea.avdmanager.EmulatorLogListener
import com.android.tools.idea.concurrency.createCoroutineScope
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.getOrCreate
import java.nio.file.Path
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

/**
 * Dispatcher of logged emulator notification messages that keeps a 5-second backlog of recently
 * logged notifications and allows its listeners to receive notifications from that backlog.
 */
@Service
internal class EmulatorNotificationDispatcher : EmulatorLogListener, Disposable {

  private val recentMessages = mutableMapOf<ProcessHandle, ArrayDeque<Message>>()
  private val scope = createCoroutineScope()
  private var janitor: Job? = null
  private val listeners = DisposableWrapperList<ListenerWithProcessHandle>()

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(EmulatorLogListener.TOPIC, this)
  }

  @UiThread
  fun addListener(emulatorProcessHandle: ProcessHandle, listener: Listener, playBackRecentMessages: Boolean = true) {
    if (listener is Disposable) {
      listeners.add(ListenerWithProcessHandle(listener, emulatorProcessHandle), listener)
    }
    else {
      listeners.add(ListenerWithProcessHandle(listener, emulatorProcessHandle))
    }
    if (playBackRecentMessages) {
      removeExpiredMessages()
      recentMessages[emulatorProcessHandle]?.forEach {
        listener.notificationMessageLogged(it.severity, it.text)
      }
    }
  }

  @AnyThread
  override fun messageLogged(sourceProcess: ProcessHandle,
                             avdFolder: Path,
                             severity: EmulatorLogListener.Severity,
                             notifyUser: Boolean,
                             message: String) {
    if (!notifyUser) {
      return
    }
    UIUtil.invokeLaterIfNeeded {
      notifyListenersAndSaveMessage(sourceProcess, severity, message)
    }
  }

  override fun dispose() {
  }

  @UiThread
  private fun notifyListenersAndSaveMessage(sourceProcess: ProcessHandle, severity: EmulatorLogListener.Severity, message: String) {
    val timestamp = System.currentTimeMillis()
    for ((listener, processHandle) in listeners) {
      if (processHandle == sourceProcess) {
        listener.notificationMessageLogged(severity, message)
      }
    }
    val list = recentMessages.getOrCreate(sourceProcess) { processHandle ->
      processHandle.onExit().thenRun { recentMessages.remove(processHandle) }
      ArrayDeque()
    }
    list.add(Message(timestamp, severity, message))
    if (janitor == null) {
      janitor = scope.launch(Dispatchers.EDT) {
        do {
          delay(MESSAGE_EXPIRATION)
          removeExpiredMessages()
        } while (recentMessages.isNotEmpty())
        janitor = null
      }
    }
  }

  private fun removeExpiredMessages() {
    val timeThreshold = System.currentTimeMillis() - MESSAGE_EXPIRATION.inWholeMilliseconds
    val iter = recentMessages.iterator()
    while (iter.hasNext()) {
      val (processHandle, list) = iter.next()
      while (list.isNotEmpty() && list.first().timestamp < timeThreshold) {
        list.removeFirst()
      }
      if (list.isEmpty()) {
        iter.remove()
      }
    }
  }

  /** Removes all accumulated notification messages. */
  @TestOnly
  @UiThread
  fun reset() {
    recentMessages.clear()
    janitor?.cancel()
    janitor = null
  }

  companion object {
    @JvmStatic
    fun getInstance(): EmulatorNotificationDispatcher =
        ApplicationManager.getApplication().getService(EmulatorNotificationDispatcher::class.java)

    @JvmStatic
    private val MESSAGE_EXPIRATION = 5.seconds
  }

  interface Listener {
    fun notificationMessageLogged(severity: EmulatorLogListener.Severity, message: String)

    // TODO: Define typealias Severity = EmulatorLogListener.Severity after upgrading to Kotlin 2.2
  }

  private data class ListenerWithProcessHandle(val listener: Listener, val processHandle: ProcessHandle)
  private data class Message(val timestamp: Long, val severity: EmulatorLogListener.Severity, val text: String)
}

