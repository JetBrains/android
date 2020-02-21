/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.profilers.perfetto.traceprocessor

import com.android.testutils.TestUtils
import com.google.common.truth.Truth
import com.intellij.openapi.util.SystemInfo
import org.junit.Assume
import org.junit.Test

class TraceProcessorDaemonManagerTest {

  @Test
  fun testSpawnAndShutdownProcess() {
    // TODO: Find proper tpd binary path when running sandboxes in Bazel.
    Assume.assumeFalse(TestUtils.runningFromBazel())

    // TODO(b/148211035): Enable for Windows after binary compilation is fixed on it.
    Assume.assumeFalse(SystemInfo.isWindows)

    val manager = TraceProcessorDaemonManager()
    Truth.assertThat(manager.processIsRunning()).isFalse()

    manager.makeSureDaemonIsRunning()
    Truth.assertThat(manager.processIsRunning()).isTrue()

    manager.dispose()
    Truth.assertThat(manager.processIsRunning()).isFalse()
  }

}