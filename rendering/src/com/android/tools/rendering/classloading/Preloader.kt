/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.rendering.classloading

import java.lang.ref.SoftReference
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/**
 * This is a wrapper around a class preloading [CompletableFuture] that allows for the proper
 * disposal of the resources used.
 */
open class Preloader<T : ModuleClassLoader>(
  moduleClassLoader: T,
  executor: Executor,
  classesToPreload: Collection<String> = emptyList(),
) {
  private val classLoader = SoftReference(moduleClassLoader)
  private var isActive = AtomicBoolean(true)

  init {
    if (classesToPreload.isNotEmpty()) {
      preload(
        moduleClassLoader,
        { isActive.get() && !(classLoader.get()?.isDisposed ?: true) },
        classesToPreload,
        executor,
      )
    }
  }

  /** Cancels the on-going preloading. */
  fun cancel() {
    isActive.set(false)
  }

  fun dispose() {
    cancel()
    val classLoaderToDispose = classLoader.get()
    classLoader.clear()
    classLoaderToDispose?.dispose()
  }

  fun getClassLoader(): T? {
    cancel() // Stop preloading since we are going to use the class loader
    return classLoader.get()
  }

  /**
   * Checks if this [Preloader] loads classes for [cl] [ModuleClassLoader]. This allows for safe
   * check without the need for share the actual [classLoader] and prevent its use.
   */
  fun isLoadingFor(cl: ModuleClassLoader) = classLoader.get() == cl

  fun isForCompatible(
    parent: ClassLoader?,
    projectTransformations: ClassTransform,
    nonProjectTransformations: ClassTransform,
  ) =
    classLoader.get()?.isCompatible(parent, projectTransformations, nonProjectTransformations)
      ?: false

  /**
   * Returns the number of currently loaded classes for the underlying [ModuleClassLoader]. Intended
   * to be used for debugging and diagnostics.
   */
  fun getLoadedCount(): Int =
    classLoader.get()?.let { it.nonProjectLoadedClasses.size + it.projectLoadedClasses.size } ?: 0
}
