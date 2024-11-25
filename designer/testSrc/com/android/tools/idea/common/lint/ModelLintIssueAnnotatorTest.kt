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
package com.android.tools.idea.common.lint

import com.android.tools.idea.common.fixtures.ComponentDescriptor
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.runInEdtAndGet
import java.util.LinkedList
import java.util.concurrent.Executor
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.times
import org.mockito.Mockito.`when`
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify

class ModelLintIssueAnnotatorTest {

  @Rule @JvmField val rule = AndroidProjectRule.inMemory()

  @Test
  fun testMultipleRequests() {
    val builder =
      NlModelBuilderUtil.model(
        rule,
        "res/layout",
        "my_layout.xml",
        ComponentDescriptor("FrameLayout").matchParentHeight().matchParentWidth(),
      )
    val model = runInEdtAndGet { builder.build() }

    val surface = model.surface
    val executor = CountableExecutor()

    val annotator = ModelLintIssueAnnotator(surface, executor)

    annotator.annotateRenderInformationToLint(model)
    assertEquals(1, executor.getTaskCount())

    executor.pause()
    annotator.annotateRenderInformationToLint(model)
    // Now there is a running task in annotator.
    // Calling ModelLintIssueAnnotator.annotateRenderInformationToLint doesn't add the new task into
    // Executor.
    annotator.annotateRenderInformationToLint(model)
    annotator.annotateRenderInformationToLint(model)
    executor.resume()
    // All tasks are executed, but only the first two added ones return early and do not trigger a
    // surface repaint.
    assertEquals(4, executor.getTaskCount())
    verify(surface, times(2)).repaint()
  }

  @Test
  fun testClearTaskWhenExceptionHappens() {
    val model = mock<NlModel>()
    `when`(model.file).thenAnswer { throw Exception() }
    Disposer.register(rule.testRootDisposable, model)
    val surface = mock<DesignSurface<*>>()
    `when`(surface.repaint()).then {}

    val executor = CountableExecutor()
    val annotator = ModelLintIssueAnnotator(surface, executor)

    runAnnotatorAndIgnoreException(annotator, model)
    assertEquals(1, executor.getTaskCount())
    // An exception has happened when executing.
    // The annotator can still add more task into executor because the pending task is cleared.
    runAnnotatorAndIgnoreException(annotator, model)
    assertEquals(2, executor.getTaskCount())
    runAnnotatorAndIgnoreException(annotator, model)
    assertEquals(3, executor.getTaskCount())
  }

  private fun runAnnotatorAndIgnoreException(annotator: ModelLintIssueAnnotator, model: NlModel) {
    try {
      annotator.annotateRenderInformationToLint(model)
    } catch (ignore: Throwable) {}
  }
}

private class CountableExecutor : Executor {

  private var paused = false
  private var count = 0
  private val tasks = LinkedList<Runnable>()

  override fun execute(command: Runnable) {
    tasks.addLast(command)
    if (!paused) {
      flushTasks()
    }
  }

  fun pause() {
    paused = true
  }

  fun resume() {
    paused = false
    flushTasks()
  }

  private fun flushTasks() {
    while (tasks.isNotEmpty()) {
      try {
        tasks.pop().run()
      } catch (_: Exception) {} finally {
        count++
      }
    }
  }

  fun getTaskCount(): Int {
    return count
  }
}
