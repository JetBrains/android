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
package com.android.tools.idea.resourceExplorer.plugin

import com.android.annotations.VisibleForTesting
import com.android.annotations.VisibleForTesting.Visibility
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.layoutlib.RenderingException
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.ResourceRepositoryManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.reference.SoftReference
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetScopedService
import org.jetbrains.ide.PooledThreadExecutor
import java.awt.image.BufferedImage
import java.util.HashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.function.Function
import javax.annotation.concurrent.GuardedBy

private val KEY = Key.create<LayoutRenderer>(LayoutRenderer::class.java.name)

@VisibleForTesting(visibility = Visibility.PRIVATE)
const val MAX_RENDER_WIDTH = 768

@VisibleForTesting(visibility = Visibility.PRIVATE)
const val MAX_RENDER_HEIGHT = 1024

@VisibleForTesting(visibility = Visibility.PRIVATE)
const val DOWNSCALE_FACTOR = 0.25f


/**
 * Source data to generate a rendering. This is used to cache the generated
 * images using [CachedRenderVersion].
 */
private data class CachedRenderSource(
  val file: VirtualFile,
  val configuration: Configuration
)

/**
 * Representation of a rendering version which is defined by the combination of
 * an image, a [version] from [LocalResourceRepository.getModificationCount] and
 * a [VirtualFile] modification timestamp ([modStamp]).
 */
private data class CachedRenderVersion(
  var version: Long,
  var modStamp: Long,
  var image: SoftReference<BufferedImage>? = null
)

typealias RenderTaskProvider = (AndroidFacet, XmlFile, Configuration) -> RenderTask?

private fun createRenderTask(facet: AndroidFacet,
                             xmlFile: XmlFile,
                             configuration: Configuration): RenderTask? {
  return RenderService.getInstance(facet.module.project)
    .taskBuilder(facet, configuration)
    .withPsiFile(xmlFile)
    .withDownscaleFactor(DOWNSCALE_FACTOR)
    .withMaxRenderSize(MAX_RENDER_WIDTH, MAX_RENDER_HEIGHT)
    .disableDecorations()
    .buildSynchronously()
}

/**
 * Creates and caches small preview images of layout files.
 * @param renderTaskProvider function that return a [RenderTask]
 */
class LayoutRenderer
@VisibleForTesting(visibility = Visibility.PRIVATE)
constructor(
  facet: AndroidFacet,
  private val renderTaskProvider: RenderTaskProvider = ::createRenderTask
) : AndroidFacetScopedService(facet) {

  private val renderCache = HashMap<CachedRenderSource, CachedRenderVersion>()
  private val localResourceRepository: LocalResourceRepository = ResourceRepositoryManager.getAppResources(facet)

  @GuardedBy("disposalLock")
  private val myPendingFutures = HashMap<VirtualFile, CompletableFuture<BufferedImage?>>()

  @GuardedBy("disposalLock")
  private var myDisposed: Boolean = false

  private val disposalLock = Any()

  override fun onDispose() {
    lateinit var futures: Array<CompletableFuture<BufferedImage?>>
    synchronized(disposalLock) {
      myDisposed = true
      futures = myPendingFutures.values.toTypedArray()
      myPendingFutures.clear()
    }
    try {
      CompletableFuture.allOf(*futures).get(5, TimeUnit.SECONDS)
    }
    catch (e: Exception) {
      // We do not care about these exceptions since we are disposing anyway
    }

    super.onDispose()
  }

  fun getLayoutRender(xmlFile: XmlFile, configuration: Configuration): CompletableFuture<BufferedImage?> {
    val file = xmlFile.virtualFile
    val fullImageFuture = getFullImage(configuration, xmlFile)

    synchronized(disposalLock) {
      if (myDisposed) {
        return CompletableFuture.completedFuture(null)
      }
      val inProgress = myPendingFutures[file]
      if (inProgress != null) {
        return inProgress
      }
      myPendingFutures.put(file, fullImageFuture)
    }

    fullImageFuture.whenComplete { _, _ ->
      synchronized(disposalLock) {
        myPendingFutures.remove(file)
      }
    }
    return fullImageFuture
  }

  private fun getFullImage(configuration: Configuration, xmlFile: XmlFile): CompletableFuture<BufferedImage?> {
    val file = xmlFile.virtualFile
    val cachedRenderSource = CachedRenderSource(file, configuration)
    val fullSize = renderCache[cachedRenderSource]?.image?.get()
    return if (fullSize != null && fileIsUpToDate(file, cachedRenderSource)) {
      CompletableFuture.completedFuture(fullSize)
    }
    else {
      renderFile(xmlFile, file, configuration)
    }
  }

  private fun fileIsUpToDate(file: VirtualFile,
                             cachedRenderSource: CachedRenderSource): Boolean =
    renderCache[cachedRenderSource]?.let {
      it.modStamp == file.timeStamp &&
      it.version == localResourceRepository.modificationCount
    } ?: false


  private fun renderFile(xmlFile: XmlFile,
                         file: VirtualFile,
                         configuration: Configuration): CompletableFuture<BufferedImage?> {

    val task = renderTaskProvider(facet, xmlFile, configuration) ?: return CompletableFuture.completedFuture(null)
    return task.render()
      .thenApplyAsync(
        Function { it: RenderResult ->
          when {
            it.renderResult.isSuccess -> it.renderedImage.copy
            it.renderResult.exception != null -> throw it.renderResult.exception
            else -> throw RenderingException(it.renderResult.status.name)
          }
        },
        PooledThreadExecutor.INSTANCE)
      .thenApply { image ->
        if (image != null) {
          val cachedRenderSource = CachedRenderSource(file, configuration)
          val cachedRenderVersion = CachedRenderVersion(
            version = localResourceRepository.modificationCount,
            modStamp = file.timeStamp,
            image = SoftReference(image))
          renderCache[cachedRenderSource] = cachedRenderVersion
        }
        image
      }
  }

  override fun onServiceDisposal(facet: AndroidFacet) {}

  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet): LayoutRenderer {
      var manager = facet.getUserData(KEY)
      if (manager == null) {
        manager = LayoutRenderer(facet)
        setInstance(facet, manager)
      }
      return manager
    }

    @VisibleForTesting
    @JvmStatic
    fun setInstance(facet: AndroidFacet, layoutRenderer: LayoutRenderer?) {
      facet.putUserData(KEY, layoutRenderer)
    }
  }
}
