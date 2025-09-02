/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.ide.common.rendering.api.SessionParams
import com.android.tools.configurations.Configuration
import com.android.tools.idea.layoutlib.RenderingException
import com.android.tools.idea.rendering.AndroidBuildTargetReference
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.parsers.PsiXmlFile
import com.android.tools.idea.rendering.taskBuilder
import com.android.tools.rendering.RenderResult
import com.android.tools.rendering.RenderService
import com.android.tools.rendering.RenderTask
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetScopedService
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.function.Function

@VisibleForTesting
const val MAX_RENDER_WIDTH = 768

@VisibleForTesting
const val MAX_RENDER_HEIGHT = 1024

private val LAYOUT_KEY = Key.create<LayoutRenderer>(LayoutRenderer::class.java.name)

private fun createRenderTask(buildTarget: AndroidBuildTargetReference,
                             xmlFile: XmlFile,
                             configuration: Configuration,
                             layoutRenderOptions: LayoutRenderOptions
): CompletableFuture<RenderTask?> {
  return layoutRenderOptions.applyTo(StudioRenderService.getInstance(buildTarget.project).taskBuilder(buildTarget, configuration))
    .withPsiFile(PsiXmlFile(xmlFile))
    .withMaxRenderSize(MAX_RENDER_WIDTH, MAX_RENDER_HEIGHT)
    .build()
}

/**
 * Creates and caches small preview images of layout files.
 * @param renderTaskProvider function that return a [RenderTask]
 */
class LayoutRenderer
@VisibleForTesting
constructor(
  facet: AndroidFacet,
  private val renderTaskProvider: (AndroidBuildTargetReference, XmlFile, Configuration, LayoutRenderOptions) -> CompletableFuture<RenderTask?>,
  private val futuresManager: ImageFuturesManager<VirtualFile>
) : AndroidFacetScopedService(facet) {

  init {
    Disposer.register(this, futuresManager)
  }

  override fun onServiceDisposal(facet: AndroidFacet) {}

  fun getLayoutRender(xmlFile: XmlFile, configuration: Configuration, layoutRenderOptions: LayoutRenderOptions? = null): CompletableFuture<BufferedImage?> {
    val imageRenderCallback: () -> CompletableFuture<BufferedImage?> = { getImage(xmlFile, configuration, layoutRenderOptions) }
    return futuresManager.registerAndGet(xmlFile.virtualFile, imageRenderCallback)
  }

  private fun getImage(xmlFile: XmlFile, configuration: Configuration, layoutRenderOptions: LayoutRenderOptions?): CompletableFuture<BufferedImage?> {
    val options = layoutRenderOptions ?: LayoutRenderOptions()
    val renderTaskFuture = renderTaskProvider(AndroidBuildTargetReference.gradleOnly(facet), xmlFile, configuration, options)
    return renderTaskFuture.thenCompose { it?.render() }
      .thenApplyAsync(Function<RenderResult?, BufferedImage?> {
        if (it == null) {
          return@Function null
        }
        when {
          it.renderResult.isSuccess -> it.renderedImage.copy
          it.renderResult.exception != null -> throw it.renderResult.exception
          else -> throw RenderingException(it.renderResult.status.name)
        }
      }, PooledThreadExecutor.INSTANCE).whenComplete { _, _ ->
        // Dispose the RenderTask once it has finished rendering.
        renderTaskFuture.get()?.dispose()
      }
  }

  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet): LayoutRenderer {
      var manager = facet.getUserData(LAYOUT_KEY)
      if (manager == null) {
        manager = LayoutRenderer(
          facet,
          ::createRenderTask,
          ImageFuturesManager<VirtualFile>()
        )
        setInstance(facet, manager)
      }
      return manager
    }

    @VisibleForTesting
    @JvmStatic
    fun setInstance(facet: AndroidFacet, layoutRenderer: LayoutRenderer?) {
      // TODO: Move method to test Module and use AndroidFacet.putUserData directly, make the Key @VisibleForTesting instead
      facet.putUserData(LAYOUT_KEY, layoutRenderer)
    }
  }
}

/**
 * Used to apply additional RenderTask configuration to the [LayoutRenderer].
 *
 * @property renderingMode The rendering mode to be used in the [RenderTask], defaults to [SessionParams.RenderingMode.NORMAL]
 * @property disableDecorations Disables decorations in the render, defaults to true for the [LayoutRenderer]
 * @property transparentBackground Enables window transparency in the future render
 */
data class LayoutRenderOptions(
  val renderingMode: SessionParams.RenderingMode? = null,
  val disableDecorations: Boolean = true,
  val transparentBackground: Boolean = false,
) {
  fun applyTo(builder: RenderService.RenderTaskBuilder): RenderService.RenderTaskBuilder {
    return builder.apply {
      renderingMode?.let { withRenderingMode(it) }
      if (transparentBackground) {
        useTransparentBackground()
      }
      if (disableDecorations) {
        disableDecorations()
      }
    }
  }
}