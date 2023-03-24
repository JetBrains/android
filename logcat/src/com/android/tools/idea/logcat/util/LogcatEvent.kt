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
package com.android.tools.idea.logcat.util

import com.android.tools.idea.logcat.LogcatPresenter
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.util.LogcatEvent.LogcatMessagesEvent
import com.android.tools.idea.logcat.util.LogcatEvent.LogcatPanelVisibility
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.atomic.AtomicBoolean

private val logger = Logger.getInstance(LogcatEvent::class.java)

/**
 * An event containing Logcat messages or an indication of the visibility of the target Logcat panel.
 */
sealed class LogcatEvent {
  class LogcatMessagesEvent(val messages: List<LogcatMessage>) : LogcatEvent()
  data class LogcatPanelVisibility(val visible: Boolean) : LogcatEvent()
}

/**
 * Handles a [Flow<LogcatEvent>]
 *
 * This function handles [LogcatMessage]s getting sent to a [LogcatPresenter] (panel) than can be invisible.
 * When a panel becomes invisible, it is moved to an "invisible mode" where it reduces its memory footprint.
 * While invisible, messages are not sent to the panel, rather they are stored in a temporary file.
 * When panel becomes visible again, the temporary file is read and the messages are sent to the panel again.
 */
internal suspend fun Flow<LogcatEvent>.consume(
  logcatPresenter: LogcatPresenter,
  id: String,
  maxSizeBytes: Int,

) {
  val messagesFile = MessagesFile(id, maxSizeBytes)
  val visible = logcatPresenter.isShowing()
  val isPanelVisible = AtomicBoolean(visible)
  if (!visible) {
    messagesFile.initialize()
  }

  try {
    collect { event ->
      // Note that onPanelVisible & onPanelInvisible are extracted to functions for readability and can be treated as inlined. Therefore,
      // it is safe for them to access their parameters without being concerned about the threading model.
      when {
        event == LogcatPanelVisibility(true) -> onPanelVisible(logcatPresenter, id, messagesFile, isPanelVisible)
        event == LogcatPanelVisibility(false) -> onPanelInvisible(logcatPresenter, id, messagesFile, isPanelVisible)
        isPanelVisible.get() -> logcatPresenter.processMessages((event as LogcatMessagesEvent).messages)
        else -> messagesFile.appendMessages((event as LogcatMessagesEvent).messages)
      }
    }
  }
  finally {
    logger.debug { "Cleaning up for panel $id" }
    messagesFile.delete()
  }
}

private suspend fun onPanelVisible(
  logcatPresenter: LogcatPresenter,
  id: String,
  messagesFile: MessagesFile,
  isPanelVisible: AtomicBoolean
) {
  logger.debug { "Panel for $id is now visible. Loading messages from file cleaning up" }
  isPanelVisible.set(true)
  logcatPresenter.processMessages(messagesFile.loadMessagesAndDelete())
}

private suspend fun onPanelInvisible(
  logcatPresenter: LogcatPresenter,
  id: String,
  messagesFile: MessagesFile,
  isPanelVisible: AtomicBoolean
) {
  logger.debug { "Panel for $id is now invisible. Initializing message file and entering invisible mode" }
  isPanelVisible.set(false)
  messagesFile.initialize()
  messagesFile.appendMessages(logcatPresenter.getBacklogMessages())
  logcatPresenter.enterInvisibleMode()
}
