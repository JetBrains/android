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
import com.android.tools.idea.uibuilder.layout.positionable.GridLayoutGroup
import com.android.tools.idea.uibuilder.layout.positionable.HeaderTestPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.layout.positionable.TestPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.content
import java.awt.Dimension
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlinx.coroutines.flow.MutableStateFlow
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

    gridLayoutManager.useCachedLayoutGroups(MutableStateFlow(emptyList()))

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
    gridLayoutManager.useCachedLayoutGroups(MutableStateFlow(emptyList()))
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

      val newLayoutGroupWithSameContent =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = width,
          sizeFunc = { scaledContentSize },
        )

      assertEquals(initialLayoutGroup, newLayoutGroupWithSameContent)
    }
  }

  @Test
  fun `test get cached layout group when content doesn't change`() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.override(true)

    // Both gridLayoutGroup and positionableGroup contains the same items
    val gridLayoutGroup: List<GridLayoutGroup> = listOf(createGridLayoutGroup1())
    val positionableGroup: List<PositionableGroup> = listOf(getPositionableGroup1())

    gridLayoutManager.useCachedLayoutGroups(MutableStateFlow(gridLayoutGroup))

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val width = 500
      val initialScale = 1.0

      // Whenever we call createLayoutGroups we organize the positionable group in grid.
      // If the content is the same so we would not create new GridLayoutGroups
      val newLayoutGroupWithSameContent: List<GridLayoutGroup> =
        gridLayoutManager.createLayoutGroups(
          groups = positionableGroup,
          scaleFunc = { initialScale },
          availableWidth = width,
          sizeFunc = { scaledContentSize },
        )

      // The resulting value is the cache
      assertEquals(gridLayoutGroup, newLayoutGroupWithSameContent)
      assertEquals(newLayoutGroupWithSameContent, gridLayoutManager.cachedLayoutGroups?.value)
    }
  }

  @Test
  fun `test get cached layout group when content changes`() {
    StudioFlags.SCROLLABLE_ZOOM_ON_GRID.override(true)

    // Both gridLayoutGroup and positionableGroup are different items
    val gridLayoutGroup: List<GridLayoutGroup> = listOf(createGridLayoutGroup1())
    val positionableGroup: List<PositionableGroup> = listOf(getPositionableGroup2())

    gridLayoutManager.useCachedLayoutGroups(MutableStateFlow(gridLayoutGroup))

    run {
      // Zooming in the surface shouldn't change layoutGroup when the SCROLLABLE_ZOOM_ON_GRID flag
      // is enabled
      val width = 500
      val initialScale = 1.0

      // Whenever we call createLayoutGroups we organize the positionable group in grid.
      // If the content is not the same (like this case) we would invalidate update the cache with
      // the new items
      val newLayoutGroupWithDifferentContent: List<GridLayoutGroup> =
        gridLayoutManager.createLayoutGroups(
          groups = positionableGroup,
          scaleFunc = { initialScale },
          availableWidth = width,
          sizeFunc = { scaledContentSize },
        )

      // The value is not the one stored in the cache
      assertNotEquals(gridLayoutGroup, newLayoutGroupWithDifferentContent)

      val actualPositionableGroup = newLayoutGroupWithDifferentContent.map { it.content() }
      val expectedPositionableGroup = positionableGroup.map { it.content }
      assertEquals(expectedPositionableGroup, actualPositionableGroup)
      assertEquals(newLayoutGroupWithDifferentContent, gridLayoutManager.cachedLayoutGroups?.value)
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

  @Test
  fun testFitIntoScaleWithBiggerPreviews() {
    val group = OrganizationGroup("1", "1")
    val manager = createGridLayoutManager()

    val tolerance = 0.01

    val contents = (0..50).map { TestPositionableContent(group, Dimension(17000, 9000)) }

    run {
      val scale = manager.getFitIntoScale(contents, 300, 100)
      assertEquals(0.01, scale, tolerance)
    }
  }

  @Test
  fun testFitIntoScaleWithNoContentOrOnlyHeaders() {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("2", "2")
    val group3 = OrganizationGroup("3", "3")
    val group4 = OrganizationGroup("4", "4")

    val manager = createGridLayoutManager()

    val tolerance = 0.01

    val contents =
      mutableListOf<PositionableContent>(
        HeaderTestPositionableContent(group1),
        HeaderTestPositionableContent(group2),
        HeaderTestPositionableContent(group3),
        HeaderTestPositionableContent(group4),
      )

    // We start with no content to show
    var scale = manager.getFitIntoScale(emptyList(), 300, 100)

    // The fit scale should be 1.0 (100%)
    assertEquals(1.0, scale, tolerance)

    // We now have no content other than Headers
    scale = manager.getFitIntoScale(contents, 300, 100)

    // The fit scale should be 1.0 (100%)
    assertEquals(1.0, scale, tolerance)

    // We now expand the last header so we have now content to show
    contents.add(TestPositionableContent(group4, Dimension(400, 500)))
    scale = manager.getFitIntoScale(contents, 300, 100)

    // The fit scale should not be 1.0 (100%) anymore
    assertNotEquals(1.0, scale, tolerance)
  }

  private fun createGroups(): List<PositionableGroup> {
    return listOf(getPositionableGroup1(), getPositionableGroup2())
  }

  private val group1 = OrganizationGroup("0", "0")
  private val headerGroup1 = HeaderTestPositionableContent(group1)
  private val group =
    listOf(
      TestPositionableContent(group1, Dimension(400, 400)),
      TestPositionableContent(group1, Dimension(400, 500)),
      TestPositionableContent(group1, Dimension(600, 1000)),
      TestPositionableContent(group1, Dimension(400, 500)),
    )

  /** A [PositionableGroup] with the same items of [createGridLayoutGroup1] */
  private fun getPositionableGroup1(): PositionableGroup {
    return PositionableGroup(content = group, header = headerGroup1)
  }

  /** A [GridLayoutGroup] with the same items of [getPositionableGroup1] */
  private fun createGridLayoutGroup1(): GridLayoutGroup {
    return GridLayoutGroup(header = headerGroup1, rows = listOf(group))
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
