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

import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.intellij.openapi.diagnostic.Logger
import java.util.concurrent.ConcurrentHashMap

/**
 * [PseudoClassLocator] that uses the [Sequence] of [DelegatingClassLoader.Loader]s to find the `.class` file. If a class is not found
 * within the [loaders], this class will try to load it from the given [fallbackClassloader] allowing to load system classes from it.
 */
class PseudoClassLocatorForLoader(
  private val loaders: Sequence<DelegatingClassLoader.Loader>,
  private val fallbackClassloader: ClassLoader?,
) : PseudoClassLocator {

  constructor(loader: DelegatingClassLoader.Loader, classLoader: ClassLoader) : this(sequenceOf(loader), classLoader)

  private val cache = ConcurrentHashMap<String, PseudoClass>()

  override fun locatePseudoClass(classFqn: String): PseudoClass {
    if (classFqn == PseudoClass.objectPseudoClass().name) return PseudoClass.objectPseudoClass() // Avoid hitting this for this common case

    return cache.computeIfAbsent(classFqn) { key ->
      val bytes = loaders.map { it.loadClass(key) }.firstNotNullOfOrNull { it }
      if (bytes != null) {
        PseudoClass.fromByteArray(bytes, this)
      } else if (fallbackClassloader != null) {
        try {
          PseudoClass.fromClass(fallbackClassloader.loadClass(key), this)
        } catch (ex: ClassNotFoundException) {
          Logger.getInstance(PseudoClassLocatorForLoader::class.java).warn("Failed to load $key", ex)
          PseudoClass.objectPseudoClass()
        }
      } else {
        Logger.getInstance(PseudoClassLocatorForLoader::class.java).warn("No classloader is provided to load $key")
        PseudoClass.objectPseudoClass()
      }
    }
  }
}
