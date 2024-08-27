/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene

import com.android.SdkConstants
import com.android.ide.common.rendering.api.Result
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.common.surface.LayoutScannerConfiguration
import com.android.tools.idea.concurrency.AndroidDispatchers.workerThread
import com.android.tools.idea.rendering.StudioEnvironmentContext
import com.android.tools.idea.testing.AndroidProjectRule
import com.android.tools.idea.uibuilder.NlModelBuilderUtil.model
import com.android.tools.idea.uibuilder.property.testutils.ComponentUtil.component
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.rendering.RenderLogger
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderResultStats
import com.android.tools.rendering.RenderService.RenderTaskBuilder
import com.android.tools.rendering.RenderTask
import com.android.tools.rendering.imagepool.ImagePool
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.EdtRule
import com.intellij.testFramework.RuleChain
import com.intellij.util.concurrency.EdtExecutorService
import java.awt.Dimension
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertFails
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LayoutlibSceneRendererTest {
  private val projectRule = AndroidProjectRule.withSdk().initAndroid(true)

  @get:Rule val chain = RuleChain(projectRule, EdtRule())
  private lateinit var renderer: LayoutlibSceneRenderer
  private val renderTaskBuilderMock = mock<RenderTaskBuilder>()
  private val renderTaskMock = mock<RenderTask>()
  private val taskInflateCount = AtomicInteger(0)
  private val taskRenderCount = AtomicInteger(0)
  private var inflateLatch = CountDownLatch(0)
  private lateinit var simulatedInflateResult: RenderResult
  private lateinit var simulatedRenderResult: RenderResult

  @Before
  fun setUp() {
    simulatedInflateResult = createRenderResult(Result.Status.SUCCESS)
    simulatedRenderResult = createRenderResult(Result.Status.SUCCESS)
    whenever(renderTaskMock.render()).then {
      taskRenderCount.incrementAndGet()
      CompletableFuture.completedFuture(simulatedRenderResult)
    }
    whenever(renderTaskMock.inflate()).then {
      inflateLatch.await(10, TimeUnit.SECONDS)
      taskInflateCount.incrementAndGet()
      CompletableFuture.completedFuture(simulatedInflateResult)
    }
    val model =
      model(
          projectRule,
          "layout",
          "layout.xml",
          component(SdkConstants.TAG_LAYOUT).withBounds(0, 0, 1000, 1000),
        )
        .build()
    renderer =
      LayoutlibSceneRenderer(
        projectRule.testRootDisposable,
        EdtExecutorService.getInstance(),
        model,
        model.surface as NlDesignSurface,
        LayoutScannerConfiguration.DISABLED,
      )
    whenever(renderTaskBuilderMock.build(any()))
      .thenReturn(CompletableFuture.completedFuture(renderTaskMock))
    renderer.sceneRenderConfiguration.setRenderTaskBuilderWrapperForTest { renderTaskBuilderMock }
  }

  @After
  fun tearDown() {
    Disposer.dispose(renderer)
  }

  @Test
  fun testInflateAndRender() = runBlocking {
    // First render, inflation and render should happen
    renderer.requestRender(trigger = null)
    delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskRenderCount.get() == 1 }
    assertEquals("expected inflation on first render but didn't happen", 1, taskInflateCount.get())

    // Re-render, inflation shouldn't happen but render should
    renderer.requestRender(trigger = null)
    delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskRenderCount.get() == 2 }
    assertEquals("unexpected inflation on re-render", 1, taskInflateCount.get())

    // Re-render with forceReinflate, inflation and render should happen
    renderer.sceneRenderConfiguration.needsInflation.set(true)
    renderer.requestRender(trigger = null)
    delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskRenderCount.get() == 3 }
    assertEquals("expected inflation on forceInflate but didn't happen", 2, taskInflateCount.get())

    // Re-render with forceReinflate, inflation should happen, but render shouldn't because of
    // inflation error
    simulatedInflateResult = createRenderResult(Result.Status.ERROR_INFLATION)
    renderer.sceneRenderConfiguration.needsInflation.set(true)
    renderer.requestRender(trigger = null)
    assertFails {
      delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskRenderCount.get() == 4 }
    }
    assertEquals(
      "expected inflation on forceInflate but didn't happen (2)",
      3,
      taskInflateCount.get(),
    )
    assertEquals("expected render not to happen after inflation failure", 3, taskRenderCount.get())

    // Re-render after inflation error, inflation should happen without forcing it and render
    // should also happen
    simulatedInflateResult = createRenderResult(Result.Status.SUCCESS)
    renderer.requestRender(trigger = null)
    delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskRenderCount.get() == 4 }
    assertEquals("expected inflation but didn't happen", 4, taskInflateCount.get())
  }

  @Test
  fun testRequestsAreConflated(): Unit = runBlocking {
    blockInflationAndRequestRender()
    // Add 5 requests
    renderer.sceneRenderConfiguration.needsInflation.set(true)
    repeat(5) { renderer.requestRender(trigger = null) }
    // inflation should still be blocked
    assertFails {
      delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskInflateCount.get() > 0 }
    }
    // Now unblock the inflation, 2 requests should be processed (the first
    // one above, and one from the 5 added later)
    unblockInflation()
    assertFails {
      delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskInflateCount.get() > 2 }
    }
    assertEquals(2, taskInflateCount.get())
    assertEquals(2, taskRenderCount.get())
  }

  @Test
  fun testDeactivateCancelsPendingRenders() = runBlocking {
    blockInflationAndRequestRender()
    // Add 5 requests
    renderer.sceneRenderConfiguration.needsInflation.set(true)
    repeat(5) { renderer.requestRender(trigger = null) }
    // inflation should still be blocked
    assertFails {
      delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskInflateCount.get() > 0 }
    }
    // deactivate the renderer now, any pending render should not be executed, but the running
    // render is not cancelled.
    renderer.deactivate()
    // Now unblock the inflation
    unblockInflation()
    assertFails {
      delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskInflateCount.get() > 1 }
    }
    // Deactivation should've cancelled the pending renders. The running render was in the middle
    // of inflation when deactivated, meaning that the inflation is not being cancelled, but the
    // following renderTask.render shouldn't execute
    assertEquals(1, taskInflateCount.get())
    assertEquals(0, taskRenderCount.get())
  }

  @Test
  fun testRenderAndWait() = runBlocking {
    blockInflationAndRequestRender()
    val requestRenderAndWaitJob =
      launch(workerThread) { renderer.requestRenderAndWait(trigger = null) }
    delay(1000)
    renderer.sceneRenderConfiguration.needsInflation.set(true)
    renderer.requestRender(trigger = null)

    // Inflation should still be blocked, and the renderAndWait shouldn't have finished
    assertTrue(requestRenderAndWaitJob.isActive)
    assertFails {
      delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskInflateCount.get() > 0 }
    }
    // now unblock and wait for the wait job to finish
    unblockInflation()
    delayUntilCondition(delayPerIterationMs = 500, 3.seconds) {
      requestRenderAndWaitJob.isCompleted
    }
    // Inflate should be 2 because forceReinflate was called after the renderAndWaitJob started,
    // but inflation was blocked at the moment and so the request processed later should have
    // re-inflated
    assertEquals(2, taskInflateCount.get())
    assertEquals(2, taskRenderCount.get())
  }

  private fun blockInflationAndRequestRender() = runBlocking {
    inflateLatch = CountDownLatch(1)
    renderer.sceneRenderConfiguration.needsInflation.set(true)
    renderer.requestRender(trigger = null)
    delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { renderer.isRendering() }
    assertFails {
      delayUntilCondition(delayPerIterationMs = 500, 3.seconds) { taskInflateCount.get() > 0 }
    }
  }

  private fun unblockInflation() {
    assertEquals("expected inflateLatch count to be 1", 1L, inflateLatch.count)
    inflateLatch.countDown()
  }

  private fun createRenderResult(result: Result.Status) =
    RenderResult(
      { StudioEnvironmentContext(projectRule.module).getOriginalFile(projectRule.fixture.file) },
      projectRule.project,
      { projectRule.module },
      RenderLogger(projectRule.project),
      null,
      false,
      result.createResult(),
      ImmutableList.of(),
      ImmutableList.of(),
      ImagePool.NULL_POOLED_IMAGE,
      ImmutableMap.of(),
      ImmutableMap.of(),
      null,
      Dimension(0, 0),
      RenderResultStats.EMPTY,
    )
}
