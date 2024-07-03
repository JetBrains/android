/*
 * Copyright (C) 2023 The Android Open Source Project
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
package com.android.tools.idea.compose.gradle.renderer

import com.android.ide.common.rendering.api.ViewInfo
import com.android.tools.idea.compose.PsiComposePreviewElementInstance
import com.android.tools.idea.compose.preview.ComposeAdapterLightVirtualFile
import com.android.tools.idea.preview.rendering.createRenderTaskFuture
import com.android.tools.idea.projectsystem.getMainModule
import com.android.tools.preview.ComposePreviewElementInstance
import com.android.tools.preview.applyTo
import com.android.tools.preview.config.getDefaultPreviewDevice
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderTask
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

@TestOnly
data class RenderFutureForTests<T>(
  val future: CompletableFuture<T>,
  val lightVirtualFile: VirtualFile,
) {
  fun <R> map(mapper: (CompletableFuture<T>) -> CompletableFuture<R>): RenderFutureForTests<R> =
    RenderFutureForTests(lightVirtualFile = lightVirtualFile, future = mapper(future))

  fun get(): RenderResultForTests<T> =
    RenderResultForTests(lightVirtualFile = lightVirtualFile, result = future.get())
}

@TestOnly data class RenderResultForTests<T>(val result: T, val lightVirtualFile: VirtualFile)

/**
 * Returns a [CompletableFuture] that creates a [RenderTask] for a single
 * [ComposePreviewElementInstance]. It is the responsibility of a client of this function to dispose
 * the resulting [RenderTask] when no loner needed.
 *
 * Where [originFile] is a physical virtual file that is being previewed. It must be from a group of
 * modules identified by [facet].
 */
@TestOnly
fun createRenderTaskFuture(
  facet: AndroidFacet,
  originFile: VirtualFile,
  previewElement: PsiComposePreviewElementInstance,
  privateClassLoader: Boolean = false,
  useLayoutScanner: Boolean = false,
  classesToPreload: Collection<String> = emptyList(),
  customViewInfoParser: ((Any) -> List<ViewInfo>)? = null,
): RenderFutureForTests<RenderTask> {
  validateOriginFile(originFile, facet, previewElement)
  val lightVirtualFile =
    ComposeAdapterLightVirtualFile(
      "singlePreviewElement.xml",
      previewElement.toPreviewXml().buildString(),
      // Note: the same as previewElement.previewElementDefinition?.virtualFile, if available.
      originFile,
    )
  return RenderFutureForTests(
    lightVirtualFile = lightVirtualFile,
    future =
      createRenderTaskFuture(
        facet = facet,
        file = lightVirtualFile,
        privateClassLoader = privateClassLoader,
        useLayoutScanner = useLayoutScanner,
        classesToPreload = classesToPreload,
        customViewInfoParser = customViewInfoParser,
        showDecorations = previewElement.displaySettings.showDecoration,
        configure = { conf ->
          previewElement.applyTo(conf) { it.settings.getDefaultPreviewDevice() }
        },
      ),
  )
}

private fun validateOriginFile(
  originFile: VirtualFile,
  facet: AndroidFacet,
  previewElement: PsiComposePreviewElementInstance,
) {
  val definitionOriginFile = previewElement.previewElementDefinition?.virtualFile
  if (definitionOriginFile != null) {
    if (originFile != definitionOriginFile) {
      error(
        "originFile does not match the origin file or the preview definition: $originFile != $definitionOriginFile"
      )
    }
  }
  if (
    runReadAction {
      ProjectFileIndex.getInstance(facet.module.project)
        .getModuleForFile(originFile)
        ?.getMainModule()
    } != facet.mainModule
  ) {
    error("originFile($originFile) does not match facet($facet)")
  }
}

/**
 * Renders a single [ComposePreviewElement] and returns a [CompletableFuture] containing the result
 * or null if the preview could not be rendered. This method will render the element asynchronously
 * and will return immediately.
 *
 * Where [originFile] is a physical virtual file that is being previewed. It must be from a group of
 * modules identified by [facet].
 */
fun renderPreviewElementForResult(
  facet: AndroidFacet,
  originFile: VirtualFile,
  previewElement: PsiComposePreviewElementInstance,
  privateClassLoader: Boolean = false,
  useLayoutScanner: Boolean = false,
  customViewInfoParser: ((Any) -> List<ViewInfo>)? = null,
  executor: Executor = AppExecutorUtil.getAppExecutorService(),
): RenderFutureForTests<RenderResult?> {
  val renderTaskFuture =
    createRenderTaskFuture(
      facet = facet,
      originFile = originFile,
      previewElement = previewElement,
      privateClassLoader = privateClassLoader,
      useLayoutScanner = useLayoutScanner,
      classesToPreload = emptyList(),
      customViewInfoParser = customViewInfoParser,
    )

  return renderTaskFuture.map {
    val renderResultFuture =
      CompletableFuture.supplyAsync({ renderTaskFuture.future.get() }, executor)
        .thenCompose { it?.render() ?: CompletableFuture.completedFuture(null as RenderResult?) }
        .thenApply {
          if (
            it != null &&
              it.renderResult.isSuccess &&
              it.logger.brokenClasses.isEmpty() &&
              !it.logger.hasErrors()
          )
            it
          else null
        }

    renderResultFuture.handle { _, _ -> renderTaskFuture.future.get().dispose() }
    renderResultFuture
  }
}

/**
 * Renders a single [ComposePreviewElement] and returns a [CompletableFuture] containing the result
 * or null if the preview could not be rendered. This method will render the element asynchronously
 * and will return immediately.
 *
 * Where [originFile] is a physical virtual file that is being previewed. It must be from a group of
 * modules identified by [facet].
 */
fun renderPreviewElement(
  facet: AndroidFacet,
  originFile: VirtualFile,
  previewElement: PsiComposePreviewElementInstance,
): CompletableFuture<BufferedImage?> {
  return renderPreviewElementForResult(facet, originFile, previewElement).future.thenApply {
    it?.renderedImage?.copy
  }
}
