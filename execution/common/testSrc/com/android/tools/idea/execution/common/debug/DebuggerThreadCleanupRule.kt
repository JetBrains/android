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
package com.android.tools.idea.execution.common.debug

import com.android.fakeadbserver.FakeAdbServer
import com.google.common.base.Stopwatch
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

/**
 * A test rule that ensures that "JDI. *" debugger threads terminate before the test completes.
 *
 * This is to prevent thread leaked errors.
 *
 * **NOTE**: This rule needs to be applied in the correct order relative to any project and fake ADB rules. A correct application order
 * should result in the project rule being outer to the fake ADB rule and the fake ADB rule being outer to the `DebuggerThreadCleanupRule`.
 *
 * Sample:
 *
 * ```
 *   @get:Rule(order = 0)
 *   val projectRule = ProjectRule()
 *
 *   @get:Rule(order = 1)
 *   val fakeAdbRule: FakeAdbTestRule = FakeAdbTestRule()
 *
 *   @get:Rule(order = 2)
 *   val debuggerThreadCleanupRule = DebuggerThreadCleanupRule { fakeAdbRule.server }
 *
 * ```
 */
class DebuggerThreadCleanupRule(private val fakeAdbServer: () -> FakeAdbServer): TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return object: Statement() {
      override fun evaluate() {
        try {
          base.evaluate()
        }
        finally {
          stopFakeAdbAndWaitForDebuggerThreadsToTerminate(fakeAdbServer())
        }
      }
    }
  }
}

/**
 * Stops the [fakeAdbServer] and waits until all "JDI .*" debugger threads terminate.
 */
fun stopFakeAdbAndWaitForDebuggerThreadsToTerminate(fakeAdbServer: FakeAdbServer) {
  fakeAdbServer.stop()
  fakeAdbServer.awaitServerTermination(1, TimeUnit.SECONDS)

  val rootGroup = generateSequence(Thread.currentThread().threadGroup) { it.parent }.last()
  val threads = arrayOfNulls<Thread>(rootGroup.activeCount())
  val n = rootGroup.enumerate(threads)
  val jdiThreads = threads.take(n).filterNotNull().filter { it.name.startsWith("JDI ") }

  if (jdiThreads.any()) {
    println("Waiting for JDI threads to terminate...");

    fun anyRunningDebuggerThreads(): Boolean {
      val activeThreads = jdiThreads.filter { it.isAlive }
      for (activeThread in activeThreads) {
        println("Still waiting for ${activeThread.name} thread to terminate...")
      }
      return activeThreads.any()
    }

    val stopwatch = Stopwatch.createStarted()
    do {
      Thread.sleep(50)
      jdiThreads.filter { it.isAlive }.forEach { it.interrupt() }
    } while (stopwatch.elapsed(TimeUnit.SECONDS) < 2 && anyRunningDebuggerThreads())
    if (anyRunningDebuggerThreads()) {
      println("Giving up waiting ...");
    } else {
      println("Done waiting for JDI threads to terminate.")
    }
  }
}

