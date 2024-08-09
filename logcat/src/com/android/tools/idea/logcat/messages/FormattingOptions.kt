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
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.util.Range

/** Formatting options of a Logcat panel. */
internal data class FormattingOptions(
  var timestampFormat: TimestampFormat = TimestampFormat(DATETIME),
  var processThreadFormat: ProcessThreadFormat = ProcessThreadFormat(BOTH, enabled = true),
  var tagFormat: TagFormat = TagFormat(),
  var appNameFormat: AppNameFormat = AppNameFormat(),
  var processNameFormat: ProcessNameFormat = AppNameFormat(enabled = false),
  var levelFormat: LevelFormat = LevelFormat(),
) {
  enum class Style(val displayName: @ActionText String) {
    STANDARD(LogcatBundle.message("logcat.format.action.standard")) {
      override val formattingOptions: FormattingOptions
        get() = AndroidLogcatFormattingOptions.getInstance().standardFormattingOptions
    },
    COMPACT(LogcatBundle.message("logcat.format.action.compact")) {
      override val formattingOptions: FormattingOptions
        get() = AndroidLogcatFormattingOptions.getInstance().compactFormattingOptions
    };

    // Needs to be abstract so Style enum values do not depend on AndroidLogcatFormattingOptions
    // because AndroidLogcatFormattingOptions has
    // a field of type Style that needs to be initialized with a value.
    abstract val formattingOptions: FormattingOptions
  }

  fun getHeaderWidth() =
    appNameFormat.width() +
      processNameFormat.width() +
      tagFormat.width() +
      processThreadFormat.width() +
      timestampFormat.width() +
      levelFormat.width()

  fun getStyle(): Style? {
    return when {
      this == AndroidLogcatFormattingOptions.getInstance().standardFormattingOptions -> STANDARD
      this == AndroidLogcatFormattingOptions.getInstance().compactFormattingOptions -> COMPACT
      else -> null
    }
  }

  fun getTagRange(): Range<Int> {
    if (!tagFormat.enabled) {
      return Range(-1, -1)
    }
    val start = timestampFormat.width() + processThreadFormat.width()
    return Range(start, start + tagFormat.width() - 1)
  }

  fun getAppIdRange(): Range<Int> {
    if (!appNameFormat.enabled) {
      return Range(-1, -1)
    }
    val start = timestampFormat.width() + processThreadFormat.width() + tagFormat.width()
    return Range(start, start + appNameFormat.width() - 1)
  }

  fun getLeveRange(): Range<Int> {
    if (!levelFormat.enabled) {
      return Range(-1, -1)
    }
    val start =
      timestampFormat.width() +
        processThreadFormat.width() +
        tagFormat.width() +
        appNameFormat.width() +
        processNameFormat.width()
    return Range(start, start + levelFormat.width() - 1)
  }
}
