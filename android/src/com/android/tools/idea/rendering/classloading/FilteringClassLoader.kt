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
package com.android.tools.idea.rendering.classloading

/**
 * A class loader that allows filtering the loading of certain classes.
 *
 * The class will be allowed to load if it exists and [allow] returns true for the name.
 */
class FilteringClassLoader(parent: ClassLoader?, private val allow: (String) -> Boolean): ClassLoader(parent) {
  override fun loadClass(name: String, resolve: Boolean): Class<*> {
    if (!allow(name)) throw ClassNotFoundException(name)
    return super.loadClass(name, resolve)
  }

  override fun findClass(name: String): Class<*> {
    if (!allow(name)) throw ClassNotFoundException(name)
    return super.findClass(name)
  }

  companion object {
    /**
     * Utility method that creates a [FilteringClassLoader] that does not allow the loading of classes that start
     * with any of the given prefixes.
     */
    @JvmStatic
    fun disallowedPrefixes(
      parent: ClassLoader?,
      prefixes: Collection<String>): FilteringClassLoader {
      // Sort by increasing length to make the shorter (more generic) prefixes first
      val prefixesArray = prefixes
        .distinct()
        .sortedBy { it.length }
        .toTypedArray()
      return FilteringClassLoader(parent) { fqcn -> prefixesArray.none { fqcn.startsWith(it) } }
    }

    /**
     * Utility method that creates a [FilteringClassLoader] that allows
     * only the classes from the given prefixes.
     */
    @JvmStatic
    fun allowedPrefixes(parent: ClassLoader?, prefixes: Collection<String>): FilteringClassLoader {
      // Sort by increasing length to make the shorter (more generic) prefixes first
      val prefixesArray = prefixes
        .distinct()
        .sortedBy { it.length }
        .toTypedArray()
      return FilteringClassLoader(parent) { fqcn -> prefixesArray.any { fqcn.startsWith(it) } }
    }
  }
}