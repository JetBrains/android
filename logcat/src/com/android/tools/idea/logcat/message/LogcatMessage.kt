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
package com.android.tools.idea.logcat.message

import java.io.ObjectInput
import java.io.ObjectOutput

/**
 * A Logcat message.
 */
data class LogcatMessage(val header: LogcatHeader, val message: String) {
  override fun toString(): String {
    return "$header: $message"
  }

  fun writeExternal(out: ObjectOutput) {
    header.writeExternal(out)
    out.writeUTF(message)
  }
}

fun ObjectInput.readLogcatMessage(): LogcatMessage {
  val logcatHeader = readLogcatHeader()
  val message = readUTF()
  return LogcatMessage(logcatHeader, message)
}
