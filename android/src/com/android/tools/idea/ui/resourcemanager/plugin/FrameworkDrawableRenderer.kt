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
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTask
import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetScopedService
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.Dimension
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.function.Supplier

private val FRAMEWORK_DRAWABLE_KEY = Key.create<FrameworkDrawableRenderer>(FrameworkDrawableRenderer::class.java.name)

private fun createRenderTask(facet: AndroidFacet,
                             configuration: Configuration): CompletableFuture<RenderTask?> {
  return RenderService.getInstance(facet.module.project)
    .taskBuilder(facet, configuration)
    .build()
}

/**
 * Creates preview images of Framework Drawables.
 */
class FrameworkDrawableRenderer
@VisibleForTesting
constructor(
  facet: AndroidFacet,
  configuration: Configuration,
  private val futuresManager: ImageFuturesManager<ResourceValue>
) : AndroidFacetScopedService(facet) {

  private val renderTaskLock = Any()
  private val renderTaskFuture = createRenderTask(facet, configuration)

  init {
    Disposer.register(this, futuresManager)
  }

  override fun onServiceDisposal(facet: AndroidFacet) {
    renderTaskFuture.whenComplete { renderTask, _ ->
      synchronized(renderTaskLock) {
        // Wait for dispose to finish since this is a module level disposal (closed project, removed module, etc).
        renderTask?.dispose()?.get(5L, TimeUnit.MINUTES)
      }
    }
  }

  fun getDrawableRender(resourceValue: ResourceValue,
                        targetSize: Dimension): CompletableFuture<BufferedImage?> {
    val renderImageCallback: () -> CompletableFuture<BufferedImage?> = { getImage(resourceValue, targetSize) }
    return futuresManager.registerAndGet(resourceValue, renderImageCallback)
  }

  private fun getImage(value: ResourceValue, dimension: Dimension): CompletableFuture<BufferedImage?> {
    return renderTaskFuture.thenCompose {
      synchronized(renderTaskLock) {
        it?.setOverrideRenderSize(dimension.width, dimension.height)
        it?.setMaxRenderSize(dimension.width, dimension.height)
        it?.renderDrawable(value)
      }
    }
  }

  companion object {
    private val futures = ConcurrentHashMap<AndroidFacet, CompletableFuture<FrameworkDrawableRenderer>>()

    @JvmStatic
    fun getInstance(facet: AndroidFacet): CompletableFuture<FrameworkDrawableRenderer> {
      facet.getUserData(FRAMEWORK_DRAWABLE_KEY)?.let { return CompletableFuture.completedFuture(it) }
      futures[facet]?.let { return it }

      return createFrameworkDrawableRenderer(facet).also {
        futures[facet] = it
        it.whenComplete { _, _ -> futures[facet] }
      }
    }

    @VisibleForTesting
    @JvmStatic
    fun setInstance(facet: AndroidFacet, drawableRenderer: FrameworkDrawableRenderer?) {
      // TODO: Move method to test Module and use AndroidFacet.putUserData directly, make the Key @VisibleForTesting instead
      facet.putUserData(FRAMEWORK_DRAWABLE_KEY, drawableRenderer)
    }

    private fun createFrameworkDrawableRenderer(facet: AndroidFacet): CompletableFuture<FrameworkDrawableRenderer> =
      CompletableFuture.supplyAsync(Supplier {
        val projectFile = facet.module.project.projectFile ?: throw Exception("ProjectFile should not be null to obtain Configuration")
        ConfigurationManager.getOrCreateInstance(facet).getConfiguration(projectFile)
      }, PooledThreadExecutor.INSTANCE)
        .thenApply { configuration ->
          FrameworkDrawableRenderer(
            facet,
            configuration,
            ImageFuturesManager<ResourceValue>()
          ).also { setInstance(facet, it) }
        }
  }
}