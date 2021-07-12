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
 * A [DelegatingClassLoader.Loader] that allows re-mapping the FQCN before passing it to the [delegate].
 */
class NameRemapperLoader(
  private val delegate: DelegatingClassLoader.Loader,
  private val mapping: (String) -> (String)): DelegatingClassLoader.Loader {
  override fun loadClass(fqcn: String): ByteArray? =
    delegate.loadClass(mapping(fqcn))
}