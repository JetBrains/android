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

import com.android.tools.idea.LogAnonymizerUtil.anonymize
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.module.Module
import java.util.WeakHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * A [ClassLoader] for the [Module] dependencies.
 */
class ModuleClassLoaderManager {
  private val cacheLock: ReentrantReadWriteLock = ReentrantReadWriteLock()
  private val cache: MutableMap<Module, ModuleClassLoader> = WeakHashMap();

  /**
   * Returns a project class loader to use for rendering. May cache instances across render sessions.
   */
  fun get(parent: ClassLoader?, module: Module): ModuleClassLoader {
    cacheLock.read {
      var moduleClassLoader =
        cache[module]

      if (moduleClassLoader != null) {
        if (moduleClassLoader.parent != parent) {
          LOG.debug("Parent has changed, discarding ModuleClassLoader")
          moduleClassLoader = null
        }
        else if (!moduleClassLoader.isUpToDate) {
          LOG.debug("Files have changed, discarding ModuleClassLoader")
          moduleClassLoader = null
        }
        else {
          LOG.debug("ModuleClassLoader is up to date")
        }
      }

      if (moduleClassLoader == null) {
        LOG.debug { "Loading new class loader for module ${anonymize(module)}" }
        moduleClassLoader = ModuleClassLoader(parent, module)
        cacheLock.write {
          cache[module] = moduleClassLoader
        }
      }

      return moduleClassLoader
    }
  }

  fun clearCache() = cacheLock.write {
    cache.clear();
  }

  fun clearCache(module: Module) = cacheLock.write {
    cache.remove(module)
  }

  /**
   * Manually disposes the cache.
   */
  fun disposeCache() = cache.clear()

  companion object {
    @JvmStatic
    private val LOG = Logger.getInstance(ModuleClassLoaderManager::class.java)

    @JvmStatic
    fun get(): ModuleClassLoaderManager =
      ApplicationManager.getApplication().getService(ModuleClassLoaderManager::class.java);
  }
}