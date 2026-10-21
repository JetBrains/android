/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.rendering

import com.android.annotations.concurrency.Slow
import com.android.ide.common.rendering.api.RenderResources
import com.google.common.collect.Maps
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import javax.swing.Icon
import kotlin.properties.Delegates.observable
import org.jetbrains.android.facet.AndroidFacet
import org.jetbrains.annotations.TestOnly

private const val MAX_CONCURRENT_ICON_RENDERS = 2

private fun defaultRenderIcon(file: VirtualFile, renderResources: RenderResources?, facet: AndroidFacet) =
  GutterIconFactory.createIcon(file, renderResources, facet, JBUI.scale(16), JBUI.scale(16))

@Service(Service.Level.PROJECT)
class GutterIconCache
@TestOnly
constructor(
  private val project: Project,
  private val highDpiSupplier: () -> Boolean,
  private val renderIcon: (VirtualFile, RenderResources?, AndroidFacet) -> Icon?,
) : Disposable {
  private val thumbnailCache: MutableMap<String, TimestampedIcon> = Maps.newConcurrentMap()
  private val inFlightRenders: MutableMap<String, CompletableFuture<TimestampedIcon>> = Maps.newConcurrentMap()
  private val boundedExecutor: ExecutorService =
    AppExecutorUtil.createBoundedApplicationPoolExecutor("GutterIconCachePool", MAX_CONCURRENT_ICON_RENDERS)
  private var highDpiDisplay by
    observable(false) { _, oldValue, newValue ->
      if (oldValue != newValue) {
        thumbnailCache.clear()
        inFlightRenders.values.forEach { it.cancel(true) }
        inFlightRenders.clear()
      }
    }

  constructor(project: Project) : this(project, UIUtil::isRetina, ::defaultRenderIcon)

  /** Returns the potentially cached [Icon] rendered from the [file], or `null` if none could be rendered. */
  @Slow
  fun getIcon(file: VirtualFile, resolver: RenderResources?, facet: AndroidFacet): Icon? {
    getTimestampedIconFromCache(file)?.let {
      return it.icon
    }
    val future =
      inFlightRenders.computeIfAbsent(file.path) { _ ->
        val newFuture = CompletableFuture<TimestampedIcon>()
        boundedExecutor.execute {
          try {
            if (newFuture.isCancelled) return@execute
            val icon = renderIcon(file, resolver, facet)
            val result = TimestampedIcon(icon, file.modificationStamp)
            if (!newFuture.isCancelled) {
              thumbnailCache[file.path] = result
              newFuture.complete(result)
            }
          } catch (t: Throwable) {
            if (t is ProcessCanceledException || t.cause is ProcessCanceledException) {
              newFuture.completeExceptionally(t)
            } else {
              thisLogger().warn("Icon rendering failed for ${file.path}", t)
              val result = TimestampedIcon(null, file.modificationStamp)
              if (!newFuture.isCancelled) {
                thumbnailCache[file.path] = result
                newFuture.complete(result)
              }
            }
          } finally {
            inFlightRenders.remove(file.path, newFuture)
          }
        }
        newFuture
      }
    val timestampedIcon =
      if (ApplicationManager.getApplication().isDispatchThread && !ApplicationManager.getApplication().isUnitTestMode) {
        try {
          future.get(250, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
          null
        }
      } else {
        try {
          ProgressIndicatorUtils.awaitWithCheckCanceled(future)
        } catch (e: Exception) {
          if (e is ProcessCanceledException || e.cause is ProcessCanceledException) {
            throw e
          }
          null
        }
      }
    return timestampedIcon?.icon
  }

  /** Returns the [Icon] for the associated [file] if it is already rendered and stored in the cache, otherwise `null`. */
  fun getIconIfCached(file: VirtualFile): Icon? = getTimestampedIconFromCache(file)?.icon

  private fun getTimestampedIconFromCache(file: VirtualFile): TimestampedIcon? {
    highDpiDisplay = highDpiSupplier()
    return thumbnailCache[file.path]?.takeIf { it.isAsNewAs(file) }
  }

  override fun dispose() {
    inFlightRenders.values.forEach { it.cancel(true) }
    inFlightRenders.clear()
    boundedExecutor.shutdownNow()
  }

  data class TimestampedIcon(val icon: Icon?, val timestamp: Long) {
    fun isAsNewAs(file: VirtualFile) = timestamp == file.modificationStamp && !FileDocumentManager.getInstance().isFileModified(file)
  }

  companion object {
    @JvmStatic fun getInstance(project: Project): GutterIconCache = project.service()
  }
}
