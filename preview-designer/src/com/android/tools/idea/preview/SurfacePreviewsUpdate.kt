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

import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.configurations.Configuration
import com.android.tools.idea.common.model.DisplaySettings
import com.android.tools.idea.common.model.NlModel
import com.android.tools.idea.common.model.NlModelUpdaterInterface
import com.android.tools.idea.common.model.updateFileContentBlocking
import com.android.tools.idea.common.surface.DesignSurfaceSettings.Companion.getInstance
import com.android.tools.idea.common.surface.organization.OrganizationGroup
import com.android.tools.idea.common.surface.organization.OrganizationGroupType
import com.android.tools.idea.concurrency.AndroidDispatchers
import com.android.tools.idea.concurrency.getPsiFileSafely
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.flags.StudioFlags
import com.android.tools.idea.preview.PreviewBundle.message
import com.android.tools.idea.preview.analytics.PreviewRefreshEventBuilder
import com.android.tools.idea.preview.navigation.PreviewNavigationHandler
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.rendering.isErrorResult
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager
import com.android.tools.idea.uibuilder.surface.NlDesignSurface
import com.android.tools.idea.util.findAndroidModule
import com.android.tools.preview.PreviewDisplaySettings
import com.android.tools.preview.PreviewElement
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.psi.PsiFile
import com.jetbrains.rd.util.getOrCreate
import kotlinx.coroutines.withContext
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.util.projectStructure.module

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
 * Syncs the [NlDesignSurface] with the given [previewElements].
 *
 * Returns all the [PreviewElement]s processed by this method.
 *
 * @param reinflate if true all the [PreviewElement]s will be forcefully reinflated.
 * @param previewElements the [PreviewElement]s to render.
 * @param log the [Logger] to log the debug information of the refresh.
 * @param psiFile the [PsiFile] containing the [PreviewElement]s.
 * @param parentDisposable a [Disposable] used as a parent for the elements generated by this call.
 * @param progressIndicator [ProgressIndicator] that runs while the refresh is in progress. When
 *   cancelled, this method should return early.
 * @param previewElementModelAdapter object to adapt the [PreviewElement]s to the [NlModel].
 * @param modelUpdater [NlModelUpdaterInterface] to be used for updating the [NlModel]
 * @param configureLayoutlibSceneManager helper called when the method needs to configure a
 *   [LayoutlibSceneManager].
 * @param refreshEventBuilder optional [PreviewRefreshEventBuilder] used for collecting metrics
 */
suspend fun <T : PsiPreviewElement> NlDesignSurface.updatePreviewsAndRefresh(
  reinflate: Boolean,
  previewElements: Collection<T>,
  log: Logger,
  psiFile: PsiFile,
  parentDisposable: Disposable,
  progressIndicator: ProgressIndicator,
  previewElementModelAdapter: PreviewElementModelAdapter<T, NlModel>,
  modelUpdater: NlModelUpdaterInterface,
  navigationHandler: PreviewNavigationHandler,
  configureLayoutlibSceneManager:
    (PreviewDisplaySettings, LayoutlibSceneManager) -> LayoutlibSceneManager,
  refreshEventBuilder: PreviewRefreshEventBuilder?,
): List<T> {
  val debugLogger = if (log.isDebugEnabled) PreviewElementDebugLogger(log) else null

  val (facet, configurationManager) =
    readAction { psiFile.module }
      ?.findAndroidModule()
      ?.let { module -> AndroidFacet.getInstance(module) }
      ?.let { facet -> facet to ConfigurationManager.getOrCreateInstance(facet.module) }
      ?: (null to null)
  if (facet == null || configurationManager == null) return emptyList()
  // Retrieve the models that were previously displayed so we can reuse them instead of creating new
  // ones.
  val existingModels = models
  val previewElementsList = previewElements.toList()
  val modelIndices =
    matchElementsToModels(existingModels, previewElementsList, previewElementModelAdapter)

  // First, remove and dispose pre-existing models that won't be reused.
  // This will happen for example if the user removes one or more previews.
  val elementsToReusableModels =
    previewElementsList.mapIndexed { idx, previewElement ->
      // If model index for this preview element >= 0 then an existing model that can be reused
      // is found. See matchElementsToModels for more details.
      previewElement to if (modelIndices[idx] == -1) null else existingModels[modelIndices[idx]]
    }

  val notReusedModels = existingModels - elementsToReusableModels.mapNotNull { it.second }.toSet()
  debugLogger?.log("Removing ${notReusedModels.size} model(s)")
  removeModels(notReusedModels)

  refreshEventBuilder?.withPreviewsCount(elementsToReusableModels.size)
  refreshEventBuilder?.withPreviewsToRefresh(elementsToReusableModels.size)

  // Load organization group state.
  val previousOrganizationState =
    getInstance(project).surfaceState.getOrganizationGroupState(psiFile.virtualFile)

  // Reuse existing groups.
  val groups =
    elementsToReusableModels
      .mapNotNull { (_, model) -> model?.organizationGroup }
      .associateBy { it.groupId }
      .toMutableMap()

  // Second, reorder the models to reuse and create the new models needed,
  // adding placeholders for all of them, but without rendering anything yet.
  val elementsToSceneManagers =
    elementsToReusableModels
      .map { (previewElement, model) ->
        val newModel: NlModel =
          createOrReuseModelForPreviewElement(
            model,
            debugLogger,
            previewElementModelAdapter,
            previewElement,
            reinflate,
            psiFile,
            configurationManager,
            parentDisposable,
            facet,
          )

        copySettingsInto(
          from = previewElement.displaySettings,
          to = newModel.displaySettings,
          psiFile,
        )

        newModel.dataProvider = previewElementModelAdapter.createDataProvider(previewElement)
        newModel.setModelUpdater(modelUpdater)

        // groupId - fully qualified name of composable
        // displayName - name of composable for example "MyComposableName - Font sizes" or
        // "MyComposableName"
        newModel.organizationGroup =
          groups.getOrCreate(previewElement.displaySettings.organizationGroup) { groupId ->
            val displayName =
              previewElement.displaySettings.organizationName ?: previewElement.displaySettings.name
            val fileAndDisplayName =
              newModel.displaySettings.fileName.value?.let { "$it.$displayName" } ?: displayName
            OrganizationGroup(
              groupId = groupId,
              displayName = fileAndDisplayName,
              groupType = newModel.displaySettings.groupType.value,
              defaultOpenedState =
                previousOrganizationState[groupId]
                  ?: newModel.displaySettings.groupType.value.defaultGroupState,
            ) {
              // Everytime state is changed we need to save it.
              isOpened ->
              getInstance(project)
                .surfaceState
                .saveOrganizationGroupState(psiFile.virtualFile, groupId, isOpened)
            }
          }

        val offset = runReadAction {
          previewElement.previewElementDefinition?.element?.textOffset ?: 0
        }
        val defaultFile =
          previewElement.previewElementDefinition?.virtualFile?.let {
            getPsiFileSafely(project, it)
          } ?: psiFile
        navigationHandler.setDefaultLocation(newModel, defaultFile, offset)
        previewElementModelAdapter.applyToConfiguration(previewElement, newModel.configuration)

        previewElement to newModel
      }
      .let { elementModelList ->
        // Reorder existing models and add placeholders altogether to improve performance and UX in
        // comparison with adding/reordering them one by one.
        this.addModelsWithoutRender(elementModelList.map { it.second })
          .mapIndexed { idx, sceneManager ->
            val previewElement = elementModelList[idx].first
            previewElement to
              configureLayoutlibSceneManager(previewElement.displaySettings, sceneManager)
          }
      }

  // Relayout the scene views and repaint, so that the updated lists of previews is shown before
  // the renders start, according to the placeholders added above. At this point, reused models
  // will keep their current Preview image and new models will be empty.
  withContext(AndroidDispatchers.uiThread) { revalidateScrollArea() }

  // Finally, render
  elementsToSceneManagers.forEachIndexed { idx, (_, sceneManager) ->
    progressIndicator.text =
      message("refresh.progress.indicator.rendering.preview", idx + 1, elementsToSceneManagers.size)

    // Some components (e.g. Popup) delay their content placement and wrap them into a coroutine
    // controlled by the clock. For that reason, we need to execute layoutlib callbacks and
    // re-render, to make sure the queued behaviors are triggered and displayed in static preview.
    sceneManager.sceneRenderConfiguration.executeCallbacksAfterRender.set(true)
    sceneManager.sceneRenderConfiguration.doubleRender.set(true)
    renderAndTrack(sceneManager, refreshEventBuilder)
  }

  debugLogger?.logRenderComplete(this)
  log.info("Render completed")
  return elementsToSceneManagers.map { it.first }
}

fun copySettingsInto(from: PreviewDisplaySettings, to: DisplaySettings, psiFile: PsiFile) {
  // Common configuration steps for new and reused models
  to.setDisplayName(from.name)
  to.setBaseName(from.baseName)
  to.setParameterName(from.parameterName)

  // TODO(b/407525144) Set correct icon and do not set file name if preview opened from the
  // same file.
  if (StudioFlags.PREVIEW_SOURCESET_UI.get()) {
    to.setFileName(psiFile.name)
    to.setGroupType(OrganizationGroupType.Test)
  }
}

/**
 * Creates a new [NlModel] or reuses an existing one.
 *
 * @return the [NlModel] to use for rendering.
 */
@VisibleForTesting
suspend fun <T : PsiPreviewElement> NlDesignSurface.createOrReuseModelForPreviewElement(
  modelToReuse: NlModel?,
  debugLogger: PreviewElementDebugLogger?,
  previewElementModelAdapter: PreviewElementModelAdapter<T, NlModel>,
  previewElement: T,
  reinflate: Boolean,
  psiFile: PsiFile,
  configurationManager: ConfigurationManager,
  parentDisposable: Disposable,
  facet: AndroidFacet,
): NlModel {
  val fileContents = previewElementModelAdapter.toXml(previewElement)
  debugLogger?.logPreviewElement(
    previewElementModelAdapter.toLogString(previewElement),
    fileContents,
  )
  var newModel: NlModel
  val newConfiguration: Configuration =
    Configuration.create(configurationManager, FolderConfiguration.createDefault()).also {
      // Always use the imageTransformation from the surface, regardless of whether the model is
      // reused or new.
      it.imageTransformation = this.getGlobalImageTransformation()
    }
  if (modelToReuse != null) {
    var forceReinflate = true
    var invalidatePreviousRender = false
    debugLogger?.log("Re-using model ${modelToReuse.virtualFile.name}")
    val affinity =
      calcAffinity(previewElement, previewElementModelAdapter.modelToElement(modelToReuse))
    // If the model is for the same element (affinity=0) and we know that it is not spoiled by
    // previous actions (reinflate=false) we can skip reinflate and therefore refresh much
    // quicker
    if (affinity == 0 && !reinflate) forceReinflate = false
    // If the model is not the same element (affinity>0), ensure that we do not use the cached
    // result from a previous render.
    if (affinity > 0) invalidatePreviousRender = true
    modelToReuse.updateFileContentBlocking(fileContents)
    // We shouldn't reuse the configuration from a previous render when we reuse the model.
    modelToReuse.configuration = newConfiguration
    newModel = modelToReuse
    this.getSceneManager(newModel)?.let {
      if (forceReinflate) it.sceneRenderConfiguration.needsInflation.set(true)
      if (invalidatePreviousRender) it.invalidateCachedResponse()
    }
  } else {
    val now = System.currentTimeMillis()
    debugLogger?.log("No models to reuse were found. New model $now.")
    val file =
      previewElementModelAdapter.createLightVirtualFile(fileContents, psiFile.virtualFile, now)
    newModel =
      NlModel.Builder(
          parentDisposable,
          AndroidBuildTargetReference.from(facet, psiFile.virtualFile),
          file,
          newConfiguration,
        )
        .withComponentRegistrar(NlComponentRegistrar)
        .withXmlProvider { project, virtualFile ->
          NlModel.getDefaultFile(project, virtualFile).also {
            it.putUserData(ModuleUtilCore.KEY_MODULE, facet.module)
          }
        }
        .build()
  }
  return newModel
}

private suspend fun renderAndTrack(
  sceneManager: LayoutlibSceneManager,
  refreshEventBuilder: PreviewRefreshEventBuilder?,
) {
  val inflate = sceneManager.sceneRenderConfiguration.needsInflation.get()
  val quality = sceneManager.sceneRenderConfiguration.quality
  val startMs = System.currentTimeMillis()
  sceneManager.requestRenderAndWait()
  val renderResult = sceneManager.renderResult
  refreshEventBuilder?.addPreviewRenderDetails(
    renderResult?.isErrorResult() == true,
    inflate,
    quality,
    System.currentTimeMillis() - startMs,
    renderResult?.logger?.messages?.singleOrNull()?.throwable?.javaClass?.simpleName,
  )
}
