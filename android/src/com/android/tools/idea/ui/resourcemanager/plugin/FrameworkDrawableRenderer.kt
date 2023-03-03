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
package com.android.tools.idea.ui.resourcemanager.plugin

import com.android.ide.common.rendering.api.ResourceValue
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.configurations.ConfigurationManager
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.taskBuilder
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetScopedService
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.function.Function
import java.util.function.Supplier

private val FRAMEWORK_DRAWABLE_KEY = Key.create<FrameworkDrawableRenderer>(FrameworkDrawableRenderer::class.java.name)

private fun createRenderTask(facet: AndroidFacet,
                             configuration: Configuration): CompletableFuture<RenderTask?> {
  return StudioRenderService.getInstance(facet.module.project)
    .taskBuilder(facet, configuration)
    .build()
}

/**
 * Creates preview images of Framework Drawables, these are Drawables located in the framework resources jar of LayoutLib.
 */
class FrameworkDrawableRenderer
@VisibleForTesting
constructor(
  facet: AndroidFacet,
  private val renderTaskProvider: (AndroidFacet, Configuration) -> CompletableFuture<RenderTask?>,
  private val futuresManager: ImageFuturesManager<ResourceValue>
) : AndroidFacetScopedService(facet) {

  init {
    Disposer.register(this, futuresManager)
  }

  override fun onServiceDisposal(facet: AndroidFacet) {}

  fun getDrawableRender(resourceValue: ResourceValue, fileForConfiguration: VirtualFile, targetSize: Dimension): CompletableFuture<BufferedImage?> {
    val renderImageCallback: () -> CompletableFuture<BufferedImage?> = { getImage(resourceValue, fileForConfiguration, targetSize) }
    return futuresManager.registerAndGet(resourceValue, renderImageCallback)
  }

  private fun getImage(value: ResourceValue, fileForConfiguration: VirtualFile, dimension: Dimension): CompletableFuture<BufferedImage?> {
    return getConfigurationFuture(facet, fileForConfiguration).thenComposeAsync(Function { configuration ->
      renderTaskProvider(facet, configuration).thenCompose { renderTask ->
        renderTask?.setOverrideRenderSize(dimension.width, dimension.height)
        renderTask?.setMaxRenderSize(dimension.width, dimension.height)
        renderTask?.renderDrawable(value)?.whenComplete { _, _ ->
          renderTask.dispose()
        }
      }
    }, PooledThreadExecutor.INSTANCE)
  }

  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet): FrameworkDrawableRenderer {
      var renderer = facet.getUserData(FRAMEWORK_DRAWABLE_KEY)
      if (renderer == null) {
        renderer = FrameworkDrawableRenderer(
          facet,
          ::createRenderTask,
          ImageFuturesManager<ResourceValue>()
        )
        facet.putUserData(FRAMEWORK_DRAWABLE_KEY, renderer)
      }
      return renderer
    }

    @VisibleForTesting
    @JvmStatic
    fun setInstance(facet: AndroidFacet, drawableRenderer: FrameworkDrawableRenderer?) {
      // TODO: Move method to test Module and use AndroidFacet.putUserData directly, make the Key @VisibleForTesting instead
      facet.putUserData(FRAMEWORK_DRAWABLE_KEY, drawableRenderer)
    }
  }
}

private fun getConfigurationFuture(facet: AndroidFacet, file: VirtualFile): CompletableFuture<Configuration> {
  return CompletableFuture.supplyAsync(Supplier {
    ConfigurationManager.getOrCreateInstance(facet.module).getConfiguration(file)
  }, PooledThreadExecutor.INSTANCE)
}