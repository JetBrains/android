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
    val groups = createGroups()

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val initialWidth = 500
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = initialWidth,
          sizeFunc = { scaledContentSize },
        )

      val layoutGroupWithDifferentContent =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = initialWidth,
          sizeFunc = { scaledContentSize },
        )

      assertNotEquals(initialLayoutGroup, layoutGroupWithDifferentContent)
    }
  }

  @Test
  fun `test layout group changes when width change`() {
    val groups = createGroups()

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val initialWidth = 10
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = initialWidth,
          sizeFunc = { scaledContentSize },
        )

      // Now we are changing width in the surface should change layoutGroup when the
      // SCROLLABLE_ZOOM_ON_GRID
      // flag is enabled
      var widthChange = 60
      val layoutGroup0 =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = widthChange,
          sizeFunc = { scaledContentSize },
        )

      // We are changing the
      assertNotEquals(initialLayoutGroup, layoutGroup0)

      widthChange = 200
      val layoutGroup1 =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = widthChange,
          sizeFunc = { scaledContentSize },
        )
      assertNotEquals(layoutGroup0, layoutGroup1)

      widthChange = 10
      val layoutGroup2 =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = widthChange,
          sizeFunc = { scaledContentSize },
        )
      assertNotEquals(layoutGroup1, layoutGroup2)

      widthChange = 2000
      val layoutGroup3 =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = widthChange,
          sizeFunc = { scaledContentSize },
        )
      assertNotEquals(layoutGroup2, layoutGroup3)

      widthChange = 5000
      val layoutGroup4 =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = widthChange,
          sizeFunc = { scaledContentSize },
        )
      assertNotEquals(layoutGroup3, layoutGroup4)
    }
  }

  @Test
  fun `test layout group doesn't change when zoom changes and flag enabled`() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.override(true)
    val groups = createGroups()

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val width = 300
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = width,
          sizeFunc = { scaledContentSize },
        )

      var zoomIn = 1.0
      var layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroups(groups, { zoomIn }, width, { scaledContentSize })
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 5.0
      layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroups(groups, { zoomIn }, width, { scaledContentSize })
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 50.0
      layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroups(groups, { zoomIn }, width, { scaledContentSize })
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 2.0
      layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { zoomIn },
          availableWidth = width,
          sizeFunc = { scaledContentSize },
        )
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 100.0
      layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroups(groups, { zoomIn }, width, { scaledContentSize })
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)
    }
  }

  @Test
  fun `test layout group doesn't change on same content and flag enabled`() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.override(true)
    val groups = createGroups()

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val width = 500
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = width,
          sizeFunc = { scaledContentSize },
        )

      val layoutGroupWithDifferentContent =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = width,
          sizeFunc = { scaledContentSize },
        )

      assertEquals(initialLayoutGroup, layoutGroupWithDifferentContent)
    }
  }

  @Test
  fun `test layout group changes when on different content and flag enabled`() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.override(true)

    val group1 = OrganizationGroup("0", "0")
    val positionableGroup1 =
      PositionableGroup(
        content =
          listOf(
            TestPositionableContent(group1, Dimension(400, 400)),
            TestPositionableContent(group1, Dimension(400, 500)),
            TestPositionableContent(group1, Dimension(600, 1000)),
            TestPositionableContent(group1, Dimension(400, 500)),
          ),
        header = HeaderTestPositionableContent(group1),
      )

    val positionableGroup2 = getPositionableGroup2()
    val initialGroups = listOf(positionableGroup1, positionableGroup2)

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val initialWidth = 500
      val initialScale = 1.0
      val initialLayoutGroup =
        gridLayoutManager.createLayoutGroups(
          groups = initialGroups,
          scaleFunc = { initialScale },
          availableWidth = initialWidth,
          sizeFunc = { scaledContentSize },
        )

      val newPositionableContentGroup1 =
        PositionableGroup(
          header = HeaderTestPositionableContent(group1),
          content =
            listOf(
              TestPositionableContent(group1, Dimension(400, 400)),
              TestPositionableContent(group1, Dimension(400, 500)),
              TestPositionableContent(group1, Dimension(600, 1000)),
              TestPositionableContent(group1, Dimension(400, 500)),
            ),
        )

      val changedGroups = listOf(newPositionableContentGroup1, positionableGroup2)
      val layoutGroupWithDifferentContent =
        gridLayoutManager.createLayoutGroups(
          changedGroups,
          { initialScale },
          initialWidth,
          { scaledContentSize },
        )

      assertNotEquals(initialLayoutGroup, layoutGroupWithDifferentContent)
    }
  }

  private fun createGroups(): List<PositionableGroup> {
    return listOf(getGroup1(), getPositionableGroup2())
  }

  private fun getGroup1(): PositionableGroup {
    val group1 = OrganizationGroup("0", "0")
    return PositionableGroup(
      content =
        listOf(
          TestPositionableContent(group1, Dimension(400, 400)),
          TestPositionableContent(group1, Dimension(400, 500)),
          TestPositionableContent(group1, Dimension(600, 1000)),
          TestPositionableContent(group1, Dimension(400, 500)),
        ),
      header = HeaderTestPositionableContent(group1),
    )
  }

  private fun getPositionableGroup2(): PositionableGroup {
    val group2 = OrganizationGroup("1", "1")
    return PositionableGroup(
      content =
        listOf(
          TestPositionableContent(group2, Dimension(200, 400)),
          TestPositionableContent(group2, Dimension(400, 50)),
          TestPositionableContent(group2, Dimension(10, 500)),
          TestPositionableContent(group2, Dimension(600, 10)),
          TestPositionableContent(group2, Dimension(400, 500)),
        ),
      header = HeaderTestPositionableContent(group2),
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
