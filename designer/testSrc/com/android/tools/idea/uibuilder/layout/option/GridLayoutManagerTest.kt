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
package com.android.tools.idea.uibuilder.layout.option

import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.common.layout.positionable.scaledContentSize
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.layout.padding.OrganizationPadding
import com.android.tools.idea.uibuilder.layout.positionable.HeaderTestPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.layout.positionable.TestPositionableContent
import java.awt.Dimension
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import org.junit.After
import org.junit.Before
import org.junit.Test

class GridLayoutManagerTest {
  private val gridLayoutManager: GridLayoutManager = createGridLayoutManager()

  @Before
  fun setUp() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.override(false)
  }

  @After
  fun tearDown() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.clearOverride()
  }

  @Test
  fun `test layout group changes on same content`() {
    val group = createGroup()

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val initialWidth = 500
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroup(
          group = group,
          scaleFunc = { initialScale },
          availableWidth = initialWidth,
          widthFunc = { scaledContentSize.width },
        )

      val layoutGroupWithDifferentContent =
        gridLayoutManager.createLayoutGroup(
          group,
          { initialScale },
          initialWidth,
          { scaledContentSize.width },
        )

      assertNotEquals(initialLayoutGroup, layoutGroupWithDifferentContent)
    }
  }

  @Test
  fun `test layout group changes when width change`() {
    val group = createGroup()

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val initialWidth = 10
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroup(
          group,
          { initialScale },
          initialWidth,
          { scaledContentSize.width },
        )

      // Now we are changing width in the surface should change layoutGroup when the
      // SCROLLABLE_ZOOM_ON_GRID
      // flag is enabled
      var widthChange = 60
      val layoutGroup0 =
        gridLayoutManager.createLayoutGroup(
          group,
          { initialScale },
          widthChange,
          { scaledContentSize.width },
        )

      // We are changing the
      assertNotEquals(initialLayoutGroup, layoutGroup0)

      widthChange = 200
      val layoutGroup1 =
        gridLayoutManager.createLayoutGroup(
          group,
          { initialScale },
          widthChange,
          { scaledContentSize.width },
        )
      assertNotEquals(layoutGroup0, layoutGroup1)

      widthChange = 10
      val layoutGroup2 =
        gridLayoutManager.createLayoutGroup(
          group,
          { initialScale },
          widthChange,
          { scaledContentSize.width },
        )
      assertNotEquals(layoutGroup1, layoutGroup2)

      widthChange = 2000
      val layoutGroup3 =
        gridLayoutManager.createLayoutGroup(
          group,
          { initialScale },
          widthChange,
          { scaledContentSize.width },
        )
      assertNotEquals(layoutGroup2, layoutGroup3)

      widthChange = 5000
      val layoutGroup4 =
        gridLayoutManager.createLayoutGroup(
          group,
          { initialScale },
          widthChange,
          { scaledContentSize.width },
        )
      assertNotEquals(layoutGroup3, layoutGroup4)
    }
  }

  @Test
  fun `test layout group doesn't change when zoom changes and flag enabled`() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.override(true)
    val group = createGroup()

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val width = 300
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroup(
          group,
          { initialScale },
          width,
          { scaledContentSize.width },
        )

      var zoomIn = 1.0
      var layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroup(group, { zoomIn }, width, { scaledContentSize.width })
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 5.0
      layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroup(group, { zoomIn }, width, { scaledContentSize.width })
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 50.0
      layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroup(group, { zoomIn }, width, { scaledContentSize.width })
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 2.0
      layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroup(group, { zoomIn }, width, { scaledContentSize.width })
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 100.0
      layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroup(group, { zoomIn }, width, { scaledContentSize.width })
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)
    }
  }

  @Test
  fun `test layout group doesn't change on same content and flag enabled`() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.override(true)
    val group = createGroup()

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val initialWidth = 500
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroup(
          group = group,
          scaleFunc = { initialScale },
          availableWidth = initialWidth,
          widthFunc = { scaledContentSize.width },
        )

      val layoutGroupWithDifferentContent =
        gridLayoutManager.createLayoutGroup(
          group,
          { initialScale },
          initialWidth,
          { scaledContentSize.width },
        )

      assertEquals(initialLayoutGroup, layoutGroupWithDifferentContent)
    }
  }

  @Test
  fun `test layout group changes when on different content and flag enabled`() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.override(true)

    val group = createGroup()

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val initialWidth = 500
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroup(
          group = group,
          scaleFunc = { initialScale },
          availableWidth = initialWidth,
          widthFunc = { scaledContentSize.width },
        )

      val newContent =
        mutableListOf<PositionableContent>().apply {
          add(TestPositionableContent(OrganizationGroup("1", "1"), Dimension(400, 400)))
          addAll(group.content)
        }

      val contentChanged = PositionableGroup(content = newContent.toList(), header = group.header)
      val layoutGroupWithDifferentContent =
        gridLayoutManager.createLayoutGroup(
          contentChanged,
          { initialScale },
          initialWidth,
          { scaledContentSize.width },
        )

      assertNotEquals(initialLayoutGroup, layoutGroupWithDifferentContent)
    }
  }

  private fun createGroup(): PositionableGroup {
    val group = OrganizationGroup("0", "0")
    return PositionableGroup(
      content =
        listOf(
          TestPositionableContent(group, Dimension(400, 400)),
          TestPositionableContent(group, Dimension(400, 500)),
          TestPositionableContent(group, Dimension(1000, 500)),
          TestPositionableContent(group, Dimension(600, 1000)),
          TestPositionableContent(group, Dimension(400, 500)),
        ),
      header = HeaderTestPositionableContent(group),
    )
  }

  private fun createGridLayoutManager(): GridLayoutManager {
    val framePadding = 50
    return GridLayoutManager(
      OrganizationPadding(
        canvasTopPadding = 0,
        canvasLeftPadding = 0,
        groupLeftPadding = 0,
        previewPaddingProvider = { (it * framePadding).toInt() },
        previewRightPadding = { _, _ -> 0 },
        previewBottomPadding = { _, _ -> 0 },
      )
    ) {
      listOf(PositionableGroup(it.toList()))
    }
  }
}
