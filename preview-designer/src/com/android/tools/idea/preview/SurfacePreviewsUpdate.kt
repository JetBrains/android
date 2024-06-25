/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.preview

import com.android.annotations.concurrency.Slow
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlModelUpdaterInterface
import com.android.tools.idea.common.model.updateFileContentBlocking
import com.android.tools.idea.common.scene.render
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.getPsiFileSafely
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.rendering.BuildTargetReference
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.preview.MethodPreviewElement
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.util.io.await
import com.jetbrains.rd.util.getOrCreate
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.backend.common.pop

private fun <T : PreviewElement<*>, M> calcAffinityMatrix(
  elements: List<T>,
  models: List<M>,
  previewElementModelAdapter: PreviewElementModelAdapter<T, M>,
): List<List<Int>> {
  val modelElements = models.map { previewElementModelAdapter.modelToElement(it) }
  return elements.map { element ->
    modelElements.map { previewElementModelAdapter.calcAffinity(element, it) }
  }
}

/**
 * Matches [PreviewElement]s with the most similar models. For a [List] of [PreviewElement]
 * ([elements]) returns a [List] of the same size with the indices of the best matched models. The
 * indices are for the input [models] [List]. If there are less [models] than [elements] then
 * indices for some [PreviewElement]s will be set to -1.
 */
fun <T : PreviewElement<*>, M> matchElementsToModels(
  models: List<M>,
  elements: List<T>,
  previewElementModelAdapter: PreviewElementModelAdapter<T, M>,
): List<Int> {
  val affinityMatrix = calcAffinityMatrix(elements, models, previewElementModelAdapter)
  if (affinityMatrix.isEmpty()) {
    return emptyList()
  }
  val sortedPairs =
    affinityMatrix
      .first()
      .indices
      .flatMap { modelIdx -> affinityMatrix.indices.map { it to modelIdx } }
      .sortedByDescending {
        affinityMatrix[it.first][it.second]
      } // sort in the reverse order to pop from back (quickly)
      .toMutableList()
  val matchedElements = mutableSetOf<Int>()
  val matchedModels = mutableSetOf<Int>()
  val matches = MutableList(affinityMatrix.size) { -1 }

  while (sortedPairs.isNotEmpty()) {
    val (elementIdx, modelIdx) = sortedPairs.pop()
    if (elementIdx in matchedElements || modelIdx in matchedModels) {
      continue
    }
    matches[elementIdx] = modelIdx
    matchedElements.add(elementIdx)
    matchedModels.add(modelIdx)
    // If we either matched all preview elements or all models we have nothing else to do and can
    // finish early
    if (
      matchedElements.size == affinityMatrix.size ||
        matchedModels.size == affinityMatrix.first().size
    ) {
      break
    }
  }
  return matches
}

/**
 * Refresh the preview with the existing [PreviewElement]s.
 *
 * @param progressIndicator [ProgressIndicator] that runs while the refresh is in progress. When
 *   cancelled, this method should return early.
 * @param configureLayoutlibSceneManager helper called when the method needs to reconfigure a
 *   [LayoutlibSceneManager].
 * @param refreshFilter a filter to only refresh some of the existing previews. By default, all of
 *   them are refreshed
 * @param refreshOrder a function that maps scene managers to integers. Then the previews will be
 *   refreshed in ascending order according to these integers. Also, the order between previews
 *   mapped to the same integer is not guaranteed.
 * @param refreshEventBuilder optional [PreviewRefreshEventBuilder] used for collecting metrics
 */
@Slow
suspend fun <T : PreviewElement<*>> NlDesignSurface.refreshExistingPreviewElements(
  progressIndicator: ProgressIndicator,
  modelToPreview: NlModel.() -> T?,
  configureLayoutlibSceneManager:
    (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager,
  refreshFilter: (LayoutlibSceneManager) -> Boolean = { true },
  refreshOrder: (LayoutlibSceneManager) -> Int = { 0 },
  refreshEventBuilder: PreviewRefreshEventBuilder?,
) {
  val previewElementsToSceneManagers =
    sceneManagers.filter(refreshFilter).sortedBy(refreshOrder).mapNotNull {
      val previewElement = modelToPreview(it.model) ?: return@mapNotNull null
      previewElement to it
    }
  refreshEventBuilder?.withPreviewsCount(sceneManagers.size)
  refreshEventBuilder?.withPreviewsToRefresh(previewElementsToSceneManagers.size)
  previewElementsToSceneManagers.forEachIndexed { index, pair ->
    if (progressIndicator.isCanceled)
      return@refreshExistingPreviewElements // Return early if user cancels the refresh.
    progressIndicator.text =
      message(
        "refresh.progress.indicator.rendering.preview",
        index + 1,
        previewElementsToSceneManagers.size,
      )
    val (previewElement, sceneManager) = pair
    // When showing decorations, show the full device size
    renderAndTrack(
      configureLayoutlibSceneManager(previewElement.displaySettings, sceneManager),
      refreshEventBuilder,
    )
  }
}

/**
 * Syncs the [NlDesignSurface] with the [PreviewElement]s from the given [PreviewElementProvider].
 * It returns all the [PreviewElement] processed by this method.
 *
 * @param reinflate if true all the [PreviewElement]s will be forcefully reinflated.
 * @param previewElements the [PreviewElement]s to render.
 * @param log the [Logger] to log the debug information of the refresh.
 * @param psiFile the [PsiFile] containing the [PreviewElement]s.
 * @param parentDisposable a [Disposable] used as a parent for the elements generated by this call.
 * @param progressIndicator [ProgressIndicator] that runs while the refresh is in progress. When
 *   cancelled, this method should return early.
 * @param onRenderCompleted method called when all the elements created/updated by this call have
 *   finished rendering. The elements count is passed as an argument.
 * @param previewElementModelAdapter object to adapt the [PreviewElement]s to the [NlModel].
 * @param modelUpdater [NlModelUpdaterInterface] to be used for updating the [NlModel]
 * @param configureLayoutlibSceneManager helper called when the method needs to configure a
 *   [LayoutlibSceneManager].
 * @param refreshEventBuilder optional [PreviewRefreshEventBuilder] used for collecting metrics
 */
suspend fun <T : PsiPreviewElement> NlDesignSurface.updatePreviewsAndRefresh(
  tryReusingModels: Boolean,
  reinflate: Boolean,
  previewElements: Collection<T>,
  log: Logger,
  psiFile: PsiFile,
  parentDisposable: Disposable,
  progressIndicator: ProgressIndicator,
  onRenderCompleted: (Int) -> Unit,
  previewElementModelAdapter: PreviewElementModelAdapter<T, NlModel>,
  modelUpdater: NlModelUpdaterInterface,
  navigationHandler: PreviewNavigationHandler,
  configureLayoutlibSceneManager:
    (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager,
  refreshEventBuilder: PreviewRefreshEventBuilder?,
): List<T> {
  val debugLogger = if (log.isDebugEnabled) PreviewElementDebugLogger(log) else null

  val (facet, configurationManager) =
    withContext(AndroidDispatchers.workerThread) {
      AndroidFacet.getInstance(psiFile)?.let { facet ->
        return@withContext facet to ConfigurationManager.getOrCreateInstance(facet.module)
      } ?: (null to null)
    }
  if (facet == null || configurationManager == null) return emptyList()
  // Retrieve the models that were previously displayed so we can reuse them instead of creating new
  // ones.
  val existingModels = models.toMutableList()
  val previewElementsList = previewElements.toList().sortByDisplayAndSourcePosition()
  val modelIndices =
    if (tryReusingModels) {
      withContext(AndroidDispatchers.workerThread) {
        matchElementsToModels(existingModels, previewElementsList, previewElementModelAdapter)
      }
    } else List(previewElementsList.size) { -1 }

  // First, remove and dispose pre-existing models that won't be reused.
  // This will happen for example if the user removes one or more previews.
  val elementsToReusableModels =
    previewElementsList.mapIndexed { idx, previewElement ->
      // If model index for this preview element >= 0 then an existing model that can be reused
      // is found. See matchElementsToModels for more details.
      previewElement to if (modelIndices[idx] == -1) null else existingModels[modelIndices[idx]]
    }

  existingModels.removeAll(elementsToReusableModels.mapNotNull { it.second })
  debugLogger?.log("Removing ${existingModels.size} model(s)")
  existingModels.forEach {
    removeModel(it)
    Disposer.dispose(it)
  }

  refreshEventBuilder?.withPreviewsCount(elementsToReusableModels.size)
  refreshEventBuilder?.withPreviewsToRefresh(elementsToReusableModels.size)

  /** Reuse existing organizationGroup. */
  val groups =
    elementsToReusableModels
      .mapNotNull { (_, model) -> model?.organizationGroup }
      .associateBy { it.methodFqn }
      .toMutableMap()

  // Second, reorder the models to reuse and create the new models needed,
  // adding placeholders for all of them, but without rendering anything yet.
  val elementsToSceneManagers =
    elementsToReusableModels.map { (previewElement, model) ->
      if (progressIndicator.isCanceled) {
        // Return early if user cancels the refresh
        return@updatePreviewsAndRefresh previewElementsList
      }
      val fileContents = previewElementModelAdapter.toXml(previewElement)
      debugLogger?.logPreviewElement(
        previewElementModelAdapter.toLogString(previewElement),
        fileContents,
      )

      val newModel: NlModel
      var forceReinflate = true
      var invalidatePreviousRender = false
      if (model != null) {
        debugLogger?.log("Re-using model ${model.virtualFile.name}")
        val affinity =
          previewElementModelAdapter.calcAffinity(
            previewElement,
            previewElementModelAdapter.modelToElement(model),
          )
        // If the model is for the same element (affinity=0) and we know that it is not spoiled by
        // previous actions (reinflate=false) we can skip reinflate and therefore refresh much
        // quicker
        if (affinity == 0 && !reinflate) forceReinflate = false
        // If the model is not the same element (affinity>0), ensure that we do not use the cached
        // result from a previous render.
        if (affinity > 0) invalidatePreviousRender = true
        model.updateFileContentBlocking(fileContents)
        newModel = model
      } else {
        val now = System.currentTimeMillis()
        debugLogger?.log("No models to reuse were found. New model $now.")
        val file =
          previewElementModelAdapter.createLightVirtualFile(fileContents, psiFile.virtualFile, now)
        val configuration =
          Configuration.create(configurationManager, FolderConfiguration.createDefault())
        newModel =
          withContext(AndroidDispatchers.workerThread) {
            NlModel.Builder(
                parentDisposable,
                BuildTargetReference.from(facet, psiFile.virtualFile),
                file,
                configuration,
              )
              .withComponentRegistrar(NlComponentRegistrar)
              .withXmlProvider { project, virtualFile ->
                NlModel.getDefaultFile(project, virtualFile).also {
                  it.putUserData(ModuleUtilCore.KEY_MODULE, facet.module)
                }
              }
              .build()
          }
      }

      // Common configuration steps for new and reused models
      newModel.displaySettings.setDisplayName(previewElement.displaySettings.name)
      newModel.dataContext = previewElementModelAdapter.createDataContext(previewElement)
      newModel.setModelUpdater(modelUpdater)
      (previewElement as? MethodPreviewElement<*>)?.let { methodPreviewElement ->
        newModel.organizationGroup =
          groups.getOrCreate(methodPreviewElement.methodFqn) {
            OrganizationGroup(
              methodPreviewElement.methodFqn,
              methodPreviewElement.displaySettings.name,
            )
          }
      }
      val newSceneManager =
        withContext(AndroidDispatchers.workerThread) { addModelWithoutRender(newModel).await() }
      val sceneManager =
        configureLayoutlibSceneManager(previewElement.displaySettings, newSceneManager).also {
          if (forceReinflate) {
            it.forceReinflate()
          }
          if (invalidatePreviousRender) {
            it.invalidateCachedResponse()
          }
        }

      val offset = runReadAction {
        previewElement.previewElementDefinition?.element?.textOffset ?: 0
      }
      val defaultFile =
        previewElement.previewElementDefinition?.virtualFile?.let { getPsiFileSafely(project, it) }
          ?: psiFile
      navigationHandler.setDefaultLocation(newModel, defaultFile, offset)

      withContext(AndroidDispatchers.workerThread) {
        previewElementModelAdapter.applyToConfiguration(previewElement, newModel.configuration)
      }

      previewElement to sceneManager
    }

  // Relayout the scene views and repaint, so that the updated lists of previews is shown before
  // the renders start, according to the placeholders added above. At this point, reused models
  // will keep their current Preview image and new models will be empty.
  revalidateScrollArea()

  // Finally, render
  var previewsRendered = 0
  elementsToSceneManagers.forEachIndexed { idx, (_, sceneManager) ->
    if (progressIndicator.isCanceled) return@forEachIndexed
    progressIndicator.text =
      message("refresh.progress.indicator.rendering.preview", idx + 1, elementsToSceneManagers.size)
    renderAndTrack(sceneManager, refreshEventBuilder) { if (it == null) previewsRendered++ }
  }
  onRenderCompleted(previewsRendered)

  debugLogger?.logRenderComplete(this)
  log.info("Render completed")
  return elementsToSceneManagers.map { it.first }
}

private suspend fun renderAndTrack(
  sceneManager: LayoutlibSceneManager,
  refreshEventBuilder: PreviewRefreshEventBuilder?,
  onCompleteCallback: (Throwable?) -> Unit = {},
) {
  val inflate = sceneManager.isForceReinflate
  val quality = sceneManager.quality
  val startMs = System.currentTimeMillis()
  sceneManager.render {
    onCompleteCallback(it)
    val renderResult = sceneManager.renderResult
    refreshEventBuilder?.addPreviewRenderDetails(
      renderResult?.isErrorResult() ?: false,
      inflate,
      quality,
      System.currentTimeMillis() - startMs,
      renderResult?.logger?.messages?.singleOrNull()?.throwable?.javaClass?.simpleName,
    )
  }
}
