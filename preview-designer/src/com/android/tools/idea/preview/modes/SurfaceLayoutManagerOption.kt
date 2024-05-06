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

import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.GroupedListSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.PositionableGroup
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.VerticalOnlyLayoutManager

/**
 * Wrapper class to define the options available for [SwitchSurfaceLayoutManagerAction].
 *
 * @param displayName Name to be shown for this option.
 * @param layoutManager [SurfaceLayoutManager] to switch to when this option is selected.
 */
data class SurfaceLayoutManagerOption(
  val displayName: String,
  val layoutManager: SurfaceLayoutManager,
  val sceneViewAlignment: DesignSurface.SceneViewAlignment = DesignSurface.SceneViewAlignment.CENTER
)

private const val PREVIEW_LEFT_PADDING = 25
private const val PREVIEW_TOP_PADDING = 5

private val PREVIEW_FRAME_PADDING_PROVIDER: (Double) -> Int = { scale ->
  // Minimum 5 at 20% and maximum 20 at 100%, responsive.
  val min = 5
  val max = 20

  when {
    scale <= 0.2 -> min
    scale >= 1.0 -> max
    else ->
      min + ((max - min) / (1 - 0.2)) * (scale - 0.2) // find interpolated value between min and max
  }.toInt()
}

private val NO_GROUP_TRANSFORM: (Collection<PositionableContent>) -> List<PositionableGroup> = {
  // FIXME(b/258718991): we decide not group the previews for now.
  listOf(PositionableGroup(it.toList()))
}

private val GROUP_BY_BASE_COMPONENT: (Collection<PositionableContent>) -> List<PositionableGroup> =
  { contents ->
    val groups = mutableMapOf<String?, MutableList<PositionableContent>>()
    for (content in contents) {
      groups.getOrPut(content.organizationGroup) { mutableListOf() }.add(content)
    }

    // Put previews which are the only preview in a group last as one group.
    val singles = groups.filter { it.value.size == 1 }
    singles.forEach { groups.remove(it.key) }
    groups.values.map { PositionableGroup(it) } +
      listOf(PositionableGroup(singles.values.flatten()))
  }

/** Toolbar option to select [PreviewMode.Gallery] layout. */
val PREVIEW_LAYOUT_GALLERY_OPTION =
  SurfaceLayoutManagerOption(
    message("gallery.mode.title"),
    GroupedGridSurfaceLayoutManager(5, 0, PREVIEW_FRAME_PADDING_PROVIDER, NO_GROUP_TRANSFORM),
    DesignSurface.SceneViewAlignment.LEFT,
  )

val LIST_LAYOUT_MANAGER_OPTION =
  if (StudioFlags.COMPOSE_PREVIEW_GROUP_LAYOUT.get()) {
    SurfaceLayoutManagerOption(
      // TODO(b/289994157) Change name to "List"
      message("vertical.groups"),
      GroupedListSurfaceLayoutManager(
        PREVIEW_TOP_PADDING,
        PREVIEW_LEFT_PADDING,
        PREVIEW_FRAME_PADDING_PROVIDER,
        GROUP_BY_BASE_COMPONENT,
      ),
      DesignSurface.SceneViewAlignment.LEFT
    )
  } else if (!StudioFlags.COMPOSE_NEW_PREVIEW_LAYOUT.get()) {
    SurfaceLayoutManagerOption(
      message("vertical.layout"),
      VerticalOnlyLayoutManager(
        NlConstants.DEFAULT_SCREEN_OFFSET_X,
        NlConstants.DEFAULT_SCREEN_OFFSET_Y,
        NlConstants.SCREEN_DELTA,
        NlConstants.SCREEN_DELTA,
        SingleDirectionLayoutManager.Alignment.CENTER
      )
    )
  } else {
    SurfaceLayoutManagerOption(
      message("new.list.layout.title"),
      GroupedListSurfaceLayoutManager(
        PREVIEW_TOP_PADDING,
        PREVIEW_LEFT_PADDING,
        PREVIEW_FRAME_PADDING_PROVIDER,
        NO_GROUP_TRANSFORM,
      ),
      DesignSurface.SceneViewAlignment.LEFT
    )
  }

val GRID_LAYOUT_MANAGER_OPTIONS =
  if (StudioFlags.COMPOSE_PREVIEW_GROUP_LAYOUT.get()) {
    SurfaceLayoutManagerOption(
      // TODO(b/289994157) Change name to "Grid"
      message("grid.groups"),
      GroupedGridSurfaceLayoutManager(
        PREVIEW_TOP_PADDING,
        PREVIEW_LEFT_PADDING,
        PREVIEW_FRAME_PADDING_PROVIDER,
        GROUP_BY_BASE_COMPONENT
      ),
      DesignSurface.SceneViewAlignment.LEFT,
    )
  } else if (!StudioFlags.COMPOSE_NEW_PREVIEW_LAYOUT.get()) {
    SurfaceLayoutManagerOption(
      message("grid.layout"),
      GridSurfaceLayoutManager(
        NlConstants.DEFAULT_SCREEN_OFFSET_X,
        NlConstants.DEFAULT_SCREEN_OFFSET_Y,
        NlConstants.SCREEN_DELTA,
        NlConstants.SCREEN_DELTA
      ),
      DesignSurface.SceneViewAlignment.LEFT
    )
  } else {
    SurfaceLayoutManagerOption(
      message("new.grid.layout.title"),
      GroupedGridSurfaceLayoutManager(
        PREVIEW_TOP_PADDING,
        0,
        PREVIEW_FRAME_PADDING_PROVIDER,
        NO_GROUP_TRANSFORM,
      ),
      DesignSurface.SceneViewAlignment.LEFT,
    )
  }
