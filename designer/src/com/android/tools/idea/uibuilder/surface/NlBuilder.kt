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
package com.android.tools.idea.uibuilder.surface

import com.android.tools.idea.common.editor.ActionManager
import com.android.tools.idea.common.layout.SurfaceLayoutOption
import com.android.tools.idea.common.layout.SurfaceLayoutOption.Companion.DEFAULT_OPTION
import com.android.tools.idea.common.model.DefaultSelectionModel
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.SelectionModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.DesignSurfaceActionHandler
import com.android.tools.idea.common.surface.Interactable
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.common.surface.LayoutScannerEnabled
import com.android.tools.idea.common.surface.SurfaceInteractable
import com.android.tools.idea.common.surface.ZoomControlsPolicy
import com.android.tools.idea.concurrency.AndroidCoroutineScope
import com.android.tools.idea.concurrency.AndroidDispatchers.uiThread
import com.android.tools.idea.rendering.RenderSettings.Companion.getProjectSettings
import com.android.tools.idea.uibuilder.editor.NlActionManager
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.visual.visuallint.ViewVisualLintIssueProvider
import com.android.tools.idea.uibuilder.visual.visuallint.VisualLintIssueProvider
import com.google.common.collect.ImmutableSet
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import java.util.function.Supplier
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

private val DEFAULT_NL_SUPPORTED_ACTIONS = ImmutableSet.copyOf(NlSupportedActions.values())

/** Default [LayoutlibSceneManager] provider */
fun defaultSceneManagerProvider(surface: NlDesignSurface, model: NlModel): LayoutlibSceneManager {
  val sceneManager =
    LayoutlibSceneManager(model, surface, layoutScannerConfig = LayoutScannerEnabled())
  val settings = getProjectSettings(model.project)
  sceneManager.sceneRenderConfiguration.let { config ->
    config.showDecorations = settings.showDecorations
    config.useImagePool = settings.useLiveRendering
    config.quality = settings.quality
  }
  return sceneManager
}

/** Default [NlDesignSurfaceActionHandler] provider. */
@VisibleForTesting
fun defaultActionHandlerProvider(
  surface: DesignSurface<LayoutlibSceneManager>
): NlDesignSurfaceActionHandler {
  return NlDesignSurfaceActionHandler(surface)
}

class NlSurfaceBuilder(
  private val project: Project,
  private val parentDisposable: Disposable,
  private val sceneManagerProvider: (NlDesignSurface, NlModel) -> LayoutlibSceneManager,
) {

  companion object {
    fun builder(project: Project, parentDisposable: Disposable): NlSurfaceBuilder {
      return builder(project, parentDisposable) { surface: NlDesignSurface, model: NlModel ->
        defaultSceneManagerProvider(surface, model)
      }
    }

    fun builder(
      project: Project,
      parentDisposable: Disposable,
      provider: (NlDesignSurface, NlModel) -> LayoutlibSceneManager,
    ): NlSurfaceBuilder {
      return NlSurfaceBuilder(project, parentDisposable, provider)
    }

    /** Builds a new [NlDesignSurface] with the default settings */
    @TestOnly
    fun build(project: Project, parentDisposable: Disposable): NlDesignSurface {
      return NlSurfaceBuilder(project, parentDisposable) { surface: NlDesignSurface, model: NlModel
          ->
          defaultSceneManagerProvider(surface, model)
        }
        .build()
    }
  }

  private var surfaceLayoutOption: SurfaceLayoutOption? = null

  /**
   * An optional [DataProvider] that allows users of the surface to provide additional information
   * associated with this surface.
   */
  private var _delegateDataProvider: DataProvider? = null

  /** Factory to create an action manager for the DesignSurface */
  private var _actionManagerProvider:
    (DesignSurface<LayoutlibSceneManager>) -> ActionManager<DesignSurface<LayoutlibSceneManager>> =
    {
      NlActionManager(it as NlDesignSurface)
    }

  /**
   * Factory to create an [Interactable] for the DesignSurface. It should only be modified for
   * tests.
   */
  private var _interactableProvider: (DesignSurface<LayoutlibSceneManager>) -> Interactable = {
    SurfaceInteractable(it)
  }

  /** Factory to create an [InteractionHandler] for the [DesignSurface]. */
  private var _interactionHandlerProvider:
    (DesignSurface<LayoutlibSceneManager>) -> InteractionHandler =
    {
      NlInteractionHandler(it)
    }
  private var _actionHandlerProvider:
    (DesignSurface<LayoutlibSceneManager>) -> DesignSurfaceActionHandler<
        DesignSurface<LayoutlibSceneManager>
      > =
    {
      defaultActionHandlerProvider(it)
    }

  private var _selectionModel: SelectionModel? = null
  private var _zoomControlsPolicy = ZoomControlsPolicy.AUTO_HIDE
  private var _supportedActionsProvider = Supplier { DEFAULT_NL_SUPPORTED_ACTIONS }

  private var _shouldRenderErrorsPanel = false

  private var _screenViewProvider: ScreenViewProvider? = null
  private var _setDefaultScreenViewProvider = false
  private var _waitForRenderBeforeRestoringZoom = false

  private var _visualLintIssueProviderFactory:
    (DesignSurface<LayoutlibSceneManager>) -> VisualLintIssueProvider =
    {
      ViewVisualLintIssueProvider(it)
    }

  /** Allows customizing the [SurfaceLayoutOption]. */
  fun setLayoutOption(layoutOption: SurfaceLayoutOption): NlSurfaceBuilder {
    surfaceLayoutOption = layoutOption
    return this
  }

  /**
   * The surface will wait for other events (for example preview rendering) before trying to restore
   * zoom.
   */
  fun waitForRenderBeforeRestoringZoom(restoreZoomSynchronously: Boolean): NlSurfaceBuilder {
    _waitForRenderBeforeRestoringZoom = restoreZoomSynchronously
    return this
  }

  /**
   * Allows customizing the [ActionManager]. Use this method if you need to apply additional
   * settings to it or if you need to completely replace it, for example for tests.
   */
  fun setActionManagerProvider(
    actionManagerProvider:
      (DesignSurface<LayoutlibSceneManager>) -> ActionManager<DesignSurface<LayoutlibSceneManager>>
  ): NlSurfaceBuilder {
    _actionManagerProvider = actionManagerProvider
    return this
  }

  /**
   * Allows to define the [Interactable] factory that will later be used to generate the
   * [Interactable] over which the [InteractionHandler] will be placed.
   */
  @TestOnly
  fun setInteractableProvider(
    interactableProvider: (DesignSurface<LayoutlibSceneManager>) -> Interactable
  ): NlSurfaceBuilder {
    _interactableProvider = interactableProvider
    return this
  }

  /**
   * Allows customizing the [InteractionHandler]. Use this method if you need to apply different
   * interaction behavior to the [DesignSurface].
   *
   * Default is [NlInteractionHandler].
   */
  fun setInteractionHandlerProvider(
    interactionHandlerProvider: (DesignSurface<LayoutlibSceneManager>) -> InteractionHandler
  ): NlSurfaceBuilder {
    _interactionHandlerProvider = interactionHandlerProvider
    return this
  }

  /** Sets the [DesignSurfaceActionHandler] provider for this surface. */
  fun setActionHandler(
    actionHandlerProvider:
      (DesignSurface<LayoutlibSceneManager>) -> DesignSurfaceActionHandler<
          DesignSurface<LayoutlibSceneManager>
        >
  ): NlSurfaceBuilder {
    _actionHandlerProvider = actionHandlerProvider
    return this
  }

  /**
   * Sets a delegate [DataProvider] that allows users of the surface to provide additional
   * information associated with this surface.
   */
  fun setDelegateDataProvider(dataProvider: DataProvider): NlSurfaceBuilder {
    _delegateDataProvider = dataProvider
    return this
  }

  /** Sets a new [SelectionModel] for this surface. */
  fun setSelectionModel(selectionModel: SelectionModel): NlSurfaceBuilder {
    _selectionModel = selectionModel
    return this
  }

  /** The surface will auto-hide the zoom controls when the mouse is not over it. */
  fun setZoomControlsPolicy(policy: ZoomControlsPolicy): NlSurfaceBuilder {
    _zoomControlsPolicy = policy
    return this
  }

  /**
   * Set the supported [NlSupportedActions] for the built DesignSurface. These actions are
   * registered by xml and can be found globally, we need to assign if the built DesignSurface
   * supports it or not. By default, the builder assumes there is no supported [NlSupportedActions].
   * <br></br><br></br> Be aware the [com.intellij.openapi.actionSystem.AnAction]s registered by
   * code are not effected.
   *
   * TODO(b/183243031): These mechanism should be integrated into [ActionManager].
   */
  fun setSupportedActionsProvider(
    supportedActionsProvider: Supplier<ImmutableSet<NlSupportedActions>>
  ): NlSurfaceBuilder {
    _supportedActionsProvider = supportedActionsProvider
    return this
  }

  /** See [.setSupportedActionsProvider]. This method will create a copy of the given set. */
  fun setSupportedActions(supportedActions: Set<NlSupportedActions>): NlSurfaceBuilder {
    val supportedActionsCopy = ImmutableSet.copyOf(supportedActions)
    setSupportedActionsProvider { supportedActionsCopy }
    return this
  }

  fun setShouldRenderErrorsPanel(shouldRenderErrorsPanel: Boolean): NlSurfaceBuilder {
    _shouldRenderErrorsPanel = shouldRenderErrorsPanel
    return this
  }

  fun setScreenViewProvider(
    screenViewProvider: ScreenViewProvider,
    setAsDefault: Boolean,
  ): NlSurfaceBuilder {
    _screenViewProvider = screenViewProvider
    _setDefaultScreenViewProvider = setAsDefault
    return this
  }

  fun setVisualLintIssueProvider(
    issueProviderFactory: (DesignSurface<LayoutlibSceneManager>) -> VisualLintIssueProvider
  ): NlSurfaceBuilder {
    _visualLintIssueProviderFactory = issueProviderFactory
    return this
  }

  fun build(): NlDesignSurface {
    val nlDesignSurfacePositionableContentLayoutManager =
      NlDesignSurfacePositionableContentLayoutManager(surfaceLayoutOption ?: DEFAULT_OPTION)
    val surface =
      NlDesignSurface(
        project,
        sceneManagerProvider,
        _actionManagerProvider,
        _interactableProvider,
        _interactionHandlerProvider,
        _actionHandlerProvider,
        _delegateDataProvider,
        _selectionModel ?: DefaultSelectionModel(),
        _zoomControlsPolicy,
        _supportedActionsProvider,
        _shouldRenderErrorsPanel,
        _waitForRenderBeforeRestoringZoom,
        _visualLintIssueProviderFactory,
        nlDesignSurfacePositionableContentLayoutManager,
      )

    Disposer.register(parentDisposable, surface)
    Disposer.register(surface, nlDesignSurfacePositionableContentLayoutManager)

    nlDesignSurfacePositionableContentLayoutManager.surface = surface
    AndroidCoroutineScope(surface).launch {
      nlDesignSurfacePositionableContentLayoutManager.currentLayoutOption.collect {
        withContext(uiThread) { surface.onLayoutUpdated(it) }
      }
    }

    _screenViewProvider?.let { surface.setScreenViewProvider(it, _setDefaultScreenViewProvider) }

    return surface
  }
}
