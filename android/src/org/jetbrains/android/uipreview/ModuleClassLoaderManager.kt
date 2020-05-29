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
package org.jetbrains.android.uipreview

import com.android.SdkConstants.CLASS_COMPOSE_VIEW_ADAPTER
import com.android.tools.idea.LogAnonymizerUtil.anonymize
import com.android.tools.idea.rendering.RenderService
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import java.util.WeakHashMap

private const val CLASS_COMPOSE_RECOMPOSER = "androidx.compose.Recomposer"
private const val CLASS_COMPOSE_FRAMES = "androidx.compose.frames.FramesKt"
/**
 * A [ClassLoader] for the [Module] dependencies.
 */
class ModuleClassLoaderManager {
  private val cache: MutableMap<Module, ModuleClassLoader> = WeakHashMap();

  /**
   * Returns a project class loader to use for rendering. May cache instances across render sessions.
   */
  @Synchronized
  fun getShared(parent: ClassLoader?, module: Module): ModuleClassLoader {
    var moduleClassLoader =
      cache[module]

    var oldClassLoader: ModuleClassLoader? = null
    if (moduleClassLoader != null) {
      if (moduleClassLoader.parent != parent) {
        LOG.debug("Parent has changed, discarding ModuleClassLoader")
        oldClassLoader = moduleClassLoader
        moduleClassLoader = null
      }
      else if (!moduleClassLoader.isUpToDate) {
        LOG.debug("Files have changed, discarding ModuleClassLoader")
        oldClassLoader = moduleClassLoader
        moduleClassLoader = null
      }
      else {
        LOG.debug("ModuleClassLoader is up to date")
      }
    }

    if (moduleClassLoader == null) {
      LOG.debug { "Loading new class loader for module ${anonymize(module)}" }
      moduleClassLoader = ModuleClassLoader(parent, module)
      cache[module] = moduleClassLoader
      oldClassLoader?.let { disposeClassLoaderThreadLocals(it) }
    }

    return moduleClassLoader
  }

  /**
   * Return a [ModuleClassLoader] for a [Module] to be used for rendering. Similar to [getShared] but guarantees that the returned
   * [ModuleClassLoader] is not shared and the caller has full ownership of it.
   */
  fun getPrivate(parent: ClassLoader?, module: Module) = ModuleClassLoader(parent, module)

  @Synchronized
  fun clearCache() {
    cache.values.forEach { disposeClassLoaderThreadLocals(it) }
    cache.clear()
  }

  @Synchronized
  fun clearCache(module: Module) {
    cache.remove(module)?.let { disposeClassLoaderThreadLocals(it) }
  }

  @Synchronized
  private fun isManaged(moduleClassLoader: ModuleClassLoader) = cache.values.contains(moduleClassLoader)

  /**
   * Inform [ModuleClassLoaderManager] that [ModuleClassLoader] is not used anymore and therefore can be
   * disposed if no longer managed.
   */
  fun release(moduleClassLoader: ModuleClassLoader) {
    if (isManaged(moduleClassLoader)) {
      // We are still managing this ClassLoader, cannot dispose yet
      return
    }

    disposeClassLoaderThreadLocals(moduleClassLoader)
  }

  companion object {
    @JvmStatic
    private val LOG = Logger.getInstance(ModuleClassLoaderManager::class.java)

    @JvmStatic
    fun get(): ModuleClassLoaderManager =
      ApplicationManager.getApplication().getService(ModuleClassLoaderManager::class.java);

    // TODO(b/152947285): Remove all ThreadLocals that retain ModuleClassLoader from the user code
    //
    // Current approach, where we are searching for particular ThreadLocals with reflection is not
    // sustainable. We will not only fail in case compose changes its internals, but also if the user
    // code (which we have no control on) defines static ThreadLocals as well.
    private fun disposeClassLoaderThreadLocals(moduleClassLoader: ModuleClassLoader) {
      // If Compose has not been loaded, we do not need to care about disposing it
      if (!moduleClassLoader.isClassLoaded(CLASS_COMPOSE_VIEW_ADAPTER)) {
        return
      }

      try {
        val framesKtClass = moduleClassLoader.loadClass(CLASS_COMPOSE_FRAMES)
        val recomposerClass = moduleClassLoader.loadClass(CLASS_COMPOSE_RECOMPOSER)

        val threadLocalFields = listOf(
          framesKtClass.getDeclaredField("threadFrame"),
          framesKtClass.getDeclaredField("threadReadObservers"),
          recomposerClass.getDeclaredField("threadRecomposer"))

        threadLocalFields.forEach { it.isAccessible = true }

        // Because we are clearing-up ThreadLocals, the code must run on the Layoutlib Thread
        RenderService.getRenderAsyncActionExecutor().runAsyncAction {
          threadLocalFields.forEach {
            try {
              (it[null] as ThreadLocal<*>).remove()
            } catch (e: IllegalAccessException) {
              LOG.warn(e) // Failure detected here will most probably cause a memory leak
            }
          }
        }
      }
      catch (t: Throwable) {
        LOG.warn(t) // Failure detected here will most probably cause a memory leak
      }
    }
  }
}