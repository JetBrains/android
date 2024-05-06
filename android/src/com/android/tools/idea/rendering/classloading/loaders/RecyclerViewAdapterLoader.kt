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
import com.android.tools.rendering.RecyclerViewHelper

/**
 * Simple [DelegatingClassLoader.Loader] that delegates to [RecyclerViewHelper.getAdapterHelperClass]. This loader
 * will load custom adapters for `RecyclerView`s.
 *
 * It is recommended as, for example, a last step of a [MultiLoader] to allow loading the adapter helper classes when
 * they are not found in any other class loader.
 */
class RecyclerViewAdapterLoader : DelegatingClassLoader.Loader {
  override fun loadClass(fqcn: String): ByteArray? = RecyclerViewHelper.getAdapterHelperClass(fqcn)
}