/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.rendering.classloading.loaders

import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader
import com.intellij.util.containers.MultiMap
import com.intellij.util.text.nullize
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting

/**
 * Utility method that returns all package prefixes in descending length order for the given class name.
 *
 * For example, for `a.b.c.D`, will return `a.b.c`, `a.b` and `a` in that order.
 */
@VisibleForTesting
fun findAllPackagePrefixes(fqcn: String): Sequence<String> =
  generateSequence(fqcn.substringBeforeLast('.', "")) {
    it.substringBeforeLast('.', "").nullize()
  }

/**
 * Max limit of the number of keys we want to save in the affineLoaderIndex of the [MultiLoaderWithAffinity].
 */
private val DEFAULT_MAX_AFFINITY_CACHE_KEYS = Integer.getInteger("compose.preview.loader.affinity.cache.max.size", 1000)

/**
 * A [DelegatingClassLoader.Loader] that can use multiple delegate [DelegatingClassLoader.Loader]. The loader will change the lookup order
 * based on where classes have been found before. For example, if class A is found in the third loader, this one will be prioritized for
 * classes on the same package in future lookups.
 *
 * [maxAffinityCacheKeys] sets the maximum size of the cache for the package -> loader index. Keep in mind that this limit is not enforced
 * for a single class but for all so, if you load a single class with more than [maxAffinityCacheKeys] package elements, they will all
 * be cached. For example, if the limit is 2 and the class is `a.b.c.Class1`, the three `a`, `a.b` and `a.b.c` packages will be added to the
 * index even if it exceeds the [maxAffinityCacheKeys].
 */
class MultiLoaderWithAffinity(private val delegates: List<DelegatingClassLoader.Loader>,
                              private val maxAffinityCacheKeys: Int = DEFAULT_MAX_AFFINITY_CACHE_KEYS) : DelegatingClassLoader.Loader {
  constructor(vararg delegates: DelegatingClassLoader.Loader) : this(delegates.toList())

  /**
   * This saves, for every package prefix, which class loaders found it in the past. This allows to filter by package and first look up the
   * class loader that are more likely to have the class.
   */
  private val affineLoaderIndex: MultiMap<String, DelegatingClassLoader.Loader> = MultiMap.createConcurrentSet()

  @get:TestOnly
  val cacheSize: Int
    get() = affineLoaderIndex.size()

  override fun loadClass(fqcn: String): ByteArray? {
    val packages = findAllPackagePrefixes(fqcn)

    val affineLoaders = packages
      .flatMap { affineLoaderIndex[it] }
      .distinct()

    val affineClass = affineLoaders.mapNotNull { it.loadClass(fqcn) }.firstOrNull()
    if (affineClass != null) return affineClass

    val checkedLoaders = affineLoaders.toSet()
    return delegates
      .asSequence()
      .filter { !checkedLoaders.contains(it) } // We do not need to check again the affine loaders
      .mapNotNull {
        it.loadClass(fqcn)?.apply {
          if (affineLoaderIndex.size() < maxAffinityCacheKeys) {
            packages.forEach { packageName -> affineLoaderIndex.putValue(packageName, it) }
          }
        }
      }
      .firstOrNull()
  }
}