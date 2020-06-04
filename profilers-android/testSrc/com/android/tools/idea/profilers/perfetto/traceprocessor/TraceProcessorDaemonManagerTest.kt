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
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class TraceProcessorDaemonManagerTest {

  @Test
  fun `spawn and shutdown daemon`() {
    // TODO: Find proper tpd binary path when running sandboxes in Bazel.
    Assume.assumeFalse(TestUtils.runningFromBazel())

    val manager = TraceProcessorDaemonManager()
    assertThat(manager.processIsRunning()).isFalse()

    manager.makeSureDaemonIsRunning()
    assertThat(manager.processIsRunning()).isTrue()

    manager.dispose()
    assertThat(manager.processIsRunning()).isFalse()
  }

  @Test
  fun `output listener - detects running`() {
    val source = BufferedReader(StringReader("Server listening on 127.0.0.1:40000\n"))
    val listener = TraceProcessorDaemonManager.TPDStdoutListener(source)

    val executor = Executors.newSingleThreadExecutor()
    val result = executor.submit(GetStatusCallable(listener))

    listener.run()
    assertThat(listener.selectedPort).isEqualTo(40000)
    assertThat(result.get()).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.RUNNING)
  }

  @Test
  fun `output listener - detects failed`() {
    val source = BufferedReader(StringReader("Server failed to start. A port number wasn't bound.\n"))
    val listener = TraceProcessorDaemonManager.TPDStdoutListener(source)

    val executor = Executors.newSingleThreadExecutor()
    val result = executor.submit(GetStatusCallable(listener))

    listener.run()
    assertThat(listener.selectedPort).isEqualTo(0)
    assertThat(result.get()).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.FAILED)
  }

  private class GetStatusCallable(private val listener: TraceProcessorDaemonManager.TPDStdoutListener)
    : Callable<TraceProcessorDaemonManager.DaemonStatus> {

    override fun call(): TraceProcessorDaemonManager.DaemonStatus {
      listener.waitForRunningOrFailed()
      return listener.status
    }
  }

}