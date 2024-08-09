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
package com.android.tools.idea.preview.modes

import com.android.tools.idea.common.layout.SceneViewAlignment
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.layout.positionable.PositionableContent
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.uibuilder.layout.option.GalleryLayoutManager
import com.android.tools.idea.uibuilder.layout.option.GridLayoutManager
import com.android.tools.idea.uibuilder.layout.option.GroupedListSurfaceLayoutManager
import com.android.tools.idea.uibuilder.layout.option.ListLayoutManager
import com.android.tools.idea.uibuilder.layout.padding.GroupPadding
import com.android.tools.idea.uibuilder.layout.padding.OrganizationPadding
import com.android.tools.idea.uibuilder.layout.positionable.HeaderPositionableContent
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager
import org.jetbrains.annotations.VisibleForTesting

private val PREVIEW_FRAME_PADDING_PROVIDER: (Double) -> Int = { scale ->
  dynamicPadding(scale, 5, 20)
}

/**
 * Provider of the horizontal and vertical paddings for preview. The input value is the scale value
 * of the current [PositionableContent].
 */
private val ORGANIZATION_PREVIEW_RIGHT_PADDING: (Double, PositionableContent) -> Int =
  { scale, content ->
    if (content is HeaderPositionableContent) dynamicPadding(scale, 0, 0)
    else dynamicPadding(scale, 5, 15)
  }

/**
 * Provider of the horizontal and vertical paddings for preview. The input value is the scale value
 * of the current [PositionableContent].
 */
private val ORGANIZATION_PREVIEW_BOTTOM_PADDING: (Double, PositionableContent) -> Int =
  { scale, content ->
    if (content is HeaderPositionableContent) dynamicPadding(scale, 0, 0)
    else dynamicPadding(scale, 5, 7)
  }

/**
 * Provider of the padding for preview. The input value is the scale value of the current
 * [PositionableContent]. Minimum padding is min at 20% and maximum padding is max at 100%,
 * responsive.
 */
private fun dynamicPadding(scale: Double, min: Int, max: Int): Int =
  when {
    scale <= 0.2 -> min
    scale >= 1.0 -> max
    else ->
      min + ((max - min) / (1 - 0.2)) * (scale - 0.2) // find interpolated value between min and max
  }.toInt()

private val NO_GROUP_TRANSFORM: (Collection<PositionableContent>) -> List<PositionableGroup> = {
  listOf(PositionableGroup(it.toList()))
}

@VisibleForTesting
val GROUP_BY_BASE_COMPONENT: (Collection<PositionableContent>) -> List<PositionableGroup> =
  { contents ->
    val groups = mutableMapOf<Any?, MutableList<PositionableContent>>()
    for (content in contents) {
      groups.getOrPut(content.organizationGroup) { mutableListOf() }.add(content)
    }

    groups.values
      .fold(Pair(mutableListOf<PositionableGroup>(), mutableListOf<PositionableContent>())) {
        temp,
        next ->
        val hasHeader = next.any { it is HeaderPositionableContent }
        // If next is not in its own group - keep it in temp.second
        if (!hasHeader) {
          temp.second.addAll(next)
        }

        // Temp.second contains all consecutive previews without its own group.
        // If next is not in a group or if it is the last element, group all collected
        // previews as one group
        if (hasHeader || groups.values.last() == next) {
          if (temp.second.isNotEmpty()) {
            temp.first.add(
              PositionableGroup(
                temp.second.filter { it !is HeaderPositionableContent },
                temp.second.filterIsInstance<HeaderPositionableContent>().singleOrNull(),
              )
            )
            temp.second.clear()
          }
        }

        // If next has its own group - it will have its own PositionableGroup
        if (hasHeader) {
          temp.first.add(
            PositionableGroup(
              next.filter { it !is HeaderPositionableContent },
              next.filterIsInstance<HeaderPositionableContent>().singleOrNull(),
            )
          )
        }

        temp
      }
      .first
  }

private val galleryPadding = GroupPadding(5, 0, PREVIEW_FRAME_PADDING_PROVIDER)
private val listPadding = GroupPadding(5, 25, PREVIEW_FRAME_PADDING_PROVIDER)
private val gridPadding = GroupPadding(5, 0, PREVIEW_FRAME_PADDING_PROVIDER)
private val organizationListPadding =
  OrganizationPadding(
    10,
    10,
    24,
    PREVIEW_FRAME_PADDING_PROVIDER,
    ORGANIZATION_PREVIEW_RIGHT_PADDING,
    ORGANIZATION_PREVIEW_BOTTOM_PADDING,
  )
private val organizationGridPadding =
  OrganizationPadding(
    10,
    10,
    24,
    PREVIEW_FRAME_PADDING_PROVIDER,
    ORGANIZATION_PREVIEW_RIGHT_PADDING,
    ORGANIZATION_PREVIEW_BOTTOM_PADDING,
  )

/** [PreviewMode.Gallery] layout option which shows once centered element. */
val GALLERY_LAYOUT_OPTION =
  SurfaceLayoutOption(
    message("gallery.mode.title"),
    GalleryLayoutManager(galleryPadding, NO_GROUP_TRANSFORM),
    false,
    SceneViewAlignment.LEFT,
  )

/** List layout option which doesn't group elements. */
val LIST_NO_GROUP_LAYOUT_OPTION =
  SurfaceLayoutOption(
    message("new.list.layout.title"),
    if (StudioFlags.COMPOSE_PREVIEW_GROUP_LAYOUT.get())
      ListLayoutManager(organizationListPadding, NO_GROUP_TRANSFORM)
    else GroupedListSurfaceLayoutManager(listPadding, NO_GROUP_TRANSFORM),
    false,
    SceneViewAlignment.LEFT,
  )

/** Grid layout option which doesn't group elements. */
val GRID_NO_GROUP_LAYOUT_OPTION =
  SurfaceLayoutOption(
    message("new.grid.layout.title"),
    if (StudioFlags.COMPOSE_PREVIEW_GROUP_LAYOUT.get())
      GridLayoutManager(organizationGridPadding, NO_GROUP_TRANSFORM)
    else GroupedGridSurfaceLayoutManager(gridPadding, NO_GROUP_TRANSFORM),
    false,
    SceneViewAlignment.LEFT,
  )

/** List layout option without grouping. */
val LIST_LAYOUT_OPTION =
  SurfaceLayoutOption(
    message("new.list.layout.title"),
    GroupedListSurfaceLayoutManager(listPadding, NO_GROUP_TRANSFORM),
    false,
    SceneViewAlignment.LEFT,
  )

/** List layout which groups elements with [GROUP_BY_BASE_COMPONENT] into organization groups. */
val LIST_EXPERIMENTAL_LAYOUT_OPTION =
  SurfaceLayoutOption(
    message("new.list.experimental.layout.title"),
    ListLayoutManager(organizationListPadding, GROUP_BY_BASE_COMPONENT),
    true,
    SceneViewAlignment.LEFT,
  )

/** Grid layout option without grouping. */
val GRID_LAYOUT_OPTION =
  SurfaceLayoutOption(
    message("new.grid.layout.title"),
    GroupedGridSurfaceLayoutManager(gridPadding, NO_GROUP_TRANSFORM),
    false,
    SceneViewAlignment.LEFT,
  )

/** Grid layout which groups elements with [GROUP_BY_BASE_COMPONENT] into organization groups. */
val GRID_EXPERIMENTAL_LAYOUT_OPTION =
  SurfaceLayoutOption(
    message("new.grid.experimental.layout.title"),
    GridLayoutManager(organizationGridPadding, GROUP_BY_BASE_COMPONENT),
    true,
    SceneViewAlignment.LEFT,
  )

/** The default layout that should appear when the Preview is open. */
val DEFAULT_LAYOUT_OPTION = GRID_LAYOUT_OPTION
