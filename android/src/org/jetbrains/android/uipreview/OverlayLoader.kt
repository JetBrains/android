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
package org.jetbrains.android.uipreview

import com.android.tools.rendering.classloading.ClassLoaderOverlays
import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader

/**
 * A [DelegatingClassLoader.Loader] that loads classes from a different path.
 *
 * The overlay is a set of `.class` files that have been modified and that will
 * take priority when loading from this.
 * This allows to have modifications for specific classes to be loaded. The overlay classes are also located outside
 * of the usual output directories used by IntelliJ/Gradle/Bazel.
 */
internal class OverlayLoader(private val overlayManager: ClassLoaderOverlays) : DelegatingClassLoader.Loader {
  override fun loadClass(fqcn: String): ByteArray? = overlayManager.classLoaderLoader.loadClass(fqcn)
}