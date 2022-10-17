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

import com.android.tools.idea.logcat.SYSTEM_HEADER
import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.idea.logcat.messages.TextAccumulator.FilterHint.AppName
import com.android.tools.idea.logcat.messages.TextAccumulator.FilterHint.Tag
import java.time.ZoneId
import kotlin.math.min


/**
 * Formats [LogcatMessage]'s into a [TextAccumulator]
 */
internal class MessageFormatter(private val logcatColors: LogcatColors, private val zoneId: ZoneId) {
  // Keeps track of the previous tag, so we can omit on consecutive lines
  // TODO(aalbert): This was borrowed from Pidcat. Should we do it too? Should we also do it for app?
  private var previousTag: String? = null
  private var previousPid: Int? = null

  fun formatMessages(formattingOptions: FormattingOptions, textAccumulator: TextAccumulator, messages: List<LogcatMessage>) {
    // Replace each newline with a newline followed by the indentation of the message portion
    val newline = "\n".padEnd(formattingOptions.getHeaderWidth() + 1)
    for (message in messages) {
      if (message.header === SYSTEM_HEADER) {
        textAccumulator.accumulate(message.message + '\n')
        continue
      }
      val start = textAccumulator.getTextLength()
      val header = message.header
      val tag = header.tag
      val appName = header.getAppName()

      textAccumulator.accumulate(formattingOptions.timestampFormat.format(header.timestamp, zoneId))
      textAccumulator.accumulate(formattingOptions.processThreadFormat.format(header.pid, header.tid))
      textAccumulator.accumulate(
        text = formattingOptions.tagFormat.format(tag, previousTag),
        textAttributes = logcatColors.getTagColor(tag),
        filterHint = getTagFilterHint(tag, formattingOptions))
      textAccumulator.accumulate(
        text = formattingOptions.appNameFormat.format(appName, header.pid, previousPid),
        filterHint = getAppNameFilterHint(appName, formattingOptions))

      formattingOptions.levelFormat.format(header.logLevel, textAccumulator, logcatColors)

      textAccumulator.accumulate(
        text = "${message.message.replace("\n", newline)}\n",
        textAttributesKey = logcatColors.getMessageKey(header.logLevel))

      val end = textAccumulator.getTextLength()
      textAccumulator.addMessageRange(start, end, message)

      previousTag = tag
      previousPid = header.pid
    }
  }
}

private fun getTagFilterHint(tag: String,  formattingOptions: FormattingOptions): Tag? {
  return when {
    formattingOptions.tagFormat.enabled -> Tag(tag, min(tag.length, formattingOptions.tagFormat.maxLength))
    else -> null
  }
}

private fun getAppNameFilterHint(appName: String,  formattingOptions: FormattingOptions): AppName? {
  return when {
    appName == "?" -> null
    formattingOptions.appNameFormat.enabled -> AppName(appName, min(appName.length, formattingOptions.appNameFormat.maxLength))
    else -> null
  }
}
