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

import com.android.tools.idea.logcat.messages.ProcessThreadFormat.Style.BOTH

/** Provides formatting for the process & thread ids */
internal data class ProcessThreadFormat(val style: Style = BOTH, val enabled: Boolean = true) {
  enum class Style(val format: (Int, Int) -> String, val width: Int) {
    /** ##### */
    PID({ pid: Int, _: Int -> "%-5d ".format(pid) }, "##### ".length),

    /** #####-##### */
    BOTH({ pid: Int, tid: Int -> "%5d-%-5d ".format(pid, tid) }, "#####-##### ".length),
  }

  fun format(pid: Int, tid: Int) = if (enabled) style.format(pid, tid) else ""

  fun width() = if (enabled) style.width else 0
}
