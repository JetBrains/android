/*
 * Copyright (C) 2025 The Android Open Source Project
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
package com.android.tools.idea.adb

enum class AdbServerLogLevel(val displayText: String, val enabledTags: String) {
  // See all tags in
  // https://android.googlesource.com/platform/packages/modules/adb/+/refs/heads/master/docs/user/adb.1.md#environment-variables
  // The default uses all tags except [sockets, packets, rwx, fdevent].
  MINIMAL(
    "minimal",
    "adb,usb,sync,sysdeps,transport,jdwp,services,auth,shell,incremental,mdns,mdns-stack",
  ),
  FULL("full", "all"),
  DISABLED("disabled", "");

  override fun toString(): String {
    return displayText
  }

  companion object {

    @JvmStatic
    fun fromDisplayText(text: String): AdbServerLogLevel {
      AdbServerLogLevel.entries.forEach {
        if (it.displayText == text) {
          return it
        }
      }
      throw IllegalArgumentException("No AdbServerLogLevel for $text")
    }
  }
}
