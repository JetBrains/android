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
package com.android.tools.idea.logcat.messages

import com.android.tools.idea.logcat.util.LOGGER
import com.android.tools.r8.retrace.ProguardMapProducer
import com.android.tools.r8.retrace.ProguardMappingSupplier
import com.android.tools.r8.retrace.Retrace
import com.android.tools.r8.retrace.RetraceCommand
import java.nio.file.Path
import kotlin.io.path.readText

internal fun RetraceCommand.Builder.rewrite(message: String): String {
  try {
    val result =
      buildString(message.length * 5) {
        Retrace.run(
          setStackTrace(message.lines())
            .setRetracedStackTraceConsumer { lines ->
              lines.forEach {
                append(it)
                append("\n")
              }
            }
            .build()
        )
        // Drop last newline
        setLength(length - 1)
      }
    if (result != message) {
      val split = result.split("\n", ignoreCase = false, limit = 2)
      assert(split.size > 1)
      return "${split[0]} [deobfuscated]\n${split[1]}"
    }
    return result
  } catch (e: Exception) {
    LOGGER.warn("Error while retracing a logcat message", e)
    return message
  }
}

internal fun createRetracer(path: Path): RetraceCommand.Builder {
  return RetraceCommand.builder()
    .setMappingSupplier(
      ProguardMappingSupplier.builder()
        .setProguardMapProducer(ProguardMapProducer.fromString(path.readText()))
        .build()
    )
}
