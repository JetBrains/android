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

import com.android.layoutlib.reflection.TrackingThreadLocal
import com.android.tools.idea.LogAnonymizerUtil.anonymize
import com.android.tools.idea.rendering.RenderService
import com.android.tools.idea.rendering.classloading.combine
import com.android.tools.idea.rendering.classloading.multiTransformOf
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import org.jetbrains.android.uipreview.ModuleClassLoader.NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS
import org.jetbrains.android.uipreview.ModuleClassLoader.PROJECT_DEFAULT_TRANSFORMS
import org.jetbrains.org.objectweb.asm.ClassVisitor
import java.util.Collections
import java.util.WeakHashMap
import java.util.function.Function.identity

private val DUMMY_HOLDER = Any()
/**
 * A [ClassLoader] for the [Module] dependencies.
 */
class ModuleClassLoaderManager {
  private val cache: MutableMap<Module, ModuleClassLoader> = WeakHashMap()
  // MutableSet is backed by the WeakHashMap in prod so we do not retain the holders
  private val holders: MutableMap<ModuleClassLoader, MutableSet<Any>> = HashMap()

  /**
   * Returns a project class loader to use for rendering. May cache instances across render sessions.
   */
  @Synchronized
  fun getShared(parent: ClassLoader?, module: Module, holder: Any): ModuleClassLoader {
    var moduleClassLoader = cache[module]

    var oldClassLoader: ModuleClassLoader? = null
    if (moduleClassLoader != null) {
      // We treat the null parent case as a request of the current shared ClassLoader without simply knowing the parent, so do NOT recreate
      if (parent != null && moduleClassLoader.parent != parent) {
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
      oldClassLoader?.let { release(it, DUMMY_HOLDER) }
    }

    holders.computeIfAbsent(moduleClassLoader) { createHoldersSet() }.apply { add(holder) }
    return moduleClassLoader
  }

  /**
   * Return a [ModuleClassLoader] for a [Module] to be used for rendering. Similar to [getShared] but guarantees that the returned
   * [ModuleClassLoader] is not shared and the caller has full ownership of it.
   */
  @JvmOverloads
  @Synchronized
  fun getPrivate(parent: ClassLoader?,
                 module: Module,
                 holder: Any,
                 additionalProjectTransformation: java.util.function.Function<ClassVisitor, ClassVisitor> = identity(),
                 additionalNonProjectTransformation: java.util.function.Function<ClassVisitor, ClassVisitor> = identity()) =
    ModuleClassLoader(parent, module,
                      combine(PROJECT_DEFAULT_TRANSFORMS, additionalProjectTransformation),
                      combine(NON_PROJECT_CLASSES_DEFAULT_TRANSFORMS, additionalNonProjectTransformation)).apply {
    holders[this] = createHoldersSet().apply { add(holder) }
  }

  /**
   * Creates a [MutableMap] to be used as a storage of [ModuleClassLoader] holders. We would like the implementation to be different in
   * prod and in tests:
   *
   * In Prod, it should be a Set of WEAK references. So that in case we do not release the holder (due to some unexpected flow) it is not
   * retained by the [ModuleClassLoaderManager]
   *
   * In Tests, we would like it to be a Set of STRONG references. So that any unreleased references got caught by the LeakHunter.
   */
  private fun createHoldersSet(): MutableSet<Any> {
    if (ApplicationManager.getApplication().isUnitTestMode) {
      return mutableSetOf()
    } else {
      return Collections.newSetFromMap(WeakHashMap())
    }
  }

  @Synchronized
  fun clearCache() {
    val values = cache.values.toList()
    cache.clear()
    values.forEach { release(it, DUMMY_HOLDER) }
  }

  @Synchronized
  fun clearCache(module: Module) {
    cache.remove(module)?.let { release(it, DUMMY_HOLDER) }
  }

  @Synchronized
  private fun isManaged(moduleClassLoader: ModuleClassLoader) = cache.values.contains(moduleClassLoader)

  @Synchronized
  private fun unHold(moduleClassLoader: ModuleClassLoader, holder: Any) {
    holders[moduleClassLoader]?.let {
      it.remove(holder)
      if (it.isEmpty()) {
        holders.remove(moduleClassLoader)
      }
    }
  }

  @Synchronized
  private fun isHeld(moduleClassLoader: ModuleClassLoader) = holders[moduleClassLoader]?.isNotEmpty() == true

  /**
   * Inform [ModuleClassLoaderManager] that [ModuleClassLoader] is not used anymore and therefore can be
   * disposed if no longer managed.
   */
  fun release(moduleClassLoader: ModuleClassLoader, holder: Any) {
    unHold(moduleClassLoader, holder)
    if (isHeld(moduleClassLoader) || isManaged(moduleClassLoader)) {
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
      ApplicationManager.getApplication().getService(ModuleClassLoaderManager::class.java)
  }

  private fun disposeClassLoaderThreadLocals(moduleClassLoader: ModuleClassLoader) {
    TrackingThreadLocal.clearThreadLocals(moduleClassLoader)?.let { threadLocals ->
      if (threadLocals.isEmpty()) {
        return
      }

      // Because we are clearing-up ThreadLocals, the code must run on the Layoutlib Thread
      RenderService.getRenderAsyncActionExecutor().runAsyncAction {
        threadLocals.forEach { threadLocal ->
          try {
            threadLocal.remove()
          } catch (e: Exception) {
            LOG.warn(e) // Failure detected here will most probably cause a memory leak
          }
        }
      }
    }
  }
}