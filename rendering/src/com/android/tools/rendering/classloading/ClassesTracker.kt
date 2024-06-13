/*
 * Copyright (C) 2024 The Android Open Source Project
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

import com.google.common.collect.HashMultimap

object ClassesTracker {
  /**
   * Keeps track of used classes. Since [ClassesTracker] is a global object we need a way to
   * distinguish usage of classes in different classloaders. The key allows to do that given every
   * classloader passes a unique reference value as a key.
   */
  private val refToClasses = HashMultimap.create<String, String>()

  /** Records usage of class [className] by the reference [ref]. */
  @JvmStatic
  fun trackClass(ref: String, className: String) {
    refToClasses.put(ref, className)
  }

  @JvmStatic fun getClasses(ref: String): Set<String> = refToClasses[ref] ?: emptySet()

  @JvmStatic
  fun clear(ref: String) {
    refToClasses.removeAll(ref)
  }
}
