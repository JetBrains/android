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

import com.android.tools.idea.common.model.DefaultSelectionModel
import com.android.tools.idea.common.model.NopSelectionModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.compose.preview.actions.PreviewSurfaceActionManager
import com.android.tools.idea.compose.preview.actions.SurfaceLayoutManagerOption
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeSceneUpdateListener
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.RealTimeSessionClock
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.GroupedGridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.GroupedListSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.PositionableContent
import com.android.tools.idea.uibuilder.surface.layout.PositionableGroup
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.VerticalOnlyLayoutManager
import com.android.tools.rendering.RenderAsyncActionExecutor
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

/** Toolbar option to select [LayoutMode.Gallery] layout. */
internal val PREVIEW_LAYOUT_GALLERY_OPTION =
  SurfaceLayoutManagerOption(
    message("gallery.mode.title"),
    GroupedGridSurfaceLayoutManager(5, 0, PREVIEW_FRAME_PADDING_PROVIDER, NO_GROUP_TRANSFORM),
    DesignSurface.SceneViewAlignment.LEFT,
  )

internal val BASE_LAYOUT_MANAGER_OPTIONS =
  if (StudioFlags.COMPOSE_PREVIEW_GROUP_LAYOUT.get()) {
    listOf(
      SurfaceLayoutManagerOption(
        // TODO(b/289994157) Change name to "List"
        message("vertical.groups"),
        GroupedListSurfaceLayoutManager(5, PREVIEW_FRAME_PADDING_PROVIDER, GROUP_BY_BASE_COMPONENT),
        DesignSurface.SceneViewAlignment.LEFT
      ),
      SurfaceLayoutManagerOption(
        // TODO(b/289994157) Change name to "Grid"
        message("grid.groups"),
        GroupedGridSurfaceLayoutManager(
          5,
          25,
          PREVIEW_FRAME_PADDING_PROVIDER,
          GROUP_BY_BASE_COMPONENT
        ),
        DesignSurface.SceneViewAlignment.LEFT,
      )
    )
  } else if (!StudioFlags.COMPOSE_NEW_PREVIEW_LAYOUT.get()) {
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
      )
    )
  } else {
    listOf(
      SurfaceLayoutManagerOption(
        message("new.list.layout.title"),
        GroupedListSurfaceLayoutManager(5, PREVIEW_FRAME_PADDING_PROVIDER, NO_GROUP_TRANSFORM),
        DesignSurface.SceneViewAlignment.LEFT
      ),
      SurfaceLayoutManagerOption(
        message("new.grid.layout.title"),
        GroupedGridSurfaceLayoutManager(5, 0, PREVIEW_FRAME_PADDING_PROVIDER, NO_GROUP_TRANSFORM),
        DesignSurface.SceneViewAlignment.LEFT,
      )
    )
  }

/** List of available layouts for the Compose Preview Surface. */
internal val PREVIEW_LAYOUT_MANAGER_OPTIONS =
  BASE_LAYOUT_MANAGER_OPTIONS + PREVIEW_LAYOUT_GALLERY_OPTION

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
  sceneComponentProvider: ComposeSceneComponentProvider,
  screenViewProvider: ScreenViewProvider
): NlDesignSurface.Builder =
  NlDesignSurface.builder(project, parentDisposable)
    .setActionManagerProvider { surface -> PreviewSurfaceActionManager(surface, navigationHandler) }
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
        ) {
          RealTimeSessionClock()
        }
        .also {
          it.setListenResourceChange(false) // don't re-render on resource changes
          it.setUpdateAndRenderWhenActivated(false) // don't re-render on activation
          it.setRenderingTopic(RenderAsyncActionExecutor.RenderingTopic.COMPOSE_PREVIEW)
        }
    }
    .setDelegateDataProvider(dataProvider)
    .setSelectionModel(
      if (StudioFlags.COMPOSE_PREVIEW_SELECTION.get()) DefaultSelectionModel()
      else NopSelectionModel
    )
    .setZoomControlsPolicy(DesignSurface.ZoomControlsPolicy.AUTO_HIDE)
    .setSupportedActions(COMPOSE_SUPPORTED_ACTIONS)
    .setShouldRenderErrorsPanel(true)
    .setScreenViewProvider(screenViewProvider, false)
    .setMaxFitIntoZoomLevel(2.0) // Set fit into limit to 200%
    .setMinScale(0.01) // Allow down to 1% zoom level
    .setVisualLintIssueProvider { ComposeVisualLintIssueProvider(it) }

/** Creates a [NlDesignSurface.Builder] for the main design surface in the Compose preview. */
internal fun createMainDesignSurfaceBuilder(
  project: Project,
  navigationHandler: NavigationHandler,
  delegateInteractionHandler: InteractionHandler,
  dataProvider: DataProvider,
  parentDisposable: Disposable,
  sceneComponentProvider: ComposeSceneComponentProvider,
  screenViewProvider: ScreenViewProvider
) =
  createPreviewDesignSurfaceBuilder(
      project,
      navigationHandler,
      delegateInteractionHandler,
      dataProvider, // Will be overridden by the preview provider
      parentDisposable,
      sceneComponentProvider,
      screenViewProvider
    )
    .setLayoutManager(DEFAULT_PREVIEW_LAYOUT_MANAGER)
