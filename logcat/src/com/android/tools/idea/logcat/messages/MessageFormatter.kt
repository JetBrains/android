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
package com.android.tools.idea.logcat.messages

import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogcatMessage
import java.nio.file.Path
import java.time.ZoneId

private val exceptionLinePattern = Regex("\n\\s*at .+\\(.+\\)\n")

/** Formats [LogcatMessage]'s into a [TextAccumulator] */
internal class MessageFormatter(
  private val logcatColors: LogcatColors,
  private val zoneId: ZoneId,
) {
  private var proguardMessageRewriter = ProguardMessageRewriter()

  fun setProguardMap(path: Path) {
    proguardMessageRewriter.loadProguardMap(path)
  }

  // Keeps track of the previous tag, so we can omit on consecutive lines
  // TODO(aalbert): This was borrowed from Pidcat. Should we do it too? Should we also do it for
  // app?
  private var previousTag: String? = null
  private var previousPid: Int? = null

  fun formatMessages(
    formattingOptions: FormattingOptions,
    textAccumulator: TextAccumulator,
    messages: List<LogcatMessage>,
  ) {
    // Replace each newline with a newline followed by the indentation of the message portion
    val headerWidth = formattingOptions.getHeaderWidth()
    val newline = "\n".padEnd(headerWidth + 1)
    for (message in messages) {
      val start = textAccumulator.getTextLength()
      val header = message.header

      if (message.header === SYSTEM_HEADER) {
        textAccumulator.accumulate(message.message)
      } else {
        val tag = header.tag
        val appName = header.getAppName()

        textAccumulator.accumulate(
          formattingOptions.timestampFormat.format(header.timestamp, zoneId)
        )
        textAccumulator.accumulate(
          formattingOptions.processThreadFormat.format(header.pid, header.tid)
        )
        textAccumulator.accumulate(
          text = formattingOptions.tagFormat.format(tag, previousTag),
          textAttributes =
            if (formattingOptions.tagFormat.colorize) logcatColors.getTagColor(tag) else null,
        )
        textAccumulator.accumulate(
          text = formattingOptions.appNameFormat.format(appName, header.pid, previousPid)
        )
        textAccumulator.accumulate(
          text =
            formattingOptions.processNameFormat.format(header.processName, header.pid, previousPid)
        )

        formattingOptions.levelFormat.format(header.logLevel, textAccumulator, logcatColors)

        // Allow extensions to rewrite exception traces
        // e.g. Gemini plugin can add a link to invoke Gemini
        val exceptionFormatters = ExceptionMessageRewriter.EP_NAME.extensionList
        val msg =
          when {
            exceptionFormatters.isEmpty() -> message.message
            exceptionLinePattern.containsMatchIn(message.message) -> {
              var result = message.message
              for (formatter in exceptionFormatters) {
                result = formatter.rewrite(result)
              }
              if (StudioFlags.LOGCAT_DEOBFUSCATE.get()) {
                result = proguardMessageRewriter.rewrite(result)
              }
              result
            }
            else -> message.message
          }

        textAccumulator.accumulate(
          text = msg.replace("\n", newline),
          textAttributesKey = logcatColors.getMessageKey(header.logLevel),
        )
        previousTag = tag
        previousPid = header.pid
      }
      textAccumulator.accumulate("\n")
      val end = textAccumulator.getTextLength()
      textAccumulator.addMessageRange(start, end - 1, message)
    }
  }

  fun reset() {
    previousTag = null
    previousPid = null
  }
}
