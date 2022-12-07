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
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import junit.framework.Assert.assertEquals
import org.junit.Test
import org.mockito.Mockito.any
import org.mockito.Mockito.anyInt
import java.awt.Point

class NlDesignSurfacePositionableContentLayoutManagerTest {

  @Test
  fun testSetLayoutManagerWillResetTheScrollPosition() {
    val mockedSurface = mock<NlDesignSurface>()
    whenever(mockedSurface.setSceneViewAlignment(any())).then { } // Do nothing
    whenever(mockedSurface.revalidateScrollArea()).then { } // Do nothing

    val position = Point(0, 0)
    whenever(mockedSurface.setScrollPosition(anyInt(), anyInt())).then { inv ->
      val x = inv.getArgument<Int>(0)
      val y = inv.getArgument<Int>(1)
      position.setLocation(x, y)
    }

    val layoutManager1 = GridSurfaceLayoutManager(0, 0, 0, 0)
    val layoutManager2 = GridSurfaceLayoutManager(0, 0, 0, 0)
    val contentLayoutManager = NlDesignSurfacePositionableContentLayoutManager(mockedSurface, layoutManager1)

    position.setLocation(100, 100)

    contentLayoutManager.setLayoutManager(layoutManager2)
    assertEquals(0, position.x)
    assertEquals(0, position.y)
  }
}
