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
import com.android.tools.idea.common.model.scaleOf
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.layout.padding.OrganizationPadding
import com.android.tools.idea.uibuilder.layout.positionable.GROUP_BY_BASE_COMPONENT
import com.android.tools.idea.uibuilder.layout.positionable.GridLayoutGroup
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.HeaderTestPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.layout.positionable.TestPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.content
import com.intellij.testFramework.UsefulTestCase.assertEmpty
import com.intellij.ui.scale.JBUIScale
import java.awt.Dimension
import java.awt.Point
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
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

  private val regularPadding =
    OrganizationPadding(25, 10, 20, 30, 40, { 5 }, { _, _ -> 5 }, { _, _ -> 5 })

  @Test
  fun measureEmpty() {
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    val positions = manager.measure(emptyList(), 100, 100)
    assertEmpty(positions.keys)
  }

  @Test
  fun measureSingle() {
    //     P - measured position
    //     ________________________________________________
    //     |                   45 ↕                        |
    //     |                P_____________                 |
    //     |  35            |     ↔30     |    35          |
    //     |  ↔             |             |    ↔           |
    //     |                | ↕ 30        |                |
    //     |                |_____________|                |
    //     |                    45 ↕                       |
    //     -------------------------------------------------
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    val content = TestPositionableContent(null, Dimension(30, 30))
    val positions = manager.measure(listOf(content), 100, 120)
    assertEquals(1, positions.size)
    assertEquals(content, positions.keys.single())
    assertEquals(Point(35, 45), positions.values.single())
  }

  @Test
  fun measureSingleInSmallSurface() {
    // Preview is bigger than surface
    // That's not really should happen, but we want to test there are always top and left
    // canvas paddings.
    //     P - measured position
    //      __________________________________
    //     |                   25 ↕          |
    //     |             P___________________|______________
    //     |  25         |     ↔ 100         |             |
    //     |  ↔          |                   |             |
    //     |             | ↕ 100             |             |
    //     |_____________|___________________|             |
    //                   |                                 |
    //                   -----------------------------------
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    val content = TestPositionableContent(null, Dimension(100, 100))
    val positions = manager.measure(listOf(content), 45, 45)
    assertEquals(1, positions.size)
    assertEquals(content, positions.keys.single())
    assertEquals(Point(25, 25), positions.values.single())
  }

  @Test
  fun fitScaleDoesFitForSingleContent() {
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    (100..1000 step 50).forEach { availableSize ->
      val singleContent = TestPositionableContent(null, Dimension(100, 100))
      val content = listOf(singleContent)
      val fitScale = manager.getFitIntoScale(content, availableSize, availableSize)
      val fitSize = manager.getSize(content, { Dimension(0, 0) }, { fitScale }, availableSize, null)
      // Check fit scale actually fits
      assertTrue { fitSize.width <= availableSize }
      assertTrue(fitSize.height <= availableSize)
    }
  }

  @Test
  fun measureContent() {
    //     P - measured position
    //     _____________________________________________________________
    //     |                             10 ↕                            |
    //     |     P______________________                                 |
    //     |20 ↔ | header 300x15       |                                 |
    //     |     |_____________________|                                 |
    //     |                              ↕ 5                            |
    //     |          P________         P________        P________       |
    //     | 20+40 ↔  | 30x22  |  5 ↔   | 30x22  |  5 ↔  | 30x22  |  5 ↔ |
    //     |          |________|        |________|       |________|      |
    //     |                               ↕ 5                           |
    //     |          P________                                          |
    //     |          | 30x22  |  5 ↔                                    |
    //     |          |________|                                         |
    //     |                                ↕ 30                         |
    //     ---------------------------------------------------------------
    val expected =
      listOf(
        Point(20, 10),
        // Row one
        Point(20 + 40, 10 + 15 + 5),
        Point(20 + 40 + 30 + 5, 10 + 15 + 5),
        Point(20 + 40 + 30 + 5 + 30 + 5, 10 + 15 + 5),
        // Row two
        Point(20 + 40, 10 + 15 + 5 + 22 + 5),
      )

    val group = OrganizationGroup("1", "1")
    val content = (1..4).map { TestPositionableContent(group, Dimension(30, 22)) }.toMutableList()
    content.add(0, HeaderTestPositionableContent(group, Dimension(300, 15)))
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    // 165 is the exact width needed to fit content as shown on diagram
    manager.measure(content, 165, 500).let { positions ->
      assertEquals(5, positions.size)
      assertEquals(expected, positions.values.toList())
    }
    // 25 is extra spacing, but it will not let to fit more content as content size is 30
    manager.measure(content, 165 + 20, 500).let { positions ->
      assertEquals(5, positions.size)
      assertEquals(expected, positions.values.toList())
    }
  }

  private val largeGroupOfPreviews: Collection<PositionableContent>
    get() {
      val group1 = OrganizationGroup("1", "1")
      val group2 = OrganizationGroup("2", "2")
      val group3 = OrganizationGroup("3", "3")
      return listOf(
        // Group 1
        HeaderTestPositionableContent(group1, Dimension(23, 15)),
        TestPositionableContent(group1, Dimension(20, 23)),
        TestPositionableContent(group1, Dimension(20, 22)),
        TestPositionableContent(group1, Dimension(31, 44)),
        // Group without header
        TestPositionableContent(null, Dimension(11, 10)),
        TestPositionableContent(null, Dimension(22, 10)),
        TestPositionableContent(null, Dimension(11, 10)),
        TestPositionableContent(null, Dimension(22, 10)),
        // Group 2
        HeaderTestPositionableContent(group2, Dimension(33, 15)),
        TestPositionableContent(group2, Dimension(44, 5)),
        TestPositionableContent(group2, Dimension(33, 5)),
        TestPositionableContent(group2, Dimension(22, 5)),
        TestPositionableContent(group2, Dimension(1, 5)),
        TestPositionableContent(group2, Dimension(2, 5)),
        // Group without header
        TestPositionableContent(group3, Dimension(77, 10)),
        TestPositionableContent(group3, Dimension(11, 10)),
        TestPositionableContent(group3, Dimension(22, 10)),
      )
    }

  @Test
  fun checkContentFitWhileResizing() {
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    fun getSize(width: Int) =
      manager.getSize(
        largeGroupOfPreviews,
        /** Unused */
        { Dimension(0, 0) },
        scaleFunc = { 1.0 },
        width,
        null,
      )

    // For this test case - we know what each preview can fit fully into width 150
    (0..100).forEach { iteration ->
      val availableWidth = 150 + 10 * iteration
      val requiredSize = getSize(availableWidth)
      assertTrue { requiredSize.width <= availableWidth }
    }
  }

  @Test
  fun checkHeightAlwaysIncreasesWithScaleIncrease() {
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    fun getSize(scale: Double) =
      manager.getSize(
        largeGroupOfPreviews,
        /** Unused */
        { Dimension(0, 0) },
        scaleFunc = { scale },
        150,
        null,
      )

    var prevHeight = 0
    (1000 downTo 1 step 10).forEach { iteration ->
      val scale = 20 / iteration.toDouble() // Iterate from 0.2% to 20%
      val requiredHeight = getSize(scale).height
      assertTrue { requiredHeight >= prevHeight }
      prevHeight = requiredHeight
    }
  }

  @Test
  fun checkFitScaleAlwaysIncreaseWithHeightIncrease() {
    val content = largeGroupOfPreviews
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    var prevFitScale = 0.0

    (20..9000 step 30).forEach { size ->
      val fitScale = manager.getFitIntoScale(content, size, size)
      assertTrue(
        "fitScale $fitScale should be bigger than prevFitScale $prevFitScale, size $size"
      ) {
        fitScale >= prevFitScale
      }
      prevFitScale = fitScale
    }
  }

  @Test
  fun fitScaleDoesFit() {
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    (400..1000 step 50).forEach { availableSize ->
      val fitScale = manager.getFitIntoScale(largeGroupOfPreviews, availableSize, availableSize)
      val fitSize =
        manager.getSize(
          largeGroupOfPreviews,
          { Dimension(0, 0) },
          { fitScale },
          availableSize,
          null,
        )
      assertTrue { fitSize.width <= availableSize }
      assertTrue(fitSize.height <= availableSize)
    }
  }

  @Test
  fun checkGroupSize() {
    //     Part of the surface taken by GridLayoutGroup, does not include any canvas paddings.
    //
    //     _   _   _   _   _   _   _   _   _   _   _   _   _   _   _  _
    //     |_______________________                                   |
    //      | header 100x16        |
    //     ||_____________________|                                   |
    //                                   ↕ 5
    //     |       _________         _________        _________       |
    //       40 ↔  | 30x22  |  5 ↔   | 30x22  |  5 ↔  | 30x22  |  5 ↔
    //     |       |________|        |________|       |________|      |
    //                                     ↕ 5
    //     |       _________                                          |
    //             | 30x22  |  5 ↔
    //     |       |________|                                         |
    //                                      ↕ 5
    //      _   _   _   _   _   _   _   _   _   _   _   _   _   _   _  _
    val group = OrganizationGroup("1", "1")
    val row1 = (1..3).map { TestPositionableContent(group, Dimension(30, 22)) }
    val row2 = listOf(TestPositionableContent(group, Dimension(30, 22)))
    val layoutGroup =
      GridLayoutGroup(HeaderTestPositionableContent(group, Dimension(100, 16)), listOf(row1, row2))
    val manager = GridLayoutManager(regularPadding, GROUP_BY_BASE_COMPONENT)
    assertEquals(Dimension(145, 75), manager.getGroupSize(layoutGroup, { 1.0 }))
    assertEquals(Dimension(100, 45), manager.getGroupSize(layoutGroup, { 0.5 }))
    assertEquals(Dimension(235, 135), manager.getGroupSize(layoutGroup, { 2.0 }))
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
        )

      val layoutGroupWithDifferentContent =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = initialWidth,
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
        )

      // We are changing the
      assertNotEquals(initialLayoutGroup, layoutGroup0)

      widthChange = 200
      val layoutGroup1 =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = widthChange,
        )
      assertNotEquals(layoutGroup0, layoutGroup1)

      widthChange = 10
      val layoutGroup2 =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = widthChange,
        )
      assertNotEquals(layoutGroup1, layoutGroup2)

      widthChange = 2000
      val layoutGroup3 =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = widthChange,
        )
      assertNotEquals(layoutGroup2, layoutGroup3)

      widthChange = 5000
      val layoutGroup4 =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = widthChange,
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
        )

      var zoomIn = 1.0
      var layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroups(groups, { zoomIn }, width)
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 5.0
      layoutGroupWhenScaleChanges = gridLayoutManager.createLayoutGroups(groups, { zoomIn }, width)
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 50.0
      layoutGroupWhenScaleChanges = gridLayoutManager.createLayoutGroups(groups, { zoomIn }, width)
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 2.0
      layoutGroupWhenScaleChanges =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { zoomIn },
          availableWidth = width,
        )
      assertEquals(initialLayoutGroup, layoutGroupWhenScaleChanges)

      zoomIn = 100.0
      layoutGroupWhenScaleChanges = gridLayoutManager.createLayoutGroups(groups, { zoomIn }, width)
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
        )

      val newLayoutGroupWithSameContent =
        gridLayoutManager.createLayoutGroups(
          groups = groups,
          scaleFunc = { initialScale },
          availableWidth = width,
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
      // If the content is not the same (as this case) we would invalidate update the cache with
      // the new items
      val newLayoutGroupWithDifferentContent: List<GridLayoutGroup> =
        gridLayoutManager.createLayoutGroups(
          groups = positionableGroup,
          scaleFunc = { initialScale },
          availableWidth = width,
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
        gridLayoutManager.createLayoutGroups(changedGroups, { initialScale }, initialWidth)

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
    val actualScale = 1.0 / JBUIScale.sysScale()
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

    // The fit scale should be 1.0 (100%) / JBUIScale.sysScale()
    assertEquals(actualScale, scale, tolerance)

    // We now have no content other than Headers
    scale = manager.getFitIntoScale(contents, 300, 100)

    // The fit scale should be 1.0 (100%) / JBUIScale.sysScale()
    assertEquals(actualScale, scale, tolerance)

    // We now expand the last header so we have now content to show
    contents.add(TestPositionableContent(group4, Dimension(400, 500)))
    scale = manager.getFitIntoScale(contents, 300, 100)

    // The fit scale should not be 1.0 (100%) / JBUIScale.sysScale() anymore
    assertNotEquals(actualScale, scale, tolerance)
  }

  @Test
  fun testGroupedContentZoomToFitFitsAvailableSpace() {
    val group1 = OrganizationGroup("1", "1")
    val group2 = OrganizationGroup("2", "2")

    val manager = createGridLayoutManager()

    // Given a group organized in two groups.
    val contents =
      mutableListOf<PositionableContent>(
        HeaderTestPositionableContent(group1),
        TestPositionableContent(group1, Dimension(400, 400)),
        TestPositionableContent(group1, Dimension(400, 500)),
        TestPositionableContent(group1, Dimension(600, 1000)),
        TestPositionableContent(group1, Dimension(400, 500)),
        HeaderTestPositionableContent(group2),
        TestPositionableContent(group2, Dimension(400, 400)),
        TestPositionableContent(group2, Dimension(400, 500)),
        TestPositionableContent(group2, Dimension(600, 1000)),
        TestPositionableContent(group2, Dimension(400, 500)),
      )

    // Given this available size on the window
    val availableSize = Dimension(800, 800)

    // We calculate the scale which content fits the available size.
    val fitScale = manager.getFitIntoScale(contents, availableSize.width, availableSize.height)

    // Once we get the scale that fits the content in the available space, we calculate the size of
    // the layout having the content with that scale applied.
    val fitScaleSize =
      manager.getSizeForTestOnly(
        contents,
        { fitScale },
        availableSize.width,
        { contentSize.scaleOf(fitScale) },
      )

    assertTrue(fitScaleSize.height <= availableSize.height)
    // However, we also expect that the height is not smaller than a certain tolerance.
    // Delta value is chosen by the minimum height of the content (headers excluded), if the test
    // fails means fitScale is not properly calculated as there were more space to fill-in.
    val heightDelta =
      contents.filter { it !is HeaderPositionableContent }.minOf { it.contentSize.height }
    assertTrue(fitScaleSize.height + heightDelta >= availableSize.height)

    // We expect the results to fit the available space, since it is organized in groups we have
    // wider groups than others. It's important that width is still within the available space.
    assertTrue(fitScaleSize.width < availableSize.width)
  }

  @Test
  fun testUngroupedContentZoomToFitFitsAvailableSpace() {
    val manager = createGridLayoutManager()

    // Given a content that is not organised, and it should show as a grid.
    val contents =
      mutableListOf<PositionableContent>(
        TestPositionableContent(null, Dimension(400, 400)),
        TestPositionableContent(null, Dimension(1400, 500)),
        TestPositionableContent(null, Dimension(600, 1000)),
        TestPositionableContent(null, Dimension(400, 500)),
        TestPositionableContent(null, Dimension(400, 800)),
        TestPositionableContent(null, Dimension(400, 500)),
        TestPositionableContent(null, Dimension(600, 1000)),
        TestPositionableContent(null, Dimension(400, 500)),
      )

    // Given this available size on the window
    val availableSize = Dimension(800, 800)

    // We calculate the scale which content fits the available size
    val fitScale = manager.getFitIntoScale(contents, availableSize.width, availableSize.height)
    // Once we get the scale that fits the content in the available space, we calculate the size of
    // the layout having the content with that scale applied.
    val fitScaleSize =
      manager.getSizeForTestOnly(
        contents,
        { fitScale },
        availableSize.width,
        { contentSize.scaleOf(fitScale) },
      )

    // Because it shows a grid we expect the width is not smaller than the available space.
    // Delta value is chosen by the minimum height of the content, if the test fails means
    // fitScale is not properly calculated as there were more space to fill-in.
    assertTrue(fitScaleSize.width < availableSize.width)
    val widthDelta = contents.minOf { it.contentSize.width }
    assertTrue(fitScaleSize.width + widthDelta >= availableSize.width)

    assertTrue(fitScaleSize.height <= availableSize.height)
    // However, we also expect that the height is not smaller than a certain tolerance.
    // Delta value is chosen by the minimum height of the content, if the test fails means
    // fitScale is not properly calculated as there were more space to fill-in.
    val heightDelta = contents.minOf { it.contentSize.height }
    assertTrue(fitScaleSize.height + heightDelta >= availableSize.height)
  }

  @Test
  fun testMixedContentZoomToFitFitsAvailableSpace() {
    val group1 = OrganizationGroup("1", "1")

    val manager = createGridLayoutManager()

    // Given a group organized in two groups.
    val contents =
      mutableListOf<PositionableContent>(
        TestPositionableContent(null, Dimension(400, 400)),
        TestPositionableContent(null, Dimension(400, 500)),
        TestPositionableContent(null, Dimension(600, 1000)),
        TestPositionableContent(null, Dimension(400, 500)),
        HeaderTestPositionableContent(group1),
        TestPositionableContent(group1, Dimension(400, 400)),
        TestPositionableContent(group1, Dimension(400, 500)),
        TestPositionableContent(group1, Dimension(600, 1000)),
        TestPositionableContent(group1, Dimension(400, 500)),
      )

    // Given this available size on the window.
    val availableSize = Dimension(800, 800)

    // We calculate the scale which content fits the available size.
    val fitScale = manager.getFitIntoScale(contents, availableSize.width, availableSize.height)

    // Once we get the scale that fits the content in the available space, we calculate the size of
    // the layout having the content with that scale applied.
    val fitScaleSize =
      manager.getSizeForTestOnly(
        contents,
        { fitScale },
        availableSize.width,
        { contentSize.scaleOf(fitScale) },
      )

    assertTrue(fitScaleSize.height <= availableSize.height)
    // However, we also expect that the height is not smaller than a certain tolerance.
    // Delta value is chosen by the minimum height of the content (headers excluded), if the test
    // fails means fitScale is not properly calculated as there were more space to fill-in.
    val heightDelta =
      contents.filter { it !is HeaderPositionableContent }.minOf { it.contentSize.height }
    assertTrue(fitScaleSize.height + heightDelta >= availableSize.height)

    // We expect the results to fit the available space, since we have mixed grouped and ungrouped
    // content we could have wider groups than others.
    // It's important that width is still within the available space
    assertTrue(fitScaleSize.width < availableSize.width)
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
        canvasSinglePadding = 0,
        canvasTopPadding = 0,
        canvasLeftPadding = 0,
        canvasBottomPadding = 0,
        groupLeftPadding = 0,
        previewPaddingProvider = { (it * framePadding).toInt() },
        previewRightPadding = { _, _ -> 0 },
        previewBottomPadding = { _, _ -> 0 },
      ),
      GROUP_BY_BASE_COMPONENT,
    )
  }
}
