/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.testing

import com.android.annotations.concurrency.UiThread
import com.android.annotations.concurrency.WorkerThread
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerHook
import com.android.tools.instrumentation.threading.agent.callback.ThreadingCheckerTrampoline
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import javax.swing.SwingUtilities

/**
 * ThreadingCheck test rule works in conjunction with a threading checker java agent and will fail
 * a test if a method annotated with a threading annotation (see [UiThread] and [WorkerThread])
 * is called on a wrong thread.
 */
class ThreadingCheckRule : TestRule {
  override fun apply(base: Statement, description: Description): Statement {
    return object : Statement() {
      override fun evaluate() {
        val hook = object : ThreadingCheckerHook {
          var hasPerformedThreadingChecks = false
          var hasThreadingViolation = false
          var errorMessage = ""

          override fun verifyOnUiThread() {
            hasPerformedThreadingChecks = true
            if (!SwingUtilities.isEventDispatchThread()) {
              hasThreadingViolation = true
              errorMessage = "Method ${getInstrumentedMethodName()} is expected to be called on EventDispatchThread."
            }
          }

          override fun verifyOnWorkerThread() {
            hasPerformedThreadingChecks = true
            if (SwingUtilities.isEventDispatchThread()) {
              hasThreadingViolation = true
              errorMessage = "Method ${getInstrumentedMethodName()} is expected to be called on a worker thread."
            }
          }

          private fun getInstrumentedMethodName(): String {
            // Instrumented method (method annotated with @WorkerThread/@UiThread) is located 4 frames up the stack trace
            return Thread.currentThread().stackTrace[4].toString()
          }
        }

        ThreadingCheckerTrampoline.installHook(hook)
        base.evaluate()
        ThreadingCheckerTrampoline.removeHook(hook)

        if (!hook.hasPerformedThreadingChecks) {
          // Either the test code doesn't run any code annotated with threading annotations or
          // threading java agent is not running.
          println("No threading checks were performed when running ${description.className}#${description.methodName} test")
        }
        if (hook.hasThreadingViolation) {
          throw RuntimeException(hook.errorMessage)
        }
      }
    }
  }
}