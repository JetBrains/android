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

import com.android.tools.rendering.classloading.loaders.DelegatingClassLoader

/**
 * Classloader used in rendering and responsible for loading classes for a specific android project
 * module, restricting and isolating access the same way it is done in the actual android
 * application.
 *
 * TODO(b/270114046): Rework this solution. Consider having a pure interface with with one of the
 *   method returning [ClassLoader] instead of extending abstract class and/or [ClassLoader] that
 *   reduces flexibility.
 */
abstract class ModuleClassLoader(parent: ClassLoader?, loader: Loader) :
  DelegatingClassLoader(parent, loader) {
  abstract val stats: ModuleClassLoaderDiagnosticsRead
  abstract val isDisposed: Boolean

  /**
   * Checks whether any of the .class files loaded by this loader have changed since the creation of
   * this class loader. Always returns false if there has not been any PSI changes.
   */
  abstract val isUserCodeUpToDate: Boolean

  /** Returns if the given [fqcn] has been loaded by this [ModuleClassLoader]. */
  abstract fun hasLoadedClass(fqcn: String): Boolean
}
