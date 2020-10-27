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
import com.intellij.build.events.BuildEvent
import com.intellij.build.events.MessageEvent
import com.intellij.build.events.impl.FileMessageEventImpl
import com.intellij.build.events.impl.MessageEventImpl
import com.intellij.build.output.BuildOutputInstantReader
import com.intellij.build.output.BuildOutputParser
import java.io.File
import java.util.function.Consumer

const val DATABINDING_GROUP = "Data Binding compiler"
internal const val ERROR_LOG_PREFIX = "[databinding] "

internal data class EncodedMessage(
  @SerializedName("msg") val message: String,
  @SerializedName("file") val filePath: String,
  @SerializedName("pos") val locations: List<FileLocation>
)

internal data class FileLocation(
  @SerializedName("line0") val startLine: Int,
  @SerializedName("col0") val startCol: Int,
  @SerializedName("line1") val endLine: Int,
  @SerializedName("col1") val endCol: Int)

/**
 * Parser for data binding output errors. This class supports parsing both JSON data binding
 * formats and a legacy hand-crafted format, as well as finding and reporting any errors found within the
 * exception obtained through the Gradle tooling api.
 */
class DataBindingOutputParser : BuildOutputParser {

  private val jsonFormatter = JsonDataBindingOutputParser()
  private val legacyFormatter = LegacyDataBindingOutputParser()

  override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
    return jsonFormatter.parse(line, reader, messageConsumer) || legacyFormatter.parse(line, reader, messageConsumer)
  }

  /**
   * Parser for data binding JSON output errors, which look something like this:
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
  private class JsonDataBindingOutputParser : BuildOutputParser {
    companion object {
      private const val ERROR_LOG_HEADER = "Found data binding error(s):"
    }

    /**
     * JSON parser, shared across parsing calls to take advantage of the caching it does.
     */
    private val gson = Gson()

    override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
      if (line == null || reader == null || messageConsumer == null) return false
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
          messageConsumer.accept(MessageEventImpl(reader.parentEventId, MessageEvent.Kind.ERROR, DATABINDING_GROUP, summary, msg.message))
        }
        else {
          // Note: msg.filePath is relative, but the build output window can't seem to find the
          // file unless we feed it the absolute path directly.
          val sourceFile = File(msg.filePath).absoluteFile
          val location = msg.locations.first()
          val filePosition = FilePosition(sourceFile, location.startLine, location.startCol, location.endLine, location.endCol)
          messageConsumer.accept(
            FileMessageEventImpl(reader.parentEventId, MessageEvent.Kind.ERROR, DATABINDING_GROUP, summary, msg.message, filePosition))
        }
        return true
      }
      catch (ignored: Exception) {
        return false
      }
    }
  }

  /**
   * Parser for legacy data binding output errors, which look something like this:
   *
   * ```
   * Found data binding errors.
   * **** data binding error ****msg:... file:... loc:... ****\ data binding error ****\n" +
   * **** data binding error ****msg:... file:... loc:... ****\ data binding error ****\n" +
   * ```
   *
   * where `msg` is the user-readable error message, `file` is a path to the problematic file,
   * and `loc` refers to the start and end positions surrounding the error
   */
  private class LegacyDataBindingOutputParser : BuildOutputParser {
    companion object {
      private const val ERROR_LOG_HEADER = "Found data binding errors."

      private val ERROR_LOG_REGEX = Regex("""\*\*\*\*/ data binding error \*\*\*\*(.+)\*\*\*\*\\ data binding error \*\*\*\*""")
      private val ERROR_MESSAGE_REGEX = Regex("msg:(.+) file:(.+) loc:(.+) ")
      /**
       * Location looks something like: loc:36:28 - 36:32
       */
      private val LOCATION_REGEX = Regex("""(\d+):(\d+) - (\d+):(\d+)""")
    }

    override fun parse(line: String?, reader: BuildOutputInstantReader?, messageConsumer: Consumer<in BuildEvent>?): Boolean {
      if (line == null || reader == null || messageConsumer == null) return false
      if (line.contains(ERROR_LOG_HEADER)) {
        // Consume useless log header so other parsers don't waste time on it
        return true
      }

      val match = ERROR_LOG_REGEX.matchEntire(line.trim()) ?: return false
      val message = match.groupValues[1]
      return parseErrorIn(message, reader, messageConsumer)
    }

    private fun parseErrorIn(output: String, reader: BuildOutputInstantReader, messageConsumer: Consumer<in MessageEvent>): Boolean {
      try {
        val messageMatch = ERROR_MESSAGE_REGEX.matchEntire(output) ?: return false
        val msg = messageMatch.groupValues[1]
        val file = messageMatch.groupValues[2]
        val loc = messageMatch.groupValues[3]

        val locMatch = LOCATION_REGEX.matchEntire(loc) ?: return false
        val startLine = locMatch.groupValues[1].toInt()
        val startCol = locMatch.groupValues[2].toInt()
        val endLine = locMatch.groupValues[3].toInt()
        val endCol = locMatch.groupValues[4].toInt()

        val sourceFile = File(file)
        val filePosition = FilePosition(sourceFile, startLine, startCol, endLine, endCol)
        messageConsumer.accept(FileMessageEventImpl(reader.parentEventId, MessageEvent.Kind.ERROR, DATABINDING_GROUP, msg, null, filePosition))
        return true
      }
      catch (ignored: Exception) {
        return false
      }
    }
  }
}
