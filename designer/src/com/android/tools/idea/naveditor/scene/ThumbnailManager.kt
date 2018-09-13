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

import com.android.annotations.VisibleForTesting
import com.android.tools.idea.configurations.Configuration
import com.android.tools.idea.rendering.RenderResult
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.RenderTask
import com.android.tools.idea.res.LocalResourceRepository
import com.android.tools.idea.res.ResourceRepositoryManager
import com.google.common.collect.HashBasedTable
import com.google.common.util.concurrent.ListenableFuture
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.xml.XmlFile
import com.intellij.reference.SoftReference
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.android.facet.AndroidFacetScopedService
import java.awt.image.BufferedImage
import java.util.HashMap
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import javax.annotation.concurrent.GuardedBy

private val KEY = Key.create<ThumbnailManager>(ThumbnailManager::class.java.name)

/**
 * Creates and caches preview images of screens in the nav editor.
 */
open class ThumbnailManager protected constructor(facet: AndroidFacet) : AndroidFacetScopedService(facet) {

  private val myImages = HashBasedTable.create<VirtualFile, Configuration, SoftReference<BufferedImage>>()
  private val myOldImages = HashBasedTable.create<VirtualFile, Configuration, SoftReference<BufferedImage>>()
  private val myRenderVersions = HashBasedTable.create<VirtualFile, Configuration, Long>()
  private val myRenderModStamps = HashBasedTable.create<VirtualFile, Configuration, Long>()
  private val myResourceRepository: LocalResourceRepository = ResourceRepositoryManager.getAppResources(facet)

  @GuardedBy("DISPOSAL_LOCK")
  private val myPendingFutures = HashMap<VirtualFile, CompletableFuture<BufferedImage?>>()

  @GuardedBy("DISPOSAL_LOCK")
  private var myDisposed: Boolean = false

  private val DISPOSAL_LOCK = Any()

  override fun onDispose() {
    lateinit var futures: Array<CompletableFuture<BufferedImage?>>
    synchronized(DISPOSAL_LOCK) {
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

  fun getOldThumbnail(file: VirtualFile, configuration: Configuration) = myOldImages.get(file, configuration)?.get()

  fun getThumbnail(xmlFile: XmlFile, configuration: Configuration): CompletableFuture<BufferedImage?> {
    val file = xmlFile.virtualFile
    val cachedReference = myImages.get(file, configuration)
    cachedReference?.get()?.let { cached ->
      if (myRenderVersions.get(file, configuration) == myResourceRepository.modificationCount &&
          myRenderModStamps.get(file, configuration) == file.timeStamp) {
        return CompletableFuture.completedFuture(cached)
      }
      else {
        myOldImages.put(file, configuration, cachedReference)
      }
    }

    val result = CompletableFuture<BufferedImage?>()
    synchronized(DISPOSAL_LOCK) {
      if (myDisposed) {
        result.complete(null)
        return result
      }
      val inProgress = myPendingFutures[file]
      if (inProgress != null) {
        return inProgress
      }
      myPendingFutures.put(file, result)
    }

    // TODO we run in a separate thread because task.render() currently isn't asynchronous
    // if inflate() (which is itself synchronous) hasn't already been called.
    ApplicationManager.getApplication().executeOnPooledThread {
      try {
        synchronized(DISPOSAL_LOCK) {
          // We might have been disposed while waiting to run
          if (myDisposed) {
            result.complete(null)
            return@executeOnPooledThread
          }
        }
        try {
          result.complete(getImage(xmlFile, file, configuration))
          myOldImages.remove(file, configuration)
        }
        catch (e: Exception) {
          result.completeExceptionally(e)
        }
        finally {
          synchronized(DISPOSAL_LOCK) {
            myPendingFutures.remove(file)
          }
        }
      }
      catch (t: Throwable) {
        result.completeExceptionally(t)
        synchronized(DISPOSAL_LOCK) {
          myPendingFutures.remove(file)
        }
      }
    }
    return result
  }

  private fun getImage(xmlFile: XmlFile, file: VirtualFile, configuration: Configuration): BufferedImage? {
    val renderService = RenderService.getInstance(module.project)
    val task = createTask(facet, xmlFile, configuration, renderService)
    var renderResult: ListenableFuture<RenderResult>? = null
    if (task != null) {
      renderResult = task.render()
    }
    var image: BufferedImage? = null
    if (renderResult != null) {
      // This should also be done in a listener if task.render() were actually async.
      image = renderResult.get().renderedImage.copy
      myImages.put(file, configuration, SoftReference<BufferedImage>(image))
      myRenderVersions.put(file, configuration, myResourceRepository.modificationCount)
      myRenderModStamps.put(file, configuration, file.timeStamp)
    }
    return image
  }

  protected open fun createTask(facet: AndroidFacet,
                                file: XmlFile,
                                configuration: Configuration,
                                renderService: RenderService): RenderTask? {
    val task = renderService.taskBuilder(facet, configuration)
      .withPsiFile(file)
      .build()
    task?.setDecorations(false)
    return task
  }

  override fun onServiceDisposal(facet: AndroidFacet) {

  }

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
