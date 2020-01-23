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

import com.android.tools.idea.rendering.clearTrackedAllocations
import com.android.tools.idea.rendering.notDisposedRenderTasks
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors

/**
 * [TestRule] that verifies that all [RenderTask] have been properly de-allocated
 */
class RenderTaskLeakCheckRule : TestRule {
  override fun apply(base: Statement,
                     description: Description): Statement {
    return object : Statement() {
      @Throws(Throwable::class)
      override fun evaluate() {
        clearTrackedAllocations()
        try {
          base.evaluate()
        }
        finally {
          if (notDisposedRenderTasks().count() != 0) {
            // Give tasks the opportunity to complete the dispose
            Thread.sleep(TimeUnit.SECONDS.toMillis(1))
          }

          notDisposedRenderTasks()
            .forEach { stackTrace: List<StackTraceElement> ->
              val stackTraceString = stackTrace.stream()
                .map { element: StackTraceElement -> "\t\t" + element }
                .collect(Collectors.joining("\n"))
              throw IllegalStateException(
                "Render task not released. Allocated at \n$stackTraceString")
            }
        }
      }
    }
  }
}