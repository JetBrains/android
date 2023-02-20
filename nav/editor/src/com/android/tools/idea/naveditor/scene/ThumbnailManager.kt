/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.naveditor.scene

import com.android.annotations.concurrency.GuardedBy
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.rendering.StudioRenderService
import com.android.tools.idea.rendering.taskBuilder
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.ResourceRepositoryManager
import com.google.common.annotations.VisibleForTesting
import com.google.common.collect.HashBasedTable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.reference.SoftReference
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.ImageUtil
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetScopedService
import java.awt.Dimension
import java.awt.Image
import java.awt.image.BufferedImage
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

private val KEY = Key.create<ThumbnailManager>(ThumbnailManager::class.java.name)

data class RefinableImage(val image: Image? = null, val refined: CompletableFuture<RefinableImage?>? = null) {
  val lastCompleted
    get() = generateSequence(this) { if (it.refined?.isDone == true) it.refined.get() else null }.last()

  val terminalImage
    get() = generateSequence(this) { it.refined?.get() }.last().image
}

/**
 * Creates and caches preview images of screens in the nav editor.
 */
open class ThumbnailManager protected constructor(facet: AndroidFacet) : AndroidFacetScopedService(facet) {

  private val myImages = HashBasedTable.create<VirtualFile, Configuration, SoftReference<BufferedImage>?>()
  private val myScaledImages = HashBasedTable.create<VirtualFile, Configuration, HashBasedTable<Dimension, ScaleContext, SoftReference<Image>?>?>()
  private val myRenderVersions = HashBasedTable.create<VirtualFile, Configuration, Long>()
  private val myRenderModStamps = HashBasedTable.create<VirtualFile, Configuration, Long>()
  private var myResourceRepository: LocalResourceRepository? = ResourceRepositoryManager.getAppResources(facet)

  @GuardedBy("disposalLock")
  private val myPendingFutures = HashMap<VirtualFile, CompletableFuture<RefinableImage?>>()

  @GuardedBy("disposalLock")
  private var myDisposed: Boolean = false

  private val disposalLock = Any()

  private fun modificationCount() = myResourceRepository?.modificationCount ?: 0

  override fun onDispose() {
    lateinit var futures: Array<CompletableFuture<RefinableImage?>>
    synchronized(disposalLock) {
      myDisposed = true
      futures = myPendingFutures.values.toTypedArray()
      myPendingFutures.clear()
    }
    myResourceRepository = null
    try {
      CompletableFuture.allOf(*futures).get(5, TimeUnit.SECONDS)
    }
    catch (e: Exception) {
      // We do not care about these exceptions since we are disposing anyway
    }

    super.onDispose()
  }

  fun getThumbnail(
    xmlFile: XmlFile,
    configuration: Configuration,
    dimensions: Dimension,
    scaleContext: ScaleContext
  ): RefinableImage {
    val file = xmlFile.virtualFile
    val cachedByDimension = myScaledImages[file, configuration]
                            ?: HashBasedTable.create<Dimension, ScaleContext, SoftReference<Image>?>().also {
                              myScaledImages.put(file, configuration, it)
                            }
    val cached = cachedByDimension[dimensions, scaleContext]?.get()
    return if (cached != null &&
               myRenderVersions.get(file, configuration) == modificationCount() &&
               myRenderModStamps.get(file, configuration) == file.timeStamp) {
      RefinableImage(cached)
    }
    else {
      RefinableImage(cached, getScaledImage(xmlFile, configuration, dimensions, scaleContext))
    }
  }

  private fun getScaledImage(
    xmlFile: XmlFile,
    configuration: Configuration,
    dimensions: Dimension,
    scaleContext: ScaleContext
  ): CompletableFuture<RefinableImage?> {
    val file = xmlFile.virtualFile
    val result = CompletableFuture<RefinableImage?>()
    // First check to see if we're already disposed/being disposed. If not, register the result as a pending result for the given file.
    synchronized(disposalLock) {
      if (myDisposed) {
        return CompletableFuture.completedFuture(null)
      }
      val inProgress = myPendingFutures[file]
      if (inProgress != null) {
        return inProgress
      }
      myPendingFutures[file] = result
    }

    // This async pipeline will eventually set "result". First get the full-sized image.
    getFullImage(configuration, xmlFile)
      // Scale the image to the desired size
      .thenApply { full ->
        if (full == null) {
          RefinableImage()
        }
        else {
          synchronized(disposalLock) {
            // We might have been disposed while waiting to run
            if (myDisposed) {
              return@thenApply null
            }
          }
          // This does the high-quality scaling asynchronously
          val scaledFuture = scaleImage(full, dimensions, scaleContext).thenApply { scaled ->
            val dimensionMap: HashBasedTable<Dimension, ScaleContext, SoftReference<Image>?> =
              myScaledImages[xmlFile.virtualFile, configuration]
              ?: HashBasedTable.create<Dimension, ScaleContext, SoftReference<Image>?>().also {
                myScaledImages.put(xmlFile.virtualFile, configuration, it)
              }
            dimensionMap.put(dimensions, scaleContext, SoftReference(scaled))
            scaled
          }.thenApply { RefinableImage(it) }
          // This stage of the top-level async pipeline returns a quickly-scaled version of the fullsize image, and the future for the high-
          // quality scaled version.
          return@thenApply RefinableImage(previewScaleImage(full, dimensions), scaledFuture)
        }
      }
      // Now we have to handle the result. Either complete the originally-returned "result" future normally or exceptionally.
      .handle { image, exception ->
        if (exception != null) {
          result.completeExceptionally(exception)
        }
        else {
          result.complete(image)
        }
        synchronized(disposalLock) {
          myPendingFutures.remove(file)
        }
      }

    return result
  }

  private fun getFullImage(
    configuration: Configuration,
    xmlFile: XmlFile
  ): CompletableFuture<BufferedImage?> {
    val file = xmlFile.virtualFile
    val fullSize = myImages[file, configuration]?.get()
    return if (fullSize != null &&
               myRenderVersions.get(file, configuration) == modificationCount() &&
               myRenderModStamps.get(file, configuration) == file.timeStamp) {
      CompletableFuture.completedFuture(fullSize)
    }
    else {
      getImage(xmlFile, file, configuration)
    }
  }

  private fun previewScaleImage(image: BufferedImage, dimensions: Dimension): BufferedImage {
    val scaled = ImageUtil.createImage(dimensions.width, dimensions.height, BufferedImage.TYPE_INT_ARGB)
    scaled.graphics.drawImage(image, 0, 0, dimensions.width, dimensions.height, null)
    return scaled
  }

  private fun scaleImage(image: BufferedImage, dimensions: Dimension, scaleContext: ScaleContext): CompletableFuture<Image> {
    val result = CompletableFuture<Image>()
    ApplicationManager.getApplication().executeOnPooledThread {
      var scaledImage = ImageUtil.ensureHiDPI(image, scaleContext)
      scaledImage = ImageUtil.scaleImage(scaledImage, dimensions.width, dimensions.height)

      result.complete(scaledImage)
    }
    return result
  }

  // open for testing
  @VisibleForTesting
  protected open fun getImage(xmlFile: XmlFile, file: VirtualFile, configuration: Configuration): CompletableFuture<BufferedImage?> {
    val renderService = StudioRenderService.getInstance(module.project)
    val renderTaskFuture = createTask(facet, xmlFile, configuration, renderService)
    return renderTaskFuture.thenCompose { task -> task.render() }
      .thenApply {
        val image = it.renderedImage.copy
        myImages.put(file, configuration, SoftReference<BufferedImage>(image))
        myRenderVersions.put(file, configuration, modificationCount())
        myRenderModStamps.put(file, configuration, file.timeStamp)
        image
      }
      .whenCompleteAsync({ _, _ -> renderTaskFuture.get()?.dispose() }, AppExecutorUtil.getAppExecutorService())
  }

  protected open fun createTask(facet: AndroidFacet,
                                file: XmlFile,
                                configuration: Configuration,
                                renderService: RenderService): CompletableFuture<RenderTask> {
    return renderService.taskBuilder(facet, configuration)
      .withPsiFile(file)
      .build()
      .whenComplete { task, _ -> task.setDecorations(false) }
  }

  override fun onServiceDisposal(facet: AndroidFacet) {}

  companion object {
    @JvmStatic
    fun getInstance(facet: AndroidFacet): ThumbnailManager {
      var manager = facet.getUserData(KEY)
      if (manager == null) {
        manager = ThumbnailManager(facet)
        setInstance(facet, manager)
      }
      return manager
    }

    @VisibleForTesting
    @JvmStatic
    fun setInstance(facet: AndroidFacet, manager: ThumbnailManager?) {
      facet.putUserData(KEY, manager)
    }
  }
}
