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

/** Interface for loading classes that can change with time. */
interface ClassLoaderOverlays {
  /**
   * Increases every time the classes are changed. One can periodically check this value against the
   * previously read value to know whether the classes were changed since the last check. The values
   * constitute a non-decreasing sequence of non-negative numbers.
   */
  val modificationStamp: Long

  /** Loader for loading classes. */
  val classLoaderLoader: DelegatingClassLoader.Loader
}
