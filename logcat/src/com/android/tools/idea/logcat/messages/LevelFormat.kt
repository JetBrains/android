/*
 * Copyright (C) 2022 The Android Open Source Project
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

import com.android.tools.idea.logcat.message.LogLevel

/**
 * Provides formatting for the log level.
 */
internal data class LevelFormat(val enabled: Boolean = true) {
  // LevelFormat is different from the other formats because it contains text with 2 different color attributes.
  // Because of this, the semantics of format() is different. Instead of returning a string, it actually accumulates the text itself.
  // Arguable, all formatters should also do this but that's for another time.
  // TODO(aalbert): Consider changing other formatters too
  fun format(logLevel: LogLevel, textAccumulator: TextAccumulator, logcatColors: LogcatColors) {
    if (enabled) {
      textAccumulator.accumulate(
        text = " ${logLevel.priorityLetter} ",
        textAttributesKey = logcatColors.getLogLevelKey(logLevel))
      textAccumulator.accumulate(" ")
    }
  }

  fun width() = if (enabled) 4 else 0
}
