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
package com.google.android.tools.debugger.test.lib

import com.intellij.tests.JUnit5BazelRunner
import java.io.PrintStream
import java.util.concurrent.atomic.AtomicBoolean

private const val NO_TESTS_RUN_MSG = "No tests executed: all tests were filtered out by bucketing."
private val noTestsRun = AtomicBoolean(false)
private const val NO_TESTS_ERROR = 42 // see org.jetbrains.intellij.build.impl.TestingTasksImpl.NO_TESTS_ERROR

/**
 * A wrapper of [JUnit5BazelRunner] that modifies its behavior to suit our needs.
 *
 * * If a bad filter is provided, we want the test to fail.
 */
class BazelRunner {
  companion object {
    @JvmStatic
    fun main(args: Array<String>) {
      // If running in a shard, do not report an error if no tests are executed.
      if (System.getenv("TEST_TOTAL_SHARDS") == null) {
        System.setErr(CheckIfNoTestsRunStream)
        Runtime.getRuntime().addShutdownHook(ShutdownHook)
      }
      JUnit5BazelRunner.main(args)
    }
  }

  private object ShutdownHook : Thread() {
    override fun run() {
      if (noTestsRun.get()) {
        Runtime.getRuntime().halt(NO_TESTS_ERROR)
      }
    }
  }

  private object CheckIfNoTestsRunStream : PrintStream(System.err, true, "UTF-8") {
    override fun print(s: String) {
      checkIfNoTestsRun(s)
      super.print(s)
    }

    override fun println(s: String?) {
      checkIfNoTestsRun(s)
      super.println(s)
    }

    override fun write(buf: ByteArray, off: Int, len: Int) {
      checkIfNoTestsRun(String(buf, off, len))
      super.write(buf, off, len)
    }
  }
}

private fun checkIfNoTestsRun(str: String?) {
  if (str != null && str.contains(NO_TESTS_RUN_MSG)) {
    noTestsRun.set(true)
  }
}
