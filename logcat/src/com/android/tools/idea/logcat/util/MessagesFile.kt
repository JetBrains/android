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

import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.message.readLogcatMessage
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.io.FileUtil
import com.intellij.util.io.inputStream
import com.intellij.util.io.outputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name

private val eof = LogcatMessage(SYSTEM_HEADER, "EOF")

/**
 * Manages a temporary file of [LogcatMessage]s
 *
 * The amount of data stored in the file(s) is capped by [maxSizeBytes]. To make things simple, rather than deleting entries from the start of
 * the file when the size is exceeded, we keep a rolling set of 2 files. This results in us actually keeping up to `2*maxSize` which is OK.
 */
internal class MessagesFile(
  private val name: String,
  private val maxSizeBytes: Int,
  private val createTempFile: (String, String) -> Path = { prefix: String, suffix: String ->
    FileUtil.createTempFile(prefix, suffix, true).toPath()
  }
) {
  private val logger = thisLogger()
  private var file: Path? = null
  private var previousFile: Path? = null
  private var outputStream: ObjectOutputStream? = null
  private var sizeBytes = 0

  /**
   * Initialize the temporary file
   */
  fun initialize() {
    file = createTempFile("studio-$name", ".bin").also {
      outputStream = ObjectOutputStream(it.outputStream())
    }
    sizeBytes = 0
    logger.debug { "Created message file ${file?.name}" }
  }

  /**
   * Write messages to the tmp file.
   *
   * When the file exceeds a certain size, close it and create a new one. We always keep the last 2 files and delete the rest.
   */
  fun appendMessages(messages: List<LogcatMessage>) {
    if (sizeBytes > maxSizeBytes) {
      logger.debug { "File ${file?.name} exceeded max size ($sizeBytes > $maxSizeBytes)" }
      outputStream?.writeEofAndClose()
      previousFile.delete()
      previousFile = file
      initialize()
    }

    val stream = outputStream ?: throw IllegalStateException("message file for $name is not initialized")
    logger.debug { "Appending ${messages.size} messages to file ${file?.name}" }
    messages.forEach {
      sizeBytes += it.message.length
      it.writeExternal(stream)
    }
  }

  /**
   * Load messages from the 2 files and delete them.
   */
  fun loadMessagesAndDelete(): List<LogcatMessage> {
    outputStream?.writeEofAndClose()
    return buildList {
      addAll(previousFile?.readMessages() ?: emptyList())
      addAll(file?.readMessages() ?: throw IllegalStateException("message file for $name is not initialized"))
      delete()
    }
  }

  /**
   * Delete all files and clean up
   */
  fun delete() {
    file.delete()
    previousFile.delete()
    file = null
    previousFile = null
    outputStream = null
    sizeBytes = 0
  }

  private fun Path.readMessages(): List<LogcatMessage> {
    ObjectInputStream(inputStream()).use {
      val messages = buildList {
        while (true) {
          val item = it.readLogcatMessage()
          if (item == eof) {
            break
          }
          add(item)
        }
      }
      logger.debug { "Loaded ${messages.size} messages from file $name" }
      return messages
    }
  }

  private fun Path?.delete() {
    val deleted = this?.deleteIfExists()
    if (deleted == true) {
      logger.debug { "Deleted file ${this?.name}" }
    }
  }

  private fun ObjectOutputStream.writeEofAndClose() {
    eof.writeExternal(this)
    close()
  }
}
