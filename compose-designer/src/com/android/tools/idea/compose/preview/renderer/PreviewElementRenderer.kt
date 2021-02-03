/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.compose.preview.renderer

import com.android.ide.common.rendering.api.SessionParams
import com.android.ide.common.resources.configuration.FolderConfiguration
import com.android.tools.idea.AndroidPsiUtils
import com.android.tools.idea.compose.preview.util.ComposeAdapterLightVirtualFile
import com.android.tools.idea.compose.preview.util.PreviewElement
import com.android.tools.idea.compose.preview.util.PreviewElementInstance
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTask
import com.google.common.annotations.VisibleForTesting
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.android.facet.AndroidFacet
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.function.Supplier

/**
 * Returns a [CompletableFuture] that creates a [RenderTask] for a single [PreviewElementInstance]. It is the
 * responsibility of a client of this function to dispose the resulting [RenderTask] when no loner needed.
 */
@VisibleForTesting
fun createRenderTaskFuture(facet: AndroidFacet,
                           previewElement: PreviewElementInstance,
                           privateClassLoader: Boolean = false): CompletableFuture<RenderTask> {
  val project = facet.module.project

  val file = ComposeAdapterLightVirtualFile("singlePreviewElement.xml", previewElement.toPreviewXml().buildString()) { previewElement.previewElementDefinitionPsi?.virtualFile }
  val psiFile = AndroidPsiUtils.getPsiFileSafely(project, file) ?: return CompletableFuture.completedFuture(null)
  val configuration = Configuration.create(ConfigurationManager.getOrCreateInstance(facet), null, FolderConfiguration.createDefault())

  return RenderService.getInstance(project)
    .taskBuilder(facet, configuration)
    .withPsiFile(psiFile)
    .disableDecorations().apply {
      if (privateClassLoader) {
        usePrivateClassLoader()
      }
    }
    .withRenderingMode(SessionParams.RenderingMode.SHRINK)
    .build()
}

/**
 * Renders a single [PreviewElement] and returns a [CompletableFuture] containing the result or null if the preview could not be rendered.
 * This method will render the element asynchronously and will return immediately.
 */
@VisibleForTesting
fun renderPreviewElementForResult(facet: AndroidFacet,
                                  previewElement: PreviewElementInstance,
                                  privateClassLoader: Boolean = false,
                                  executor: Executor = AppExecutorUtil.getAppExecutorService()): CompletableFuture<RenderResult?> {
  val renderTaskFuture = createRenderTaskFuture(facet, previewElement, privateClassLoader)

  val renderResultFuture = CompletableFuture.supplyAsync({ renderTaskFuture.get() }, executor)
    .thenCompose { it?.render() ?: CompletableFuture.completedFuture(null as RenderResult?) }
    .thenApply { if (it != null && it.renderResult.isSuccess && it.logger.brokenClasses.isEmpty() && !it.logger.hasErrors()) it else null }

  renderResultFuture.handle { _, _ -> renderTaskFuture.get().dispose() }

  return renderResultFuture
}

/**
 * Renders a single [PreviewElement] and returns a [CompletableFuture] containing the result or null if the preview could not be rendered.
 * This method will render the element asynchronously and will return immediately.
 */
fun renderPreviewElement(facet: AndroidFacet,
                         previewElement: PreviewElementInstance): CompletableFuture<BufferedImage?> {
  return renderPreviewElementForResult(facet, previewElement).thenApply { it?.renderedImage?.copy }
}