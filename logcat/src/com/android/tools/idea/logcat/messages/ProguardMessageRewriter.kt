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

package com.android.tools.idea.logcat.messages

import com.android.tools.idea.logcat.message.LogcatMessage
import com.android.tools.r8.retrace.Retrace
import com.android.tools.r8.retrace.RetraceCommand
import com.intellij.util.getValue
import com.intellij.util.setValue
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/** Rewrites an obfuscated stack trace using a provided mapping file */
internal class ProguardMessageRewriter {
  private var retracer by AtomicReference<RetraceCommand.Builder?>(null)

  fun loadProguardMap(path: Path) {
    val builder = createRetracer(path)
    // We prime the internal caches when we are asked to load the file rather than pay that price
    // while retracing the first exception.
    Retrace.run(builder.setStackTrace(emptyList()).setRetracedStackTraceConsumer {}.build())
    retracer = builder
  }

  fun rewrite(message: LogcatMessage): String {
    val msg = message.message
    return retracer?.rewrite(msg) ?: msg
  }
}
