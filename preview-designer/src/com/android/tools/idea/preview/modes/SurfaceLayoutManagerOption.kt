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
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.uicheck.UiCheckModeFilter
import com.android.tools.idea.uibuilder.layout.option.FocusLayoutManager
import com.android.tools.idea.uibuilder.layout.option.GridLayoutManager
import com.android.tools.idea.uibuilder.layout.positionable.GROUP_BY_BASE_COMPONENT
import com.android.tools.idea.uibuilder.layout.positionable.NO_GROUP_TRANSFORM

/** [PreviewMode.Focus] layout option which shows once centered element. */
val FOCUS_MODE_LAYOUT_OPTION =
  SurfaceLayoutOption(
    displayName = message("focus.mode.title"),
    createLayoutManager = { FocusLayoutManager() },
    organizationEnabled = false,
    sceneViewAlignment = SceneViewAlignment.LEFT,
    layoutType = SurfaceLayoutOption.LayoutType.Focus,
    shouldStoreScale = false,
  )

/** Grid layout option which doesn't group elements. */
val GRID_NO_GROUP_LAYOUT_OPTION =
  SurfaceLayoutOption(
    displayName = message("grid.layout.title"),
    createLayoutManager = { GridLayoutManager() },
    organizationEnabled = false,
    sceneViewAlignment = SceneViewAlignment.LEFT,
    layoutType = SurfaceLayoutOption.LayoutType.OrganizationGrid,
    shouldStoreScale = false,
  )

/**
 * Grid layout which groups elements with [GROUP_BY_BASE_COMPONENT] into organization groups.
 * Grouping is done by Composable.
 */
val GRID_LAYOUT_OPTION =
  SurfaceLayoutOption(
    displayName = message("grid.layout.title"),
    createLayoutManager = {
      GridLayoutManager(
        transform =
          if (StudioFlags.COMPOSE_PREVIEW_GROUP_LAYOUT.get()) GROUP_BY_BASE_COMPONENT
          else NO_GROUP_TRANSFORM
      )
    },
    organizationEnabled = true,
    sceneViewAlignment = SceneViewAlignment.LEFT,
    layoutType = SurfaceLayoutOption.LayoutType.OrganizationGrid,
  )

/**
 * If organization is enabled - previews are grouped by UI Check type - for example "Screen sizes",
 * "Font scales". See [UiCheckModeFilter] for different types of checks.
 */
val UI_CHECK_LAYOUT_OPTION =
  SurfaceLayoutOption(
    displayName = message("grid.layout.title"),
    createLayoutManager = {
      GridLayoutManager(
        transform =
          if (StudioFlags.COMPOSE_PREVIEW_UI_CHECK_GROUP_LAYOUT.get()) GROUP_BY_BASE_COMPONENT
          else NO_GROUP_TRANSFORM
      )
    },
    organizationEnabled = StudioFlags.COMPOSE_PREVIEW_UI_CHECK_GROUP_LAYOUT.get(),
    sceneViewAlignment = SceneViewAlignment.LEFT,
    layoutType = SurfaceLayoutOption.LayoutType.OrganizationGrid,
    shouldStoreScale = false,
  )

/** The default layout that should appear when the Preview is open. */
val DEFAULT_LAYOUT_OPTION = GRID_LAYOUT_OPTION

/** List of available layouts for the Preview Surface. */
val PREVIEW_LAYOUT_OPTIONS = listOf(GRID_LAYOUT_OPTION, FOCUS_MODE_LAYOUT_OPTION)
