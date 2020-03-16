/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.dagger

import com.android.tools.idea.flags.StudioFlags
import com.intellij.build.FilePosition
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import java.io.File
import java.util.function.Consumer

/**
 * Parses Dagger errors in build output.
 *
 * Creates dedicated child node for Dagger error. Makes message more readable and prepare it for [DaggerConsoleFilter].
 */
class DaggerOutputParser : BuildOutputParser {
  companion object {
    const val ERROR_PREFIX = "[Dagger/"
    private val FILE_REGEXP = Regex("(.+):(\\d+):(\\d)?")
    private const val ERROR_GROUP = "DAGGER"
  }

  override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
    if (StudioFlags.DAGGER_SUPPORT_ENABLED.get().not()) return false
    if (line == null || reader == null || messageConsumer == null) return false
    return line.contains(ERROR_PREFIX) && parseError(line, reader, messageConsumer)
  }

  private fun readMessage(reader: BuildOutputInstantReader): String {
    return StringBuilder().apply {
      while (true) {
        val nextLine = reader.readLine() ?: break
        appendln(nextLine)
      }
    }.toString()
  }

  private fun parseError(
    output: String,
    reader: BuildOutputInstantReader,
    messageConsumer: Consumer<in MessageEvent>
  ): Boolean {
    val fileLine = output.substringBefore(" ")
    val fileMatches = FILE_REGEXP.find(fileLine) ?: return false
    val fileName = fileMatches.groups[1]!!.value
    val line = fileMatches.groups[2]?.value?.toInt() ?: 0
    val column = fileMatches.groups[3]?.value?.toInt() ?: 0
    val filePosition = FilePosition(File(fileName), line, column)

    val errorHeaderStart = output.indexOf(ERROR_PREFIX)
    val errorHeaderEnd = output.indexOf(']', errorHeaderStart)
    val errorHeader = output.substring(errorHeaderStart, errorHeaderEnd + 1)

    val detailedMessage = StringBuilder().apply {
      appendln(fileLine)
      appendln(errorHeader)
      appendln(output.substring(errorHeaderEnd + 1))
      append(readMessage(reader))
    }.toString()

    messageConsumer.accept(FileMessageEventImpl(
      reader.parentEventId, MessageEvent.Kind.ERROR, ERROR_GROUP, errorHeader, detailedMessage, filePosition
    ))
    return true
  }
}
