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
import com.android.tools.idea.common.model.DefaultModelUpdater
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlModelBuilder
import com.android.tools.idea.common.model.updateFileContentBlocking
import com.android.tools.idea.common.scene.render
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.getPsiFileSafely
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.kotlin.backend.common.pop

/**
 * [NlModel.NlModelUpdaterInterface] to be used for updating the Compose model from the Compose render result.
 */
private val modelUpdater: NlModel.NlModelUpdaterInterface = DefaultModelUpdater()

private fun <T : PreviewElement, M> calcAffinityMatrix(
  elements: List<T>, models: List<M>, previewElementModelAdapter: PreviewElementModelAdapter<T, M>): List<List<Int>> {
  val modelElements = models.map { previewElementModelAdapter.modelToElement(it) }
  return elements.map { element -> modelElements.map { previewElementModelAdapter.calcAffinity(element, it) } }
}

/**
 * Matches [PreviewElement]s with the most similar models. For a [List] of [PreviewElement] ([elements]) returns a [List] of the same
 * size with the indices of the best matched models. The indices are for the input [models] [List]. If there are less [models] than
 * [elements] then indices for some [PreviewElement]s will be set to -1.
 */
fun <T : PreviewElement, M> matchElementsToModels(
  models: List<M>, elements: List<T>, previewElementModelAdapter: PreviewElementModelAdapter<T, M>
): List<Int> {
  val affinityMatrix = calcAffinityMatrix(elements, models, previewElementModelAdapter)
  if (affinityMatrix.isEmpty()) {
    return emptyList()
  }
  val sortedPairs =
    affinityMatrix.first().indices
      .flatMap { modelIdx -> affinityMatrix.indices.map { it to modelIdx } }
      .sortedByDescending { affinityMatrix[it.first][it.second] } // sort in the reverse order to pop from back (quickly)
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
    // If we either matched all preview elements or all models we have nothing else to do and can finish early
    if (matchedElements.size == affinityMatrix.size || matchedModels.size == affinityMatrix.first().size) {
      break
    }
  }
  return matches
}

/**
 * Refresh the preview with the existing [PreviewElement]s.
 *
 * @param progressIndicator [ProgressIndicator] that runs while the refresh is in progress. When cancelled, this method should return early.
 * @param configureLayoutlibSceneManager helper called when the method needs to reconfigure a [LayoutlibSceneManager].
 */
@Slow
suspend fun <T : PreviewElement> NlDesignSurface.refreshExistingPreviewElements(
  progressIndicator: ProgressIndicator,
  modelToPreview: NlModel.() -> T?,
  configureLayoutlibSceneManager: (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager
) {
  val previewElementsToSceneManagers = models.mapNotNull {
    val sceneManager = getSceneManager(it) ?: return@mapNotNull null
    val previewElement = modelToPreview(it) ?: return@mapNotNull null
    previewElement to sceneManager
  }
  previewElementsToSceneManagers
    .forEachIndexed { index, pair ->
      if (progressIndicator.isCanceled) return@refreshExistingPreviewElements // Return early if user cancels the refresh.
      progressIndicator.text = message("refresh.progress.indicator.rendering.preview", index + 1, previewElementsToSceneManagers.size)
      val (previewElement, sceneManager) = pair
      // When showing decorations, show the full device size
      configureLayoutlibSceneManager(previewElement.displaySettings, sceneManager).requestDoubleRender()
    }
}

/**
 * Syncs the [NlDesignSurface] with the [PreviewElement]s from the given [PreviewElementProvider]. It returns all the
 * [PreviewElement] processed by this method.
 *
 * @param reinflate if true all the [PreviewElement]s will be forcefully reinflated.
 * @param previewElementProvider the [PreviewElementProvider] to load the [PreviewElement]s from.
 * @param log the [Logger] to log the debug information of the refresh.
 * @param psiFile the [PsiFile] containing the [PreviewElement]s.
 * @param parentDisposable a [Disposable] used as a parent for the elements generated by this call.
 * @param progressIndicator [ProgressIndicator] that runs while the refresh is in progress. When cancelled, this method should return early.
 * @param onRenderCompleted method called when all the elements created/updated by this call have finished rendering.
 * @param previewElementModelAdapter object to adapt the [PreviewElement]s to the [NlModel].
 * @param configureLayoutlibSceneManager helper called when the method needs to configure a [LayoutlibSceneManager].
 */
suspend fun <T : PreviewElement> NlDesignSurface.updatePreviewsAndRefresh(
  reinflate: Boolean,
  previewElementProvider: PreviewElementProvider<T>,
  log: Logger,
  psiFile: PsiFile,
  parentDisposable: Disposable,
  progressIndicator: ProgressIndicator,
  onRenderCompleted: () -> Unit,
  previewElementModelAdapter: PreviewElementModelAdapter<T, NlModel>,
  configureLayoutlibSceneManager: (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager): List<T> {
  val debugLogger = if (log.isDebugEnabled) PreviewElementDebugLogger(log) else null
  val facet = AndroidFacet.getInstance(psiFile) ?: return emptyList()
  val configurationManager = ConfigurationManager.getOrCreateInstance(facet)
  // Retrieve the models that were previously displayed so we can reuse them instead of creating new ones.
  val existingModels = models.toMutableList()
  val previewElementsList = previewElementProvider.previewElements().toList().sortByDisplayAndSourcePosition()
  val modelIndices = matchElementsToModels(existingModels, previewElementsList, previewElementModelAdapter)
  // Now we generate all the models (or reuse) for the PreviewElements.
  val models = previewElementsList
    .mapIndexed { idx, previewElement ->
      val fileContents = previewElementModelAdapter.toXml(previewElement)

      debugLogger?.logPreviewElement(previewElementModelAdapter.toLogString(previewElement), fileContents)
      if (progressIndicator.isCanceled) return@updatePreviewsAndRefresh previewElementsList // Return early if user cancels the refresh

      val model = if (modelIndices[idx] >= 0) {
        // If model index for this preview element >= 0 then an existing model that can be reused is found. See matchElementsToModels for
        // more details.
        val reusedModel = existingModels[modelIndices[idx]]
        val affinity = previewElementModelAdapter.calcAffinity(previewElement, previewElementModelAdapter.modelToElement(reusedModel))
        // If the model is for the same element (affinity=0) and we know that it is not spoiled by previous actions (reinflate=false)
        // we can skip reinflate and therefore refresh much quicker
        val forceReinflate = reinflate || affinity != 0

        debugLogger?.log("Re-using model ${reusedModel.virtualFile.name}")
        reusedModel.updateFileContentBlocking(fileContents)
        // Reconfigure the model by setting the new display name and applying the configuration values
        reusedModel.modelDisplayName = previewElement.displaySettings.name
        reusedModel.dataContext = previewElementModelAdapter.createDataContext(previewElement)
        // We call addModel even though the model might not be new. If we try to add an existing model,
        // this will trigger a new render which is exactly what we want.
        configureLayoutlibSceneManager(
          previewElement.displaySettings,
          addModelWithoutRender(reusedModel)).also {
          if (forceReinflate) {
            it.forceReinflate()
          }
        }
        reusedModel
      }
      else {
        val now = System.currentTimeMillis()
        debugLogger?.log("No models to reuse were found. New model $now.")
        val file = previewElementModelAdapter.createLightVirtualFile(fileContents, psiFile.virtualFile, now)
        val configuration = Configuration.create(configurationManager, null, FolderConfiguration.createDefault())
        withContext(AndroidDispatchers.workerThread) {
          val newModel = NlModel.builder(facet, file, configuration)
            .withParentDisposable(parentDisposable)
            .withModelDisplayName(previewElement.displaySettings.name)
            .withModelUpdater(modelUpdater)
            .withComponentRegistrar(NlComponentRegistrar)
            .withDataContext(previewElementModelAdapter.createDataContext(previewElement))
            .withXmlProvider { project, virtualFile ->
              NlModelBuilder.getDefaultFile(project, virtualFile).also {
                it.putUserData(ModuleUtilCore.KEY_MODULE, facet.module)
              }
            }
            .build()
          configureLayoutlibSceneManager(
            previewElement.displaySettings,
            addModelWithoutRender(newModel))
          newModel.groupId = previewElement.displaySettings.group
          newModel
        }
      }
      if (progressIndicator.isCanceled) return@updatePreviewsAndRefresh previewElementsList // Return early if user cancels the refresh

      val offset = runReadAction {
        previewElement.previewElementDefinitionPsi?.element?.textOffset ?: 0
      }

      val defaultFile = previewElement.previewElementDefinitionPsi?.virtualFile?.let {
        getPsiFileSafely(project, it)
      } ?: psiFile

      (navigationHandler as? PreviewNavigationHandler)?.setDefaultLocation(model, defaultFile, offset)

      previewElementModelAdapter.applyToConfiguration(previewElement, model.configuration)

      model to previewElement
    }
  if (progressIndicator.isCanceled) return previewElementsList // Return early if user cancels the refresh

  existingModels.removeAll(models.map { it.first })

  // Remove and dispose pre-existing models that were not used.
  // This will happen if the user removes one or more previews.
  debugLogger?.log("Removing ${existingModels.size} model(s)")
  existingModels.forEach {
    removeModel(it)
    Disposer.dispose(it)
  }

  val newSceneManagers = models
    .map {
      val (model, _) = it
      getSceneManager(model)!!
    }

  // Relayout the scene views and repaint, so that the updated lists of previews is shown before the render starts.
  // While rendering, reused models will keep their current Preview image and new models will be empty.
  revalidateScrollArea()
  if (newSceneManagers.isNotEmpty()) {
    var preview = 1 // next preview to render
    progressIndicator.text = message("refresh.progress.indicator.rendering.preview", preview++, newSceneManagers.size)
    newSceneManagers.forEach {
      if (progressIndicator.isCanceled) return@forEach
      it.render()
      if (preview <= newSceneManagers.size) { // Skip the last one, since we log *before* rendering each preview.
        progressIndicator.text = message("refresh.progress.indicator.rendering.preview", preview++, newSceneManagers.size)
      }
    }
  }
  onRenderCompleted()

  debugLogger?.logRenderComplete(this)

  return models.map { it.second }
}