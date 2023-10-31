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

import com.android.tools.rendering.classloading.ClassBinaryCache
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader

/**
 * A [DelegatingClassLoader.Loader] that uses the given [binaryCache] to store the returned classes.
 * The given [transformationId] is used to ensure the classes stored have the same transformations applied and they
 * have not been invalidated.
 *
 * This class only loads from the cache and does not do any writing on the given [binaryCache].
 */
class ClassBinaryCacheLoader(
  private val delegate: DelegatingClassLoader.Loader,
  private val transformationId: String,
  private val binaryCache: ClassBinaryCache) : DelegatingClassLoader.Loader {
  override fun loadClass(fqcn: String): ByteArray? = binaryCache.get(fqcn, transformationId) ?: delegate.loadClass(fqcn)
}