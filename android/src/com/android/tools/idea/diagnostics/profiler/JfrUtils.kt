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
package com.android.tools.idea.diagnostics.profiler

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.ThreadingAssertions
import jdk.jfr.Configuration
import jdk.jfr.FlightRecorder
import jdk.jfr.Recording
import java.io.InputStreamReader
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.ZoneId

fun isJfrAvailable() = FlightRecorder.isAvailable()

@Service
class Jfr {
  val LOG = Logger.getInstance(Jfr::class.java)
  var r: Recording? = null

  fun isProfilerActive(): Boolean {
    ThreadingAssertions.assertEventDispatchThread()
    return r != null
  }

  fun start() {
    ThreadingAssertions.assertEventDispatchThread()

    if (isProfilerActive()) {
      return
    }

    r = Recording(getJfrConfiguration())
    r?.start()
    LOG.info("JFR Started")
  }

  private fun getJfrConfiguration(): Configuration {
    val inputStream = javaClass.getResourceAsStream("/diagnostics/prodprofile.jfc") ?: return Configuration.getConfiguration("profile")
    return InputStreamReader(inputStream).use { r ->
      Configuration.create(r)
    }
  }

  fun stop() {
    ThreadingAssertions.assertEventDispatchThread()

    if (!isProfilerActive()) {
      return
    }

    dump()
    r?.stop()
    r = null
    LOG.info("JFR Stopped")
  }

  fun dump(): Path? {
    ThreadingAssertions.assertEventDispatchThread()

    if (r == null) {
      return null
    }

    val dt = LocalDateTime.now(ZoneId.of("America/Los_Angeles"))
    val path = Files.createTempFile("studio-${dt.year}-${dt.monthValue}-${dt.dayOfMonth}-${dt.hour}-${dt.minute}-${dt.second}-", ".jfr");
    r?.dump(path)
    LOG.info("Saved JFR Recording in $path")
    return path
  }
}