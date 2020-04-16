/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.uibuilder

import com.android.tools.idea.rendering.AllocationStackTrace
import com.android.tools.idea.rendering.DisposeStackTrace
import com.android.tools.idea.rendering.StackTraceCapture
import com.android.tools.idea.rendering.clearTrackedAllocations
import com.android.tools.idea.rendering.notDisposedRenderTasks
import com.intellij.openapi.diagnostic.Logger
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit

/**
 * [TestRule] that verifies that all [RenderTask] have been properly de-allocated
 */
class RenderTaskLeakCheckRule : TestRule {
  private fun StackTraceCapture.asString(): String =
    stackTrace.joinToString("\n") { element: StackTraceElement -> "\t\t$element" }

  override fun apply(base: Statement,
                     description: Description): Statement {
    return object : Statement() {
      @Throws(Throwable::class)
      override fun evaluate() {
        clearTrackedAllocations()
        base.evaluate()
        var wait = TimeUnit.SECONDS.toMillis(1)
        var retries = 3
        while (notDisposedRenderTasks().count() != 0 && retries-- > 0) {
          Logger.getInstance(RenderTaskLeakCheckRule::class.java).warn("Waiting for RenderTasks to be disposed. ${retries} retries left")
          // Give tasks the opportunity to complete the dispose
          Thread.sleep(wait)
          wait *= 2
        }

        val notDisposed = notDisposedRenderTasks().toList()
        if (notDisposed.isNotEmpty()) {
          val exceptionText = notDisposed.mapIndexed { index, (task, stackTrace) ->
            val type = when (stackTrace) {
              is AllocationStackTrace -> "Allocated at"
              is DisposeStackTrace -> "Scheduled for dispose but not disposed yet. Disposed scheduled at"
              else -> "Unknown"
            }
            "RenderTask[$index] (${task}) ${type}\n${stackTrace.asString()}"
          }.joinToString("\n\n")

          throw IllegalStateException(
            "${notDisposed.size} RenderTask(s) not released. \n$exceptionText")
        }
      }
    }
  }
}