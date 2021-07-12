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

/**
 * A [DelegatingClassLoader.Loader] that delegates the loads to [delegate] and calls [onBeforeLoad] and [onAfterLoad] before and after
 * loading the classes.
 */
open class ListeningLoader(private val delegate: DelegatingClassLoader.Loader,
                           private val onBeforeLoad: (fqcn: String) -> Unit = {},
                           private val onAfterLoad: (fqcn: String, bytes: ByteArray) -> Unit = { _, _ -> },
                           private val onNotFound: (fqcn: String) -> Unit = {}) : DelegatingClassLoader.Loader {
  override fun loadClass(fqcn: String): ByteArray? {
    onBeforeLoad(fqcn)
    val bytes = delegate.loadClass(fqcn)
    if (bytes != null) {
      onAfterLoad(fqcn, bytes)
    }
    else onNotFound(fqcn)
    return bytes
  }
}