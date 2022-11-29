/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.compose.preview

import com.android.tools.idea.common.model.NopSelectionModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.compose.preview.actions.PreviewSurfaceActionManager
import com.android.tools.idea.compose.preview.scene.COMPOSE_SCREEN_VIEW_PROVIDER
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeSceneUpdateListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.actions.SurfaceLayoutManagerOption
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.RealTimeSessionClock
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.GroupedListSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.VerticalOnlyLayoutManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project

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

private val NO_GROUP_TRANSFORM:
  (Collection<PositionableContent>) -> List<List<PositionableContent>> =
  {
    // FIXME(b/258718991): we decide not group the previews for now.
    listOf(it.toList())
  }

@Suppress("unused") // b/258718991
private val GROUP_BY_GROUP_ID_TRANSFORM:
  (Collection<PositionableContent>) -> List<List<PositionableContent>> =
  { contents ->
    val groups = mutableMapOf<String?, MutableList<PositionableContent>>()
    for (content in contents) {
      groups.getOrPut(content.groupId) { mutableListOf() }.add(content)
    }
    // Put the previews which don't have group first.
    // TODO(b/245363234)?: Consider to sort the group by name?
    val nulls = groups.remove(null)
    if (nulls != null) listOf(nulls) + groups.values.toList() else groups.values.toList()
  }

/** List of available layouts for the Compose Preview Surface. */
internal val PREVIEW_LAYOUT_MANAGER_OPTIONS =
  listOf(
    SurfaceLayoutManagerOption(
      message("vertical.layout"),
      VerticalOnlyLayoutManager(
        NlConstants.DEFAULT_SCREEN_OFFSET_X,
        NlConstants.DEFAULT_SCREEN_OFFSET_Y,
        NlConstants.SCREEN_DELTA,
        NlConstants.SCREEN_DELTA,
        SingleDirectionLayoutManager.Alignment.CENTER
      )
    ),
    SurfaceLayoutManagerOption(
      message("grid.layout"),
      GridSurfaceLayoutManager(
        NlConstants.DEFAULT_SCREEN_OFFSET_X,
        NlConstants.DEFAULT_SCREEN_OFFSET_Y,
        NlConstants.SCREEN_DELTA,
        NlConstants.SCREEN_DELTA
      ),
      DesignSurface.SceneViewAlignment.LEFT
    ),
  ) +
    if (!StudioFlags.COMPOSE_NEW_PREVIEW_LAYOUT.get()) emptyList()
    else
      listOf(
        SurfaceLayoutManagerOption(
          "Group List Layout (By Group Name)",
          GroupedListSurfaceLayoutManager(5, PREVIEW_FRAME_PADDING_PROVIDER, NO_GROUP_TRANSFORM),
          DesignSurface.SceneViewAlignment.LEFT
        ),
        SurfaceLayoutManagerOption(
          "Group Grid Layout (By Group name)",
          GroupedGridSurfaceLayoutManager(5, PREVIEW_FRAME_PADDING_PROVIDER, NO_GROUP_TRANSFORM),
          DesignSurface.SceneViewAlignment.LEFT
        ),
      )

/** Default layout manager selected in the preview. */
internal val DEFAULT_PREVIEW_LAYOUT_MANAGER = PREVIEW_LAYOUT_MANAGER_OPTIONS.first().layoutManager

private val COMPOSE_SUPPORTED_ACTIONS =
  setOf(NlSupportedActions.SWITCH_DESIGN_MODE, NlSupportedActions.TOGGLE_ISSUE_PANEL)

/**
 * Creates a [NlDesignSurface.Builder] with a common setup for the design surfaces in Compose
 * preview.
 */
private fun createPreviewDesignSurfaceBuilder(
  project: Project,
  navigationHandler: NavigationHandler,
  delegateInteractionHandler: InteractionHandler,
  dataProvider: DataProvider,
  parentDisposable: Disposable,
  sceneComponentProvider: ComposeSceneComponentProvider
): NlDesignSurface.Builder =
  NlDesignSurface.builder(project, parentDisposable)
    .setNavigationHandler(navigationHandler)
    .setActionManagerProvider { surface -> PreviewSurfaceActionManager(surface) }
    .setInteractionHandlerProvider { delegateInteractionHandler }
    .setActionHandler { surface -> PreviewSurfaceActionHandler(surface) }
    .setSceneManagerProvider { surface, model ->
      // Compose Preview manages its own render and refresh logic, and then it should avoid
      // some automatic renderings triggered in LayoutLibSceneManager
      LayoutlibSceneManager(
          model,
          surface,
          sceneComponentProvider,
          ComposeSceneUpdateListener(),
        ) { RealTimeSessionClock() }
        .also {
          it.setListenResourceChange(false) // don't re-render on resource changes
          it.setUpdateAndRenderWhenActivated(false) // don't re-render on activation
        }
    }
    .setDelegateDataProvider(dataProvider)
    .setSelectionModel(NopSelectionModel)
    .setZoomControlsPolicy(DesignSurface.ZoomControlsPolicy.AUTO_HIDE)
    .setSupportedActions(COMPOSE_SUPPORTED_ACTIONS)
    .setShouldRenderErrorsPanel(true)
    .setScreenViewProvider(COMPOSE_SCREEN_VIEW_PROVIDER, false)
    .setMaxFitIntoZoomLevel(2.0) // Set fit into limit to 200%

/** Creates a [NlDesignSurface.Builder] for the main design surface in the Compose preview. */
internal fun createMainDesignSurfaceBuilder(
  project: Project,
  navigationHandler: NavigationHandler,
  delegateInteractionHandler: InteractionHandler,
  dataProvider: DataProvider,
  parentDisposable: Disposable,
  sceneComponentProvider: ComposeSceneComponentProvider
) =
  createPreviewDesignSurfaceBuilder(
      project,
      navigationHandler,
      delegateInteractionHandler,
      dataProvider, // Will be overridden by the preview provider
      parentDisposable,
      sceneComponentProvider
    )
    .setLayoutManager(DEFAULT_PREVIEW_LAYOUT_MANAGER)
