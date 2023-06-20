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
package com.android.tools.idea.rendering.classloading

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import junit.framework.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.StringWriter
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread
import kotlin.random.Random

interface LoopTestInterface {
  fun call(livenessCheck: () -> Unit)
}

class LoopTestClass : LoopTestInterface {
  var counter = 0

  fun methodCall() {
    if (Random.nextBoolean()) {
      counter--
    }
    else {
      counter++
    }
  }

  override fun call(livenessCheck: () -> Unit) {
    while (true) {
      livenessCheck()

      // Adding this code to avoid the compiler optimizing the loop away
      if (counter < 80000000) {
        counter++
      }
      else {
        counter--
      }

      methodCall()
    }
  }
}


class CooperativeInterruptTransformTest {
  /** [StringWriter] that stores the decompiled classes after they've been transformed. */
  private val afterTransformTrace = StringWriter()

  /** [StringWriter] that stores the decompiled classes before they've been transformed. */
  private val beforeTransformTrace = StringWriter()

  // This will will log to the stdout logging information that might be useful debugging failures.
  // The logging only happens if the test fails.
  @get:Rule
  val onFailureRule = object : TestWatcher() {
    override fun failed(e: Throwable?, description: Description?) {
      super.failed(e, description)

      println("\n---- Classes before transformation ----")
      println(beforeTransformTrace)
      println("\n---- Classes after transformation ----")
      println(afterTransformTrace)
    }
  }

  @Test
  fun `check cooperative interrupt`() {
    val testClassLoader = setupTestClassLoaderWithTransformation(mapOf("Test" to LoopTestClass::class.java), beforeTransformTrace,
                                                                 afterTransformTrace) { visitor ->
      CooperativeInterruptTransform(visitor, 100)
    }
    val loopTestInstance = testClassLoader.loadClass("Test").getDeclaredConstructor().newInstance() as LoopTestInterface
    // The alivenessCheck will be automatically set to true by the thread in every loop iteration.
    // This way, we can check the thread is working and not just blocked in an invalid bytecode sequence.
    val alivenessCheck = AtomicBoolean(false)
    // This lock is locked by the thread on start and will not be released unless the thread exists. We use it
    // to check if the thread has exited.
    val threadLock = ReentrantLock()
    // CountDownLatch that is used for the main thread to wait for the longThread to have started.
    val threadStartLatch = CountDownLatch(1)

    val longThread = thread(start = true) {
      try {
        threadLock.lock()
        threadStartLatch.countDown()
        loopTestInstance.call { alivenessCheck.set(true) }
      }
      catch (_: InterruptedException) {
      }
      finally {
        threadLock.unlock()
      }
    }

    // Wait for the thread start.
    threadStartLatch.await(5, TimeUnit.SECONDS)

    // The thread should never end without us calling interrupt. It should be running in a tight loop setting the
    // alivenessCheck to true all the time.
    repeat(3) {
      if (threadLock.tryLock(1, TimeUnit.SECONDS)) fail("The thread should not finish on its own")
      assertTrue(alivenessCheck.getAndSet(false))
    }

    // Interrupt the thread. When the thread finishes, it will release the lock.
    longThread.interrupt()
    if (!threadLock.tryLock(10, TimeUnit.SECONDS)) fail("The thread should finish when interrupt is called")
  }

  @Test
  fun `check class does not instrument all methods`() {
    val instrumentedChecks = mutableSetOf<String>()
    val testClassLoader = setupTestClassLoaderWithTransformation(mapOf("Test" to LoopTestClass::class.java), beforeTransformTrace,
                                                                 afterTransformTrace) { visitor ->
      CooperativeInterruptTransform(visitor, 100) { className, methodName ->
        instrumentedChecks.add("$className.$methodName")
        // We do not instrument any of the methods for this test
        false
      }
    }
    assertEquals("""
      Test.<init>
      Test.call
      Test.getCounter
      Test.methodCall
      Test.setCounter
    """.trimIndent(), instrumentedChecks.sorted().joinToString("\n"))
    val loopTestInstance = testClassLoader.loadClass("Test").newInstance() as LoopTestInterface
    val manualStopSwitch = AtomicBoolean(false)
    val alivenessCheck = AtomicBoolean(false)
    val threadLock = ReentrantLock()
    val threadStartLatch = CountDownLatch(1)

    val longThread = thread(start = true) {
      try {
        threadLock.lock()
        threadStartLatch.countDown()
        loopTestInstance.call {
          if (manualStopSwitch.get()) throw InterruptedException("Interrupted manually")
          alivenessCheck.set(true)
        }
      }
      catch (_: InterruptedException) {
      }
      finally {
        threadLock.unlock()
      }
    }

    // Wait for the thread start.
    threadStartLatch.await(5, TimeUnit.SECONDS)

    // The thread should never end without us calling interrupt. It should be running in a tight loop setting the
    // alivenessCheck to true all the time.
    repeat(6) {
      if (threadLock.tryLock(1, TimeUnit.SECONDS)) fail("The thread should not finish on its own")
      assertTrue(alivenessCheck.getAndSet(false))
      // From the third iteration, we try to interrupt the thread
      if (it > 3) longThread.interrupt()
    }
    manualStopSwitch.set(true)
    longThread.join(TimeUnit.SECONDS.toMillis(5))
  }
}