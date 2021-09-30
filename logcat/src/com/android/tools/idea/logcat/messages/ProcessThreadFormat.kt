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

/**
 * Provides formatting for the process & thread ids
 */
internal enum class ProcessThreadFormat(val format: (Int, Int) -> String) {
  // According to /proc/sys/kernel/[pid_max/threads-max], the max values of pid/tid are 32768/57136
  NO_IDS({ _, _ -> "" }),
  PID({ pid: Int, _: Int -> "%-5d ".format(pid) }),
  BOTH({ pid: Int, tid: Int -> "%5d-%-5d ".format(pid, tid) }),
}