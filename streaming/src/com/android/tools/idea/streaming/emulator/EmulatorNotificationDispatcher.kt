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
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.DisposableWrapperList
import com.intellij.util.ui.UIUtil
import com.jetbrains.rd.util.getOrCreate
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.datetime.Instant
import org.jetbrains.annotations.TestOnly

/**
 * Dispatcher of logged emulator notification messages that keeps a 5-second backlog of recently logged notifications and allows its
 * listeners to receive notifications from that backlog.
 */
@Service
internal class EmulatorNotificationDispatcher : EmulatorLogListener, Disposable {

  private val recentMessages = mutableMapOf<ProcessHandle, PerishableItemQueue<Message>>()
  private val listeners = DisposableWrapperList<ListenerWithProcessHandle>()
  private var messageExpiration: Duration = 5.seconds

  init {
    ApplicationManager.getApplication().messageBus.connect(this).subscribe(EmulatorLogListener.TOPIC, this)
  }

  @UiThread
  fun addListener(emulatorProcessHandle: ProcessHandle, listener: Listener, playBackRecentMessages: Boolean = true) {
    if (listener is Disposable) {
      listeners.add(ListenerWithProcessHandle(listener, emulatorProcessHandle), listener)
    } else {
      listeners.add(ListenerWithProcessHandle(listener, emulatorProcessHandle))
    }
    if (playBackRecentMessages) {
      recentMessages[emulatorProcessHandle]?.forEach { listener.notificationMessageLogged(it.severity, it.text) }
    }
  }

  @UiThread
  fun removeListener(emulatorProcessHandle: ProcessHandle, listener: Listener) {
    listeners.find { it.processHandle == emulatorProcessHandle && it.listener == listener }?.let { listeners.remove(it) }
  }

  @AnyThread
  override fun messageLogged(
    sourceProcess: ProcessHandle,
    avdFolder: Path,
    severity: EmulatorLogListener.Severity,
    notifyUser: Boolean,
    message: String,
  ) {
    if (!notifyUser) {
      return
    }
    UIUtil.invokeLaterIfNeeded { notifyListenersAndSaveMessage(sourceProcess, severity, message) }
  }

  override fun dispose() {}

  @UiThread
  private fun notifyListenersAndSaveMessage(sourceProcess: ProcessHandle, severity: EmulatorLogListener.Severity, message: String) {
    val expirationTime = Instant.fromEpochMilliseconds(System.currentTimeMillis()) + messageExpiration
    for ((listener, processHandle) in listeners) {
      if (processHandle == sourceProcess) {
        listener.notificationMessageLogged(severity, message)
      }
    }
    val list =
      recentMessages.getOrCreate(sourceProcess) { processHandle ->
        processHandle.onExit().thenRun {
          val queue = recentMessages.remove(processHandle)
          queue?.let { Disposer.dispose(it) }
        }
        PerishableItemQueue<Message>().also { Disposer.register(this, it) }
      }
    list.add(Message(expirationTime, severity, message))
  }

  /** Removes all accumulated notification messages. */
  @TestOnly
  @UiThread
  fun reset() {
    recentMessages.values.forEach { it.clear() }
    recentMessages.clear()
  }

  @TestOnly
  fun setMessageExpiration(duration: Duration) {
    messageExpiration = duration
  }

  companion object {
    @JvmStatic
    fun getInstance(): EmulatorNotificationDispatcher =
      ApplicationManager.getApplication().getService(EmulatorNotificationDispatcher::class.java)
  }

  interface Listener {
    fun notificationMessageLogged(severity: EmulatorLogListener.Severity, message: String)

    // TODO: Define typealias Severity = EmulatorLogListener.Severity after upgrading to Kotlin 2.2
  }

  private data class ListenerWithProcessHandle(val listener: Listener, val processHandle: ProcessHandle)

  private data class Message(override val expirationTime: Instant, val severity: EmulatorLogListener.Severity, val text: String) :
    Perishable
}
