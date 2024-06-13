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
package com.android.tools.rendering.classloading.loaders

import com.android.SdkConstants
import com.android.tools.rendering.classloading.fromPackageNameToBinaryName
import com.google.common.io.ByteStreams

/**
 * A [DelegatingClassLoader.Loader] that delegates the class finding to a given [ClassLoader]. This
 * is different to using a [ClassLoader] directly because it does NOT define the class, it only
 * loads the bytes from disk. This allows for other [DelegatingClassLoader.Loader]s to do additional
 * transformations, caching or anything needed before the actual definition of the class happens.
 */
class ClassLoaderLoader
@JvmOverloads
constructor(
  private val classLoader: ClassLoader,
  private val onLoadedClass: (String, String, ByteArray) -> Unit = { _, _, _ -> },
) : DelegatingClassLoader.Loader {
  override fun loadClass(fqcn: String): ByteArray? {
    val diskName = fqcn.fromPackageNameToBinaryName() + SdkConstants.DOT_CLASS
    val classUrl = classLoader.getResource(diskName) ?: return null
    // We do not request the stream from URL because it is problematic, see
    // https://stackoverflow.com/questions/7071761
    val bytes =
      classLoader.getResourceAsStream(diskName).use {
        ByteStreams.toByteArray(it ?: return@use null)
      }

    if (bytes != null) {
      onLoadedClass(fqcn, classUrl.path, bytes)
    }
    return bytes
  }
}
