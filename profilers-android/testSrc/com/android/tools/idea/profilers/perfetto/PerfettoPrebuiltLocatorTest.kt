/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto

import com.google.common.truth.Truth.assertThat
import com.intellij.openapi.util.SystemInfo
import org.junit.Test

class PerfettoPrebuiltLocatorTest {

  @Test
  fun testGetExecutablePathForTraceconv() {
    val path = PerfettoPrebuiltLocator.getExecutablePath("traceconv")
    assertThat(path).isNotEmpty()
    if (SystemInfo.isWindows) {
      assertThat(path).endsWith("traceconv.exe")
    } else {
      assertThat(path).endsWith("traceconv")
    }
  }

  @Test
  fun testGetExecutablePathForTraceProcessorDaemon() {
    val path =
      PerfettoPrebuiltLocator.getExecutablePath(
        binaryName = "trace_processor_daemon",
        devDirName = "trace-processor-daemon",
        releaseDirName = "trace_processor_daemon",
      )
    assertThat(path).isNotEmpty()
    if (SystemInfo.isWindows) {
      assertThat(path).endsWith("trace_processor_daemon.exe")
    } else {
      assertThat(path).endsWith("trace_processor_daemon")
    }
  }
}
