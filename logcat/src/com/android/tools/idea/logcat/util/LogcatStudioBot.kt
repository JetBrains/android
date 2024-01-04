/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.logcat.util

import com.android.tools.idea.logcat.message.LogcatMessage
import kotlin.math.min

/**
 * Given a logcat message, extracts the most relevant information as a string. This will skip the
 * header, and for crashes, will focus on the root cause of the exception in order to make it
 * shorter (and will skip various stack traces too.)
 */
internal fun LogcatMessage.extractStudioBotContent(): String {
  return extractRelevant(message) + " with tag " + header.tag
}

/** Number of stack frames to include in summary */
private const val TOP_FRAME_COUNT = 5

private fun extractRelevant(message: String): String {
  if (isCrashFrame(message)) {
    val stack = pickStack(message)
    val frames = stack.lines()
    val top = frames.subList(0, min(frames.size, TOP_FRAME_COUNT))
    return top.joinToString("\n") { it.trim() }
  }

  return message.trim()
}

/**
 * If the logcat message represents a crash, it can have a long stack trace with multiple nested
 * "Caused by" exceptions.
 *
 * We want to pick the best one.
 *
 * Rethrows makes this a little tricky. If an app is rethrowing an exception, it's the caused-by
 * exception that's interesting. But we don't necessarily just want to go the very innermost caused
 * by.
 *
 * This will find the *first* exception where the first (innermost) stack frame appears to be in the
 * system.
 */
private fun pickStack(message: String): String {
  if (!isCrashFrame(message)) {
    return message
  }
  val chains = message.split("Caused by: ")
  // Pick the first stack caused-by where the first line is from the framework
  for (trace in chains) {
    val stack = trace.indexOf("\tat ")
    if (stack != -1) {
      val start = stack + 4
      if (
        trace.startsWith("java.", start) ||
          trace.startsWith("android.", start) ||
          trace.startsWith("org.apache.", start) ||
          trace.startsWith("org.json.", start) ||
          trace.startsWith("com.google.", start) ||
          trace.startsWith("com.android.internal", start)
      ) {
        return trace
      }
    }
  }
  return message
}

private fun isCrashFrame(line: String): Boolean {
  return line.contains("\tat ")
}
