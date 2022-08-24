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

import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NopSelectionModel
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.surface.InteractionHandler
import com.android.tools.idea.compose.preview.actions.PreviewSurfaceActionManager
import com.android.tools.idea.compose.preview.scene.COMPOSE_SCREEN_VIEW_PROVIDER
import com.android.tools.idea.compose.preview.scene.ComposeSceneComponentProvider
import com.android.tools.idea.compose.preview.scene.ComposeSceneUpdateListener
import com.android.tools.idea.compose.preview.util.ComposeAdapterLightVirtualFile
import com.android.tools.idea.compose.preview.util.ComposePreviewElementInstance
import com.android.tools.idea.compose.preview.util.applyTo
import com.android.tools.idea.preview.PreviewDisplaySettings
import com.android.tools.idea.preview.PreviewElement
import com.android.tools.idea.preview.PreviewElementDebugLogger
import com.android.tools.idea.preview.PreviewElementProvider
import com.android.tools.idea.preview.updatePreviewsAndRefresh
import com.android.tools.idea.uibuilder.actions.SurfaceLayoutManagerOption
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.scene.RealTimeSessionClock
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlSupportedActions
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.VerticalOnlyLayoutManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * List of available layouts for the Compose Preview Surface.
 */
internal val PREVIEW_LAYOUT_MANAGER_OPTIONS = listOf(
  SurfaceLayoutManagerOption(message("vertical.layout"),
                             VerticalOnlyLayoutManager(NlConstants.DEFAULT_SCREEN_OFFSET_X, NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                                                       NlConstants.SCREEN_DELTA, NlConstants.SCREEN_DELTA,
                                                       SingleDirectionLayoutManager.Alignment.CENTER)),
  SurfaceLayoutManagerOption(message("grid.layout"),
                             GridSurfaceLayoutManager(NlConstants.DEFAULT_SCREEN_OFFSET_X, NlConstants.DEFAULT_SCREEN_OFFSET_Y,
                                                      NlConstants.SCREEN_DELTA, NlConstants.SCREEN_DELTA),
                             DesignSurface.SceneViewAlignment.LEFT)
)

/**
 * Default layout manager selected in the preview.
 */
internal val DEFAULT_PREVIEW_LAYOUT_MANAGER = PREVIEW_LAYOUT_MANAGER_OPTIONS.first().layoutManager

private val COMPOSE_SUPPORTED_ACTIONS = setOf(NlSupportedActions.SWITCH_DESIGN_MODE, NlSupportedActions.TOGGLE_ISSUE_PANEL)

/**
 * Creates a [NlDesignSurface.Builder] with a common setup for the design surfaces in Compose
 * preview.
 */
private fun createPreviewDesignSurfaceBuilder(
  project: Project,
  navigationHandler: NlDesignSurface.NavigationHandler,
  delegateInteractionHandler: InteractionHandler,
  dataProvider: DataProvider,
  parentDisposable: Disposable,
  sceneComponentProvider: ComposeSceneComponentProvider
): NlDesignSurface.Builder = NlDesignSurface.builder(project, parentDisposable)
    .setIsPreview(true)
    .setNavigationHandler(navigationHandler)
    .setActionManagerProvider { surface -> PreviewSurfaceActionManager(surface) }
    .setInteractionHandlerProvider { delegateInteractionHandler }
    .setActionHandler { surface -> PreviewSurfaceActionHandler(surface) }
    .setSceneManagerProvider { surface, model ->
      LayoutlibSceneManager(
        model,
        surface,
        sceneComponentProvider,
        ComposeSceneUpdateListener()
      ) { RealTimeSessionClock() }
    }
    .setDelegateDataProvider(dataProvider)
    .setSelectionModel(NopSelectionModel)
    .setZoomControlsPolicy(DesignSurface.ZoomControlsPolicy.HIDDEN)
    .setSupportedActions(COMPOSE_SUPPORTED_ACTIONS)
    .setShouldRenderErrorsPanel(true)
    .setScreenViewProvider(COMPOSE_SCREEN_VIEW_PROVIDER, false)
    .setMaxFitIntoZoomLevel(2.0) // Set fit into limit to 200%

/**
 * Creates a [NlDesignSurface.Builder] for the main design surface in the Compose preview.
 */
internal fun createMainDesignSurfaceBuilder(
  project: Project,
  navigationHandler: NlDesignSurface.NavigationHandler,
  delegateInteractionHandler: InteractionHandler,
  dataProvider: DataProvider,
  parentDisposable: Disposable,
  sceneComponentProvider: ComposeSceneComponentProvider
) = createPreviewDesignSurfaceBuilder(
  project,
  navigationHandler,
  delegateInteractionHandler,
  dataProvider, // Will be overridden by the preview provider
  parentDisposable,
  sceneComponentProvider
).setLayoutManager(DEFAULT_PREVIEW_LAYOUT_MANAGER)

/**
 * Creates a [NlDesignSurface.Builder] for the pinned design surface in the Compose preview.
 */
internal fun createPinnedDesignSurfaceBuilder(
  project: Project,
  navigationHandler: NlDesignSurface.NavigationHandler,
  delegateInteractionHandler: InteractionHandler,
  dataProvider: DataProvider,
  parentDisposable: Disposable,
  sceneComponentProvider: ComposeSceneComponentProvider
) = createPreviewDesignSurfaceBuilder(
  project,
  navigationHandler,
  delegateInteractionHandler,
  dataProvider,
  parentDisposable,
  sceneComponentProvider
).setLayoutManager(
  GridSurfaceLayoutManager(
    NlConstants.DEFAULT_SCREEN_OFFSET_X,
    NlConstants.DEFAULT_SCREEN_OFFSET_Y,
    NlConstants.SCREEN_DELTA,
    NlConstants.SCREEN_DELTA
  )
)

/**
 * Returns a number indicating how [el1] [ComposePreviewElementInstance] is to the [el2] [ComposePreviewElementInstance]. 0 meaning they
 * are equal and higher the number the more dissimilar they are. This allows for, when re-using models, the model with the most similar
 * [ComposePreviewElementInstance] is re-used. When the user is just switching groups or selecting a specific model, this allows switching
 * to the existing preview faster.
 */
fun calcComposeElementsAffinity(el1: ComposePreviewElementInstance, el2: ComposePreviewElementInstance?): Int {
  if (el2 == null) return 3

  return when {
    // These are the same
    el1 == el2 -> 0

    // The method and display settings are the same
    el1.composableMethodFqn == el2.composableMethodFqn &&
    el1.displaySettings == el2.displaySettings -> 1

    // The name of the @Composable method matches but other settings might be different
    el1.composableMethodFqn == el2.composableMethodFqn -> 2

    // No match
    else -> 4
  }
}

/**
 * Class to wrap [ComposePreviewElementInstance]-specific debug logging functionality.
 */
private class ComposeDebugLogger(log: Logger) : PreviewElementDebugLogger<ComposePreviewElementInstance>(log) {

  override fun logPreviewElement(previewElement: ComposePreviewElementInstance, previewXmlContent: String) {
    log("""Preview found at ${stopwatch.duration.toMillis()}ms
        displayName=${previewElement.displaySettings.name}
        methodName=${previewElement.composableMethodFqn}

        $previewXmlContent
     """.trimIndent())
  }
}

/**
 * Compose-specific implementation of [updatePreviewsAndRefresh].
 *
 * If [quickRefresh] is true, the preview surfaces for the same [PreviewElement]s do not get reinflated, allowing to save time for the
 * static to animated preview transition.
 */
internal suspend fun NlDesignSurface.updateComposePreviewsAndRefresh(
  quickRefresh: Boolean,
  previewElementProvider: PreviewElementProvider<ComposePreviewElementInstance>,
  log: Logger,
  psiFile: PsiFile,
  parentDisposable: Disposable,
  progressIndicator: ProgressIndicator,
  onRenderCompleted: () -> Unit,
  previewElementToXml: (ComposePreviewElementInstance) -> String,
  dataContextProvider: (ComposePreviewElementInstance) -> DataContext,
  modelToPreview: NlModel.() -> ComposePreviewElementInstance?,
  configureLayoutlibSceneManager: (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager): List<ComposePreviewElementInstance> {
  val debugLogger = if (log.isDebugEnabled) ComposeDebugLogger(log) else null
  return updatePreviewsAndRefresh(
    !quickRefresh,
    previewElementProvider,
    debugLogger,
    psiFile,
    parentDisposable,
    progressIndicator,
    onRenderCompleted,
    previewElementToXml,
    dataContextProvider,
    modelToPreview,
    ::calcComposeElementsAffinity,
    ComposePreviewElementInstance::applyTo,
    ::ComposeAdapterLightVirtualFile,
    configureLayoutlibSceneManager
  )
}