/*
 * Copyright (C) 2023 The Android Open Source Project
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

import com.android.tools.rendering.ModuleRenderContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.Module
import java.io.Closeable
import java.lang.ref.WeakReference

inline fun <T : ModuleClassLoader, R> ModuleClassLoaderManager.Reference<T>.useWithClassLoader(
  block: (T) -> R
) = use { block(classLoader) }

/**
 * Responsible for providing access to [ModuleClassLoader]s.
 *
 * This is required because normally [ModuleClassLoader] is a very heavy resource, and it is
 * important to keep as few instances of those as possible and delete those right after they are no
 * longer needed.
 */
interface ModuleClassLoaderManager<T : ModuleClassLoader> {
  /**
   * A reference to a [ModuleClassLoader]. This reference is used as a token for users of the
   * [ModuleClassLoaderManager] to ensure the correct handling of shared and private module class
   * loaders. When the caller finishes using the [ModuleClassLoader] it should call
   * [ModuleClassLoaderManager.release] or [Reference.close] to release the reference.
   *
   * References implement [Closeable] to they can be used in try/catch blocks with automatic
   * release.
   *
   * Released references should not be used after the release call.
   */
  class Reference<T : ModuleClassLoader>(manager: ModuleClassLoaderManager<T>, val classLoader: T) :
    Closeable {
    private val managerOwnerRef = WeakReference(manager)

    override fun close() {
      managerOwnerRef.get()?.release(this)
    }
  }

  companion object {
    @JvmStatic
    fun get(): ModuleClassLoaderManager<*> =
      ApplicationManager.getApplication().getService(ModuleClassLoaderManager::class.java)
  }

  fun getShared(
    parent: ClassLoader?,
    moduleRenderContext: ModuleRenderContext,
    additionalProjectTransformation: ClassTransform = ClassTransform.identity,
    additionalNonProjectTransformation: ClassTransform = ClassTransform.identity,
    onNewModuleClassLoader: Runnable = Runnable {},
  ): Reference<T>

  // Workaround for interfaces not currently supporting @JvmOverloads
  // (https://youtrack.jetbrains.com/issue/KT-36102)
  fun getShared(parent: ClassLoader?, moduleRenderContext: ModuleRenderContext): Reference<T> =
    getShared(parent, moduleRenderContext, ClassTransform.identity, ClassTransform.identity) {}

  fun getPrivate(
    parent: ClassLoader?,
    moduleRenderContext: ModuleRenderContext,
    additionalProjectTransformation: ClassTransform = ClassTransform.identity,
    additionalNonProjectTransformation: ClassTransform = ClassTransform.identity,
  ): Reference<T>

  fun getPrivate(parent: ClassLoader?, moduleRenderContext: ModuleRenderContext): Reference<T> =
    getPrivate(parent, moduleRenderContext, ClassTransform.identity, ClassTransform.identity)

  fun release(moduleClassLoaderReference: Reference<*>)

  fun clearCache(module: Module)
}
