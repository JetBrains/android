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
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.common.surface.ZoomControlsPolicy
import com.android.tools.idea.compose.preview.actions.PreviewSurfaceActionManager
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.modes.DEFAULT_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.LIST_EXPERIMENTAL_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.LIST_LAYOUT_OPTION
import com.android.tools.idea.preview.modes.LIST_NO_GROUP_LAYOUT_OPTION
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NavigationHandler
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.surface.NlSurfaceBuilder
import com.android.tools.idea.uibuilder.surface.ScreenViewProvider
import com.android.tools.rendering.RenderAsyncActionExecutor
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project

private val COMPOSE_SUPPORTED_ACTIONS =
  ImmutableSet.of(NlSupportedActions.SWITCH_DESIGN_MODE, NlSupportedActions.TOGGLE_ISSUE_PANEL)

/**
 * Creates a [NlSurfaceBuilder] with a common setup for the design surfaces in Compose preview.
 * [isInteractive] should return when the Preview is in interactive mode. When it is, the
 * [NlDesignSurface] will disable the interception of global shortcuts like refresh ("R") or toggle
 * issue panel ("E").
 */
private fun createPreviewDesignSurfaceBuilder(
  project: Project,
  navigationHandler: NavigationHandler,
  delegateInteractionHandler: InteractionHandler,
  dataProvider: DataProvider,
  parentDisposable: Disposable,
  sceneComponentProvider: ComposeSceneComponentProvider,
  screenViewProvider: ScreenViewProvider,
  isInteractive: () -> Boolean,
): NlSurfaceBuilder =
  NlSurfaceBuilder.builder(project, parentDisposable) { surface, model ->
      // Compose Preview manages its own render and refresh logic, and then it should avoid
      // some automatic renderings triggered in LayoutLibSceneManager
      LayoutlibSceneManager(model, surface, sceneComponentProvider = sceneComponentProvider).also {
        it.sceneRenderConfiguration.layoutScannerConfig.isLayoutScannerEnabled = false
        it.listenResourceChange = false // don't re-render on resource changes
        it.updateAndRenderWhenActivated = false // don't re-render on activation
        it.sceneRenderConfiguration.renderingTopic =
          RenderAsyncActionExecutor.RenderingTopic.COMPOSE_PREVIEW
      }
    }
    .shouldZoomOnFirstComponentResize(false)
    .setActionManagerProvider { surface -> PreviewSurfaceActionManager(surface, navigationHandler) }
    .setInteractionHandlerProvider { delegateInteractionHandler }
    .setActionHandler { surface -> PreviewSurfaceActionHandler(surface) }
    .setDelegateDataProvider(dataProvider)
    .setSelectionModel(
      if (StudioFlags.COMPOSE_PREVIEW_SELECTION.get()) DefaultSelectionModel()
      else NopSelectionModel
    )
    .setZoomControlsPolicy(ZoomControlsPolicy.AUTO_HIDE)
    .setSupportedActionsProvider {
      if (!isInteractive()) COMPOSE_SUPPORTED_ACTIONS else ImmutableSet.of()
    }
    .setShouldRenderErrorsPanel(true)
    .setScreenViewProvider(screenViewProvider, false)
    .setVisualLintIssueProvider { ComposeVisualLintIssueProvider(it) }
    .setShouldShowLayoutDeprecationBanner {
      listOf(LIST_LAYOUT_OPTION, LIST_EXPERIMENTAL_LAYOUT_OPTION, LIST_NO_GROUP_LAYOUT_OPTION)
        .contains(it)
    }

/**
 * Creates a [NlSurfaceBuilder] for the main design surface in the Compose preview. [isInteractive]
 * should return when the Preview is in interactive mode. When it is, the [NlDesignSurface] will
 * disable the interception of global shortcuts like refresh ("R") or toggle issue panel ("E").
 */
internal fun createMainDesignSurfaceBuilder(
  project: Project,
  navigationHandler: NavigationHandler,
  delegateInteractionHandler: InteractionHandler,
  dataProvider: DataProvider,
  parentDisposable: Disposable,
  sceneComponentProvider: ComposeSceneComponentProvider,
  screenViewProvider: ScreenViewProvider,
  isInteractive: () -> Boolean,
) =
  createPreviewDesignSurfaceBuilder(
      project,
      navigationHandler,
      delegateInteractionHandler,
      dataProvider, // Will be overridden by the preview provider
      parentDisposable,
      sceneComponentProvider,
      screenViewProvider,
      isInteractive,
    )
    .setLayoutOption(DEFAULT_LAYOUT_OPTION)
