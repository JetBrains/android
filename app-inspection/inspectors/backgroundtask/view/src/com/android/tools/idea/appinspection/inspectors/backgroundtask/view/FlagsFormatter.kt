/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.appinspection.inspectors.backgroundtask.view

/** Formats Android Flags as a String */
internal object FlagsFormatter {
  private class Flag(val name: String, val value: Int)

  // PendingIntent flags are defined by their bit pos in Android sources
  private val PENDING_INTENT_FLAGS =
    listOf(
      Flag("FLAG_ONE_SHOT", 1 shl 30),
      Flag("FLAG_NO_CREATE", 1 shl 29),
      Flag("FLAG_CANCEL_CURRENT", 1 shl 28),
      Flag("FLAG_UPDATE_CURRENT", 1 shl 27),
      Flag("FLAG_IMMUTABLE", 1 shl 26),
      Flag("FLAG_MUTABLE", 1 shl 25),
      Flag("FLAG_ALLOW_UNSAFE_IMPLICIT_INTENT", 1 shl 24),
    )

  // Intent flags are defined by their bit value in Android sources
  private val INTENT_FLAGS =
    listOf(
      Flag("FLAG_GRANT_READ_URI_PERMISSION", 0x00000001),
      Flag("FLAG_GRANT_READ_URI_PERMISSION", 0x00000001),
      Flag("FLAG_GRANT_WRITE_URI_PERMISSION", 0x00000002),
      Flag("FLAG_FROM_BACKGROUND", 0x00000004),
      Flag("FLAG_DEBUG_LOG_RESOLUTION", 0x00000008),
      Flag("FLAG_EXCLUDE_STOPPED_PACKAGES", 0x00000010),
      Flag("FLAG_INCLUDE_STOPPED_PACKAGES", 0x00000020),
      Flag("FLAG_GRANT_PERSISTABLE_URI_PERMISSION", 0x00000040),
      Flag("FLAG_GRANT_PREFIX_URI_PERMISSION", 0x00000080),
      Flag("FLAG_DIRECT_BOOT_AUTO", 0x00000100),
      Flag("FLAG_IGNORE_EPHEMERAL", -0x80000000),
      Flag("FLAG_ACTIVITY_NO_HISTORY", 0x40000000),
      Flag("FLAG_ACTIVITY_SINGLE_TOP", 0x20000000),
      Flag("FLAG_ACTIVITY_NEW_TASK", 0x10000000),
      Flag("FLAG_ACTIVITY_MULTIPLE_TASK", 0x08000000),
      Flag("FLAG_ACTIVITY_CLEAR_TOP", 0x04000000),
      Flag("FLAG_ACTIVITY_FORWARD_RESULT", 0x02000000),
      Flag("FLAG_ACTIVITY_PREVIOUS_IS_TOP", 0x01000000),
      Flag("FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS", 0x00800000),
      Flag("FLAG_ACTIVITY_BROUGHT_TO_FRONT", 0x00400000),
      Flag("FLAG_ACTIVITY_RESET_TASK_IF_NEEDED", 0x00200000),
      Flag("FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY", 0x00100000),
      Flag("FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET", 0x00080000),
      Flag("FLAG_ACTIVITY_NO_USER_ACTION", 0x00040000),
      Flag("FLAG_ACTIVITY_REORDER_TO_FRONT", 0X00020000),
      Flag("FLAG_ACTIVITY_NO_ANIMATION", 0X00010000),
      Flag("FLAG_ACTIVITY_CLEAR_TASK", 0X00008000),
      Flag("FLAG_ACTIVITY_TASK_ON_HOME", 0X00004000),
      Flag("FLAG_ACTIVITY_RETAIN_IN_RECENTS", 0x00002000),
      Flag("FLAG_ACTIVITY_LAUNCH_ADJACENT", 0x00001000),
      Flag("FLAG_ACTIVITY_MATCH_EXTERNAL", 0x00000800),
      Flag("FLAG_ACTIVITY_REQUIRE_NON_BROWSER", 0x00000400),
      Flag("FLAG_ACTIVITY_REQUIRE_DEFAULT", 0x00000200),
      Flag("FLAG_RECEIVER_REGISTERED_ONLY", 0x40000000),
      Flag("FLAG_RECEIVER_REPLACE_PENDING", 0x20000000),
      Flag("FLAG_RECEIVER_FOREGROUND", 0x10000000),
      Flag("FLAG_RECEIVER_OFFLOAD", -0x80000000),
      Flag("FLAG_RECEIVER_OFFLOAD_FOREGROUND", 0x00000800),
      Flag("FLAG_RECEIVER_NO_ABORT", 0x08000000),
      Flag("FLAG_RECEIVER_REGISTERED_ONLY_BEFORE_BOOT", 0x04000000),
      Flag("FLAG_RECEIVER_BOOT_UPGRADE", 0x02000000),
      Flag("FLAG_RECEIVER_INCLUDE_BACKGROUND", 0x01000000),
      Flag("FLAG_RECEIVER_EXCLUDE_BACKGROUND", 0x00800000),
      Flag("FLAG_RECEIVER_FROM_SHELL", 0x00400000),
      Flag("FLAG_RECEIVER_VISIBLE_TO_INSTANT_APPS", 0x00200000),
    )

  fun pendingIntentFlagsAsString(i: Int): String = PENDING_INTENT_FLAGS.asString(i)

  fun intentFlagsAsString(i: Int): String = INTENT_FLAGS.asString(i)

  private fun List<Flag>.asString(i: Int): String =
    filter { i and it.value != 0 }.joinToString { it.name }
}
