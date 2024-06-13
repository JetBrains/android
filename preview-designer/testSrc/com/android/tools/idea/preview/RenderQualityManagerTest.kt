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
package com.android.tools.idea.preview

import com.android.ide.common.rendering.api.Result
import com.android.testutils.MockitoKt.any
import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.tools.editor.PanZoomListener
import com.android.tools.idea.DesignSurfaceTestUtil.createZoomControllerFake
import com.android.tools.idea.common.surface.SceneView
import com.android.tools.idea.testing.disposable
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.rendering.RenderResult
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ProjectRule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.awt.Dimension
import java.awt.Point
import java.awt.Rectangle
import java.util.concurrent.CountDownLatch
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Class used for bundling together some data used across the tests in [RenderQualityManagerTest].
 * This class is conceptually acting as a "preview tool" using a RenderQualityManager.
 */
private class TestPreviewTool {
  var errorMargin: Float = 0.1f
  var targetQuality: Float = 1f
  var currentQuality: Float = 1f
  var qualityChangeNeededLatch = CountDownLatch(0)
  val debounceTime: Long = 500

  fun qualityChangeMightBeNeeded() {
    assertTrue(qualityChangeNeededLatch.count > 0, "Unexpected qualityChangeMightBeNeeded")
    qualityChangeNeededLatch.countDown()
  }
}

private class TestRenderQualityPolicy(private val tool: TestPreviewTool) : RenderQualityPolicy {
  override val acceptedErrorMargin: Float
    get() = tool.errorMargin

  // Note that the debounce time cannot change as it is only used in the initialization
  // of a DefaultRenderQualityManager
  override val debounceTimeMillis: Long = tool.debounceTime

  override fun getTargetQuality(scale: Double, isVisible: Boolean): Float {
    return tool.targetQuality
  }
}

class RenderQualityManagerTest {
  @JvmField @Rule val projectRule = ProjectRule()

  private lateinit var tool: TestPreviewTool
  private lateinit var qualityManager: DefaultRenderQualityManager

  private lateinit var sceneViewMock: SceneView
  private val sceneViewRectangle = Rectangle(Point(10, 10), Dimension(10, 10))
  private lateinit var sceneManagerMock: LayoutlibSceneManager

  private lateinit var surfaceMock: NlDesignSurface
  private lateinit var panZoomListener: PanZoomListener
  private lateinit var listenerInitialization: CountDownLatch
  private var scrollRectangle: Rectangle? = null

  @Before
  fun setUp() {
    listenerInitialization = CountDownLatch(1)
    tool = TestPreviewTool()

    sceneViewMock = mock<SceneView>()
    sceneManagerMock = mock<LayoutlibSceneManager>()
    whenever(sceneManagerMock.sceneViews).then {
      return@then listOf(sceneViewMock)
    }
    whenever(sceneManagerMock.lastRenderQuality).then {
      return@then tool.currentQuality
    }

    surfaceMock = mock<NlDesignSurface>()
    whenever(surfaceMock.addPanZoomListener(any(PanZoomListener::class.java))).then {
      panZoomListener = it.getArgument(0)
      listenerInitialization.countDown()
      return@then Unit
    }
    whenever(surfaceMock.findSceneViewRectangles()).then {
      return@then mapOf(Pair(sceneViewMock, sceneViewRectangle))
    }
    whenever(surfaceMock.currentScrollRectangle).then {
      return@then scrollRectangle
    }
    whenever(surfaceMock.zoomController).thenReturn(createZoomControllerFake(1.0))

    Disposer.register(projectRule.disposable, surfaceMock)
    Disposer.register(projectRule.disposable, sceneManagerMock)

    qualityManager =
      DefaultRenderQualityManager(surfaceMock, TestRenderQualityPolicy(tool)) {
        tool.qualityChangeMightBeNeeded()
      }
    listenerInitialization.await()
  }

  @Test
  fun testDebounceTime() = runBlocking {
    // Only 1 change of quality should be triggered when many changes happen but the
    // debounce time is larger than the time passed between those changes.
    tool.qualityChangeNeededLatch = CountDownLatch(1)
    repeat(20) {
      // Alternate between both notifications as they should have the same effect
      if (it % 2 == 0) panZoomListener.zoomChanged(1.0, 1.0)
      else panZoomListener.panningChanged(null)
      delay(tool.debounceTime / 5)
    }
    tool.qualityChangeNeededLatch.await()

    // Every change should trigger a change of quality when the time between them is
    // larger than the debounce time.
    tool.qualityChangeNeededLatch = CountDownLatch(2)
    repeat(2) {
      // Alternate between both notifications as they should have the same effect
      if (it % 2 == 0) panZoomListener.zoomChanged(1.0, 1.0)
      else panZoomListener.panningChanged(null)
      delay(tool.debounceTime * 5)
    }

    tool.qualityChangeNeededLatch.await()
  }

  @Test
  fun testErrorMargin() = runBlocking {
    tool.errorMargin = 0.2f
    tool.currentQuality = 0.5f

    tool.targetQuality = 0.75f
    assertTrue(qualityManager.needsQualityChange(sceneManagerMock))

    tool.targetQuality = 0.25f
    assertTrue(qualityManager.needsQualityChange(sceneManagerMock))

    tool.targetQuality = 0.65f
    assertFalse(qualityManager.needsQualityChange(sceneManagerMock))

    tool.targetQuality = 0.35f
    assertFalse(qualityManager.needsQualityChange(sceneManagerMock))
  }

  @Test
  fun testCancelledExceptionTriggersQualityChange() = runBlocking {
    tool.errorMargin = 0.2f
    tool.currentQuality = 0.5f

    tool.targetQuality = 0.75f
    val renderCancelledResult = mock<RenderResult>()
    val mockResult = mock<Result>()
    whenever(mockResult.isSuccess).thenReturn(false)
    whenever(mockResult.exception).thenReturn(CancellationException())
    whenever(renderCancelledResult.renderResult).thenReturn(mockResult)
    whenever(sceneManagerMock.renderResult).thenReturn(renderCancelledResult)
    assertTrue(qualityManager.needsQualityChange(sceneManagerMock))
  }

  @Test
  fun testCancelledExceptionTriggersQualityChange_EvenForSameQuality() = runBlocking {
    tool.errorMargin = 0.2f
    tool.currentQuality = 0.5f

    tool.targetQuality = 0.5f
    val renderCancelledResult = mock<RenderResult>()
    val mockResult = mock<Result>()
    whenever(mockResult.isSuccess).thenReturn(false)
    whenever(mockResult.exception).thenReturn(CancellationException())
    whenever(renderCancelledResult.renderResult).thenReturn(mockResult)
    whenever(sceneManagerMock.renderResult).thenReturn(renderCancelledResult)
    assertTrue(qualityManager.needsQualityChange(sceneManagerMock))
  }

  @Test
  fun testErrorDoesNotTriggerQualityChange() = runBlocking {
    tool.errorMargin = 0.2f
    tool.currentQuality = 0.5f

    tool.targetQuality = 0.75f
    val renderCancelledResult = mock<RenderResult>()
    val mockResult = mock<Result>()
    whenever(mockResult.isSuccess).thenReturn(false)
    whenever(mockResult.exception).thenReturn(IllegalStateException())
    whenever(renderCancelledResult.renderResult).thenReturn(mockResult)
    whenever(sceneManagerMock.renderResult).thenReturn(renderCancelledResult)
    assertFalse(qualityManager.needsQualityChange(sceneManagerMock))
  }

  @Test
  fun testPause() = runBlocking {
    tool.errorMargin = 0.2f
    tool.currentQuality = 0.5f

    tool.targetQuality = 0.75f
    assertTrue(qualityManager.needsQualityChange(sceneManagerMock))

    qualityManager.pause()
    assertFalse(qualityManager.needsQualityChange(sceneManagerMock))

    qualityManager.resume()
    assertTrue(qualityManager.needsQualityChange(sceneManagerMock))
  }

  // Regression test for b/336947005
  @Test
  fun testSceneViewChangeIsDetected() = runBlocking {
    qualityManager.needsQualityChange(sceneManagerMock)
    assertTrue(qualityManager.sceneViewRectanglesContainsForTest(sceneViewMock))

    sceneViewMock = mock<SceneView>()
    assertFalse(qualityManager.sceneViewRectanglesContainsForTest(sceneViewMock))

    // when checking quality targets, the new sceneView should be detected and loaded into the map
    qualityManager.needsQualityChange(sceneManagerMock)
    assertTrue(qualityManager.sceneViewRectanglesContainsForTest(sceneViewMock))
  }
}
