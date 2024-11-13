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
import com.android.tools.idea.uibuilder.layout.padding.GroupPadding
import com.android.tools.idea.uibuilder.layout.padding.PREVIEW_FRAME_PADDING_PROVIDER
import com.android.tools.idea.uibuilder.layout.positionable.GROUP_BY_BASE_COMPONENT
import com.android.tools.idea.uibuilder.layout.positionable.PositionableGroup
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager

private val NO_GROUP_TRANSFORM: (Collection<PositionableContent>) -> List<PositionableGroup> = {
  listOf(PositionableGroup(it.toList()))
}

private val gridPadding = GroupPadding(5, 0, PREVIEW_FRAME_PADDING_PROVIDER)

/** [PreviewMode.Gallery] layout option which shows once centered element. */
val GALLERY_LAYOUT_OPTION =
  SurfaceLayoutOption(
    message("gallery.mode.title"),
    GalleryLayoutManager(),
    false,
    SceneViewAlignment.LEFT,
  )

/** Grid layout option which doesn't group elements. */
val GRID_NO_GROUP_LAYOUT_OPTION =
  SurfaceLayoutOption(
    message("new.grid.layout.title"),
    if (StudioFlags.COMPOSE_PREVIEW_GROUP_LAYOUT.get()) GridLayoutManager()
    else GroupedGridSurfaceLayoutManager(gridPadding, NO_GROUP_TRANSFORM),
    false,
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
    GridLayoutManager(transform = GROUP_BY_BASE_COMPONENT),
    true,
    SceneViewAlignment.LEFT,
  )

/** The default layout that should appear when the Preview is open. */
val DEFAULT_LAYOUT_OPTION = GRID_LAYOUT_OPTION

/** List of available layouts for the Preview Surface. */
val PREVIEW_LAYOUT_OPTIONS =
  listOf(GRID_LAYOUT_OPTION, GALLERY_LAYOUT_OPTION) +
    if (StudioFlags.COMPOSE_PREVIEW_GROUP_LAYOUT.get()) listOf(GRID_EXPERIMENTAL_LAYOUT_OPTION)
    else emptyList()
