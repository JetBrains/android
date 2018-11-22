/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.build.output

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.intellij.build.FilePosition
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import java.io.File
import java.util.function.Consumer
import com.android.tools.idea.gradle.output.parser.androidPlugin.DataBindingOutputParser as PluginDataBindingOutputParser

private const val DATABINDING_GROUP = "Data Binding compiler"
private const val ERROR_LOG_HEADER = "Found data binding error(s):"
private const val ERROR_LOG_PREFIX = "[databinding] "

/**
 * Parser for data binding output errors, which look something like this:
 *
 * ```
 * Found data binding error(s):
 *
 * [databinding] { ... json encoded error ... }
 * [databinding] { ... json encoded error ... }
 * ```
 *
 * JSON structures are defined in `ScopedException` in the databinding compiler codebase.
 */
class DataBindingOutputParser : BuildOutputParser {
  /**
   * JSON parser, shared across parsing calls to take advantage of the caching it does.
   */
  private val gson = Gson()

  override fun parse(line: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in MessageEvent>): Boolean {
    if (line.contains(ERROR_LOG_HEADER)) {
      // Consume useless log header so other parsers don't waste time on it
      return true
    }

    val errorPrefix = line.indexOf(ERROR_LOG_PREFIX)
    if (errorPrefix >= 0) {
      val errorStart = errorPrefix + ERROR_LOG_PREFIX.length
      return parseErrorIn(line.substring(errorStart), reader, messageConsumer)
    }
    return false
  }

  private fun parseErrorIn(output: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in MessageEvent>): Boolean {
    try {
      val msg = gson.fromJson<EncodedMessage>(output, EncodedMessage::class.java)
      val summary = msg.message.substringBefore('\n')
      if (msg.locations.isEmpty()) {
        messageConsumer.accept(MessageEventImpl(reader.buildId, MessageEvent.Kind.ERROR, DATABINDING_GROUP, summary, msg.message))
      }
      else {
        val sourceFile = File(msg.filePath)
        val location = msg.locations.first()
        val filePosition = FilePosition(sourceFile, location.startLine, location.startCol, location.endLine, location.endCol)
        messageConsumer.accept(
          FileMessageEventImpl(reader.buildId, MessageEvent.Kind.ERROR, DATABINDING_GROUP, summary, msg.message, filePosition))
      }
      return true
    }
    catch (ignored: Exception) {
      return false
    }
  }

  private data class EncodedMessage(
    @SerializedName("msg") val message: String,
    @SerializedName("file") val filePath: String,
    @SerializedName("pos") val locations: List<FileLocation>
  )

  private data class FileLocation(
    @SerializedName("line0") val startLine: Int,
    @SerializedName("col0") val startCol: Int,
    @SerializedName("line1") val endLine: Int,
    @SerializedName("col1") val endCol: Int)
}
