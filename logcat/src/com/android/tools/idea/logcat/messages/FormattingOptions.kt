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

import com.android.tools.idea.logcat.LogcatBundle
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.COMPACT
import com.android.tools.idea.logcat.messages.FormattingOptions.Style.STANDARD
import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.BOTH
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.DATETIME
import com.android.tools.idea.logcat.messages.TimestampFormat.Style.TIME
import com.intellij.openapi.util.NlsActions.ActionText

// TODO(aalbert): This will come from a PersistentStateComponent eventually
private val standardFormattingOptions = FormattingOptions(
  TimestampFormat(DATETIME, enabled = true),
  ProcessThreadFormat(BOTH, enabled = true),
  TagFormat(maxLength = 23, hideDuplicates = false, enabled = true),
  AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = true)
)

// TODO(aalbert): This will come from a PersistentStateComponent eventually
private val compactFormattingOptions = FormattingOptions(
  TimestampFormat(TIME, enabled = true),
  ProcessThreadFormat(BOTH, enabled = false),
  TagFormat(maxLength = 23, hideDuplicates = false, enabled = false),
  AppNameFormat(maxLength = 35, hideDuplicates = false, enabled = false)
)

/**
 * Formatting options of a Logcat panel.
 */
internal data class FormattingOptions(
  var timestampFormat: TimestampFormat = TimestampFormat(DATETIME),
  var processThreadFormat: ProcessThreadFormat = ProcessThreadFormat(BOTH, enabled = true),
  var tagFormat: TagFormat = TagFormat(),
  var appNameFormat: AppNameFormat = AppNameFormat(),
) {
  enum class Style(val formattingOptions: FormattingOptions, val displayName: @ActionText String) {
    STANDARD(standardFormattingOptions, LogcatBundle.message("logcat.format.action.standard")),
    COMPACT(compactFormattingOptions, LogcatBundle.message("logcat.format.action.compact")),
  }

  fun getHeaderWidth() = appNameFormat.width() + tagFormat.width() + processThreadFormat.width() + timestampFormat.width()

  fun getStyle(): Style? {
    return when  {
      this === standardFormattingOptions -> STANDARD
      this === compactFormattingOptions -> COMPACT
      else -> null
    }
  }
}
