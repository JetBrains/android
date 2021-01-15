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

import com.android.annotations.concurrency.Slow
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlModelBuilder
import com.android.tools.idea.common.model.NopSelectionModel
import com.android.tools.idea.common.model.updateFileContentBlocking
import com.android.tools.idea.common.surface.DelegateInteractionHandler
import com.android.tools.idea.common.surface.DesignSurface
import com.android.tools.idea.common.util.asLogString
import com.android.tools.idea.compose.preview.actions.PreviewSurfaceActionManager
import com.android.tools.idea.compose.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.compose.preview.util.ComposeAdapterLightVirtualFile
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.android.tools.idea.compose.preview.util.layoutlibSceneManagers
import com.android.tools.idea.compose.preview.util.matchElementsToModels
import com.android.tools.idea.compose.preview.util.modelAffinity
import com.android.tools.idea.compose.preview.util.requestComposeRender
import com.android.tools.idea.compose.preview.util.sortByDisplayAndSourcePosition
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.run.util.StopWatch
import com.android.tools.idea.uibuilder.actions.SurfaceLayoutManagerOption
import com.android.tools.idea.uibuilder.graphics.NlConstants
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.uibuilder.surface.NlScreenViewProvider
import com.android.tools.idea.uibuilder.surface.layout.GridSurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.SingleDirectionLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.SurfaceLayoutManager
import com.android.tools.idea.uibuilder.surface.layout.VerticalOnlyLayoutManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import kotlinx.coroutines.future.await
import org.jetbrains.android.facet.AndroidFacet
import java.util.concurrent.CompletableFuture
import java.util.function.BiFunction

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

/**
 * [NlModel.NlModelUpdaterInterface] to be used for updating the Compose model from the Compose render result.
 */
private val modelUpdater: NlModel.NlModelUpdaterInterface = DefaultModelUpdater()

/**
 * Creates a [NlDesignSurface] setup for the Compose preview.
 */
internal fun createPreviewDesignSurface(
  project: Project,
  navigationHandler: NlDesignSurface.NavigationHandler,
  delegateInteractionHandler: DelegateInteractionHandler,
  dataProvider: DataProvider,
  parentDisposable: Disposable,
  zoomControlsPolicy: DesignSurface.ZoomControlsPolicy,
  defaultLayoutManager: SurfaceLayoutManager = DEFAULT_PREVIEW_LAYOUT_MANAGER,
  sceneManagerProvider: BiFunction<NlDesignSurface, NlModel, LayoutlibSceneManager>): NlDesignSurface =
  NlDesignSurface.builder(project, parentDisposable)
    .setIsPreview(true)
    .showModelNames()
    .setNavigationHandler(navigationHandler)
    .setLayoutManager(defaultLayoutManager)
    .setActionManagerProvider { surface -> PreviewSurfaceActionManager(surface) }
    .setInteractionHandlerProvider { delegateInteractionHandler }
    .setActionHandler { surface -> PreviewSurfaceActionHandler(surface) }
    .setSceneManagerProvider(sceneManagerProvider)
    .setEditable(true)
    .setDelegateDataProvider(dataProvider)
    .setSelectionModel(NopSelectionModel)
    .setZoomControlsPolicy(DesignSurface.ZoomControlsPolicy.HIDDEN)
    .build()
    .apply {
      setScreenViewProvider(NlScreenViewProvider.COMPOSE, false)
      setMaxFitIntoZoomLevel(2.0) // Set fit into limit to 200%
    }

/**
 * Refresh the preview with the existing [PreviewElement]s.
 *
 * @param configureLayoutlibSceneManager helper called when the method needs to reconfigure a [LayoutlibSceneManager].
 */
@Slow
internal suspend fun NlDesignSurface.refreshExistingPreviewElements(configureLayoutlibSceneManager: (PreviewElement, LayoutlibSceneManager) -> LayoutlibSceneManager) {
  models.mapNotNull {
    val sceneManager = getSceneManager(it) as? LayoutlibSceneManager ?: return@mapNotNull null
    val previewElement = it.dataContext.getData(COMPOSE_PREVIEW_ELEMENT) ?: return@mapNotNull null
    previewElement to sceneManager
  }
    .forEach {
      val (previewElement, sceneManager) = it
      // When showing decorations, show the full device size
      configureLayoutlibSceneManager(previewElement, sceneManager)
        .requestComposeRender()
        .await()
    }
}

private fun NlDesignSurface.logSurfaceStatus(log: Logger) {
  // Log any rendering errors
  layoutlibSceneManagers.forEach {
    val modelName = it.model.modelDisplayName
    it.renderResult?.let { result ->
      val renderLogger = result.logger
      log.debug("""modelName="$modelName" result
                  | $result
                  | hasErrors=${renderLogger.hasErrors()}
                  | missingClasses=${renderLogger.missingClasses}
                  | messages=${renderLogger.messages.asLogString()}
                  | exceptions=${renderLogger.brokenClasses.values + renderLogger.classesWithIncorrectFormat.values}
                """.trimMargin())
      }
    }
}

/**
 * Syncs the [NlDesignSurface] with the [PreviewElementInstance] from the given [PreviewElementInstanceProvider]. It returns all the
 * [PreviewElementInstance] processed by this method.
 *
 * @param quickRefresh if true, the preview surfaces for the same [PreviewElement]s do not get reinflated, allowing to save time for the static
 * to animated preview transition.
 * @param previewElementProvider the [PreviewElementInstanceProvider] to load the [PreviewElementInstance] from.
 * @param log the [Logger] to log the debug information of the refresh.
 * @param psiFile the [PsiFile] containing the [PreviewElement]s.
 * @param parentDisposable a [Disposable] used as a parent for the elements generated by this call.
 * @param onRenderCompleted method called when all the elements created/updated by this call have finished rendering.
 * @param previewElementToXml helper to convert [PreviewElementInstance] to the XML output used by the surface.
 * @param dataContextProvider helper to provide [DataContext] elements that will be used by this surface.
 * @param configureLayoutlibSceneManager helper called when the method needs to configure a [LayoutlibSceneManager].
 */
@Slow
internal fun NlDesignSurface.updatePreviewsAndRefresh(
  quickRefresh: Boolean,
  previewElementProvider: PreviewElementProvider<PreviewElementInstance>,
  log: Logger,
  psiFile: PsiFile,
  parentDisposable: Disposable,
  onRenderCompleted: () -> Unit,
  previewElementToXml: (PreviewElementInstance) -> String,
  dataContextProvider: (PreviewElementInstance) -> DataContext,
  configureLayoutlibSceneManager: (PreviewElement, LayoutlibSceneManager) -> LayoutlibSceneManager): List<PreviewElementInstance> {
  val stopwatch = if (log.isDebugEnabled) StopWatch() else null
  val facet = AndroidFacet.getInstance(psiFile) ?: return emptyList()
  val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
  // Retrieve the models that were previously displayed so we can reuse them instead of creating new ones.
  val existingModels = models.toMutableList()
  val previewElementsList = previewElementProvider.previewElements.toList().sortByDisplayAndSourcePosition()
  val modelIndices = matchElementsToModels(existingModels, previewElementsList)
  // Now we generate all the models (or reuse) for the PreviewElements.
  val models = previewElementsList
    .map { Pair(it, previewElementToXml(it)) }
    .mapIndexed { idx, it ->
      val (previewElement, fileContents) = it

      if (log.isDebugEnabled) {
        log.debug("""Preview found at ${stopwatch?.duration?.toMillis()}ms
              displayName=${previewElement.displaySettings.name}
              methodName=${previewElement.composableMethodFqn}

              $fileContents
          """.trimIndent())
      }

      val model = if (modelIndices[idx] >= 0) {
        // If model index for this preview element >= 0 then an existing model that can be reused is found. See matchElementsToModels for
        // more details.
        val reusedModel = existingModels[modelIndices[idx]]
        val affinity = modelAffinity(reusedModel, previewElement)
        // If the model is for the same element (affinity=0) and we know that it is not spoiled by previous actions (quickRefresh)
        // we can skip reinflate and therefore refresh much quicker
        val forceReinflate = !(affinity == 0 && quickRefresh)

        log.debug("Re-using model ${reusedModel.virtualFile.name}")
        reusedModel.updateFileContentBlocking(fileContents)
        // Reconfigure the model by setting the new display name and applying the configuration values
        reusedModel.modelDisplayName = previewElement.displaySettings.name
        reusedModel.dataContext = dataContextProvider(previewElement)
        // We call addModel even though the model might not be new. If we try to add an existing model,
        // this will trigger a new render which is exactly what we want.
        configureLayoutlibSceneManager(
          previewElement,
          addModelWithoutRender(reusedModel) as LayoutlibSceneManager).also {
          if (forceReinflate) {
            it.forceReinflate()
          }
        }
        reusedModel
      }
      else {
        val now = System.currentTimeMillis()
        log.debug("No models to reuse were found. New model $now.")
        val file = ComposeAdapterLightVirtualFile("compose-model-$now.xml", fileContents) { psiFile.virtualFile }
        val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
        val newModel = NlModel.builder(facet, file, configuration)
          .withParentDisposable(parentDisposable)
          .withModelDisplayName(previewElement.displaySettings.name)
          .withModelUpdater(modelUpdater)
          .withComponentRegistrar(componentRegistrar)
          .withDataContext(dataContextProvider(previewElement))
          .withXmlProvider { project, virtualFile ->
            NlModelBuilder.getDefaultFile(project, virtualFile).also {
              it.putUserData(ModuleUtilCore.KEY_MODULE, facet.module)
            }
          }
          .build()
        configureLayoutlibSceneManager(
          previewElement,
          addModelWithoutRender(newModel) as LayoutlibSceneManager)
        newModel
      }

      val offset = ReadAction.compute<Int, Throwable> {
        previewElement.previewElementDefinitionPsi?.element?.textOffset ?: 0
      }

      val defaultFile = previewElement.previewElementDefinitionPsi?.virtualFile?.let {
        AndroidPsiUtils.getPsiFileSafely(project, it)
      } ?: psiFile

      (navigationHandler as PreviewNavigationHandler).setDefaultLocation(model, defaultFile, offset)

      previewElement.configuration.applyTo(model.configuration)

      model to previewElement
    }

  existingModels.removeAll(models.map { it.first })

  // Remove and dispose pre-existing models that were not used.
  // This will happen if the user removes one or more previews.
  if (log.isDebugEnabled) log.debug("Removing ${existingModels.size} model(s)")
  existingModels.forEach {
    removeModel(it)
    Disposer.dispose(it)
  }

  val newSceneManagers = models
    .map {
      val (model, _) = it
      getSceneManager(model) as LayoutlibSceneManager
    }

  repaint()
  if (newSceneManagers.isNotEmpty()) {
    CompletableFuture.allOf(*newSceneManagers
      .map { it.requestComposeRender() }
      .toTypedArray())
      .join()
  }
  onRenderCompleted()

  if (log.isDebugEnabled) {
    log.debug("Render completed in ${stopwatch?.duration?.toMillis()}ms")
    logSurfaceStatus(log)
  }

  return models.map { it.second }
}