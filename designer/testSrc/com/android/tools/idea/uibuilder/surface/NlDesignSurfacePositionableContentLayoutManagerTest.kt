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
package com.android.tools.idea.uibuilder.surface

import com.android.testutils.MockitoKt.mock
import com.android.testutils.MockitoKt.whenever
import com.android.testutils.delayUntilCondition
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.surface.layout.TestPositionableContent
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.ApplicationRule
import kotlin.test.assertFalse
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.any

class NlDesignSurfacePositionableContentLayoutManagerTest {
  @get:Rule val applicationRule = ApplicationRule()

  private lateinit var parentDisposable: Disposable

  @Before
  fun setUp() {
    parentDisposable = Disposer.newDisposable()
  }

  @After
  fun tearDown() {
    Disposer.dispose(parentDisposable)
  }

  @Test
  fun updateIsCancelled() {
    runBlocking(uiThread) {
      val disposable = Disposer.newDisposable()
      val mockedSurface = mock<NlDesignSurface>()
      whenever(mockedSurface.onLayoutUpdated(any())).then {}

      val layoutManager1 = GridSurfaceLayoutManager(0, 0, 0, 0)
      val layoutManager2 = GridSurfaceLayoutManager(0, 0, 0, 0)
      val contentLayoutManager =
        NlDesignSurfacePositionableContentLayoutManager(
          mockedSurface,
          disposable,
          SurfaceLayoutOption("", layoutManager1),
        )
      contentLayoutManager.currentLayout.value = SurfaceLayoutOption("", layoutManager2)
      Disposer.dispose(disposable)
      assertFalse(contentLayoutManager.scope.isActive)
    }
  }

  @Test
  fun testSetLayoutManagerWillResetTheScrollPosition() {
    runBlocking(uiThread) {
      val mockedSurface = mock<NlDesignSurface>()
      var layoutUpdates = 0
      whenever(mockedSurface.onLayoutUpdated(any())).then { layoutUpdates++ }

      val layoutManager1 = GridSurfaceLayoutManager(0, 0, 0, 0)
      val layoutManager2 = GridSurfaceLayoutManager(0, 0, 0, 0)
      val contentLayoutManager =
        NlDesignSurfacePositionableContentLayoutManager(
          mockedSurface,
          parentDisposable,
          SurfaceLayoutOption("", layoutManager1),
        )
      assertEquals(0, layoutUpdates)
      contentLayoutManager.currentLayout.value = SurfaceLayoutOption("", layoutManager2)
      delayUntilCondition(250) { layoutUpdates == 1 }
      assertEquals(1, layoutUpdates)
    }
  }

  @Test
  fun testMeasurePosition() {
    runBlocking(uiThread) {
      val mockedSurface = mock<NlDesignSurface>()
      whenever(mockedSurface.onLayoutUpdated(any())).then {} // Do nothing
      val layoutManager1 = GridSurfaceLayoutManager(0, 0, 0, 0)
      val layoutManager2 = GridSurfaceLayoutManager(0, 0, 0, 0)
      val contentLayoutManager =
        NlDesignSurfacePositionableContentLayoutManager(
          mockedSurface,
          parentDisposable,
          SurfaceLayoutOption("", layoutManager1),
        )
      contentLayoutManager.currentLayout.value = SurfaceLayoutOption("", layoutManager2)

      val content1 = TestPositionableContent(width = 100, height = 100)
      val content2 = TestPositionableContent(width = 100, height = 100)
      val content3 = TestPositionableContent(width = 100, height = 100)
      val content4 = TestPositionableContent(width = 100, height = 100)
      val contents = listOf(content1, content2, content3, content4)

      // The layout of these content should be 2 x 2
      val positions =
        contentLayoutManager.getMeasuredPositionableContentPosition(contents, 200, 200)

      assertEquals(0, positions[content1]!!.x)
      assertEquals(0, positions[content1]!!.y)
      assertEquals(100, positions[content2]!!.x)
      assertEquals(0, positions[content2]!!.y)
      assertEquals(0, positions[content3]!!.x)
      assertEquals(100, positions[content3]!!.y)
      assertEquals(100, positions[content4]!!.x)
      assertEquals(100, positions[content4]!!.y)

      // After measuring, the position of the given contents shouldn't be changed.
      assertEquals(0, content1.x)
      assertEquals(0, content1.y)
      assertEquals(0, content2.x)
      assertEquals(0, content2.y)
      assertEquals(0, content3.x)
      assertEquals(0, content3.y)
      assertEquals(0, content4.x)
      assertEquals(0, content4.y)
    }
  }
}
