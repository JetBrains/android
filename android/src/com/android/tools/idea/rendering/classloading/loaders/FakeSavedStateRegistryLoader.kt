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
package com.android.tools.idea.rendering.classloading.loaders

import com.android.tools.idea.rendering.classloading.FakeSavedStateRegistryClassDump

private const val FAKE_SAVEDSTATE_REGISTRY_PACKAGE_NAME = "_layoutlib_._internal_.androidx.lifecycle.FakeSavedStateRegistry"

/**
 * Loads the FakeSavedStateRegistry class from a generated JVM code class dump ([FakeSavedStateRegistryClassDump]).
 */
class FakeSavedStateRegistryLoader(private val delegate: DelegatingClassLoader.Loader) : DelegatingClassLoader.Loader {
  override fun loadClass(fqcn: String): ByteArray? {
    // Using the namespace androidx.lifecycle seems common.
    // To avoid possible conflicts we use an invalid package: "_layoutlib_._internal_"
    return if (fqcn == FAKE_SAVEDSTATE_REGISTRY_PACKAGE_NAME) {
      FakeSavedStateRegistryClassDump.lifecycleClassDump
    }
    else {
      delegate.loadClass(fqcn)
    }
  }
}