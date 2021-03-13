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

import com.android.tools.profilers.FakeFeatureTracker
import com.android.utils.Pair
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.BufferedReader
import java.io.StringReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class TraceProcessorDaemonManagerTest {

  private val fakeTicker = FakeTicker(10, TimeUnit.MILLISECONDS)
  private val fakeFeatureTracker = FakeFeatureTracker()

  @Test
  fun `spawn and shutdown daemon`() {
    val manager = TraceProcessorDaemonManager(fakeTicker)
    assertThat(manager.processIsRunning()).isFalse()

    manager.makeSureDaemonIsRunning(fakeFeatureTracker)
    assertThat(manager.processIsRunning()).isTrue()

    manager.dispose()
    assertThat(manager.processIsRunning()).isFalse()

    assertThat(fakeFeatureTracker.traceProcessorManagerMetrics).containsExactly(Pair.of(true, 10L))
  }

  @Test
  fun `output listener - detects running`() {
    val source = BufferedReader(StringReader("Some Message\nServer listening on 127.0.0.1:40000\n"))
    val listener = TraceProcessorDaemonManager.TPDStdoutListener(source)

    val executor = Executors.newSingleThreadExecutor()
    val listenerRunnable = executor.submit(RunListener(listener))

    listener.waitUntilTerminated(0)
    val port = listener.selectedPort
    val status = listener.status

    assertThat(port).isEqualTo(40000)
    assertThat(status).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.END_OF_STREAM)

    listenerRunnable.get()
    assertThat(listener.selectedPort).isEqualTo(40000)
    assertThat(listener.status).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.END_OF_STREAM)
  }

  @Test
  fun `output listener - detects failed`() {
    val source = BufferedReader(StringReader("Some Message\nServer failed to start. A port number wasn't bound.\n"))
    val listener = TraceProcessorDaemonManager.TPDStdoutListener(source)

    val executor = Executors.newSingleThreadExecutor()
    val listenerRunnable = executor.submit(RunListener(listener))

    listener.waitForStatusChangeOrTerminated(0)
    val port = listener.selectedPort
    val status = listener.status

    assertThat(port).isEqualTo(0)
    assertThat(status).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.FAILED)

    listenerRunnable.get()
    assertThat(listener.selectedPort).isEqualTo(0)
    assertThat(listener.status).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.FAILED)
  }

  @Test
  fun `output listener - detects end of stream`() {
    val source = BufferedReader(StringReader("Some Message\nStream will end\n"))
    val listener = TraceProcessorDaemonManager.TPDStdoutListener(source)

    val executor = Executors.newSingleThreadExecutor()
    val listenerRunnable = executor.submit(RunListener(listener))

    listener.waitForStatusChangeOrTerminated(0)
    val port = listener.selectedPort
    val status = listener.status

    assertThat(port).isEqualTo(0)
    assertThat(status).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.END_OF_STREAM)

    listenerRunnable.get()
    assertThat(listener.selectedPort).isEqualTo(0)
    assertThat(listener.status).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.END_OF_STREAM)
  }

  @Test
  fun `output listener - timeout on blocked reader`() {
    val reader = LockExposedStringReader("Will Never Read This\nStream will end\n")
    val source = BufferedReader(reader)
    val listener = TraceProcessorDaemonManager.TPDStdoutListener(source)

    val executor = Executors.newFixedThreadPool(2)
    val lockHolder = LockHolder(reader.getLock())
    val holderRunnable = executor.submit(lockHolder)
    lockHolder.waitUntilLocked() // Wait until it's locked before firing the listener.
    val listenerRunnable = executor.submit(RunListener(listener))

    // As holderRunnable is holding reader's lock, so the reader can't return and will timeout.
    listener.waitForStatusChangeOrTerminated(5000)
    val port = listener.selectedPort
    val status = listener.status

    assertThat(port).isEqualTo(0)
    assertThat(status).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.STARTING)

    // Release the reader lock and let the thread finish.
    lockHolder.unlock()
    holderRunnable.get()
    listenerRunnable.get()

    // The listener actually finished with END OF STREAM after we release the lock, as expected.
    assertThat(listener.status).isEqualTo(TraceProcessorDaemonManager.DaemonStatus.END_OF_STREAM)
  }

  private class RunListener(private val listener: TraceProcessorDaemonManager.TPDStdoutListener): Runnable {
    override fun run() {
      listener.run()
    }
  }

  // Holds {@code lock} until unlock() is called.
  private class LockHolder(private val lock: Any): Runnable {
    private val internalLock = Object()
    private var locked = false

    override fun run() {
      synchronized(lock) {
        synchronized(internalLock) {
          locked = true
          // Notify here in case someone is waiting for waitUntilLocked
          internalLock.notifyAll()
          // Then wait, until we get a signal to unlock
          internalLock.wait()
          locked = false
        }
      }
    }

    fun unlock() {
      synchronized(internalLock) {
        internalLock.notifyAll()
      }
    }

    fun waitUntilLocked() {
      synchronized(internalLock) {
        while (!locked) internalLock.wait()
      }
    }
  }

  // Impl of StringReader with internal lock exposed, so we can test timeout logic.
  private class LockExposedStringReader(str: String): StringReader(str) {
    fun getLock(): Any = this.lock
  }

}