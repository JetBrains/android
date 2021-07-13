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
 * A [DelegatingClassLoader.Loader] that has a static mapping of the FQCN and the byte array representation.
 * Mainly useful for testing or cases where the elements can be loaded ahead of time and retained.
 */
class StaticLoader(private val classes: Map<String, ByteArray>) : DelegatingClassLoader.Loader {
  constructor(vararg pairs: Pair<String, ByteArray>) : this(mapOf(*pairs))

  override fun loadClass(fqcn: String): ByteArray? = classes[fqcn]
}

/**
 * Instance of a [DelegatingClassLoader.Loader] that never succeeds to find a class.
 */
object NopLoader : DelegatingClassLoader.Loader {
  override fun loadClass(fqcn: String): ByteArray? = null
}

/**
 * A [ClassLoader] that delegates the loading of the classes to a [DelegatingClassLoader.Loader]. This allows the
 * creation of class loaders that do loading differently without using inheritance.
 */
open class DelegatingClassLoader(parent: ClassLoader?, private val loader: Loader) : ClassLoader(parent) {
  /**
   * Interface to be implemented by classes that can load classes.
   */
  interface Loader {
    /**
     * Loads a class and returns the [ByteArray] representation or null if it could not be loaded.
     */
    fun loadClass(fqcn: String): ByteArray?
  }

  @Throws(ClassNotFoundException::class)
  final override fun loadClass(name: String): Class<*> {
    onBeforeLoadClass(name)
    val start = System.currentTimeMillis()
    var loaded = false
    try {
      val clazz = super.loadClass(name)
      loaded = true
      return clazz
    }
    finally {
      onAfterLoadClass(name, loaded, System.currentTimeMillis() - start)
    }
  }

  @Throws(ClassNotFoundException::class)
  final override fun findClass(name: String): Class<*> {
    onBeforeFindClass(name)
    val start = System.currentTimeMillis()
    var found = false
    try {
      val bytes = loader.loadClass(name)
                  ?: throw ClassNotFoundException(name)

      val clazz = defineClass(name, bytes, 0, bytes.size)
      found = true
      return clazz
    }
    finally {
      onAfterFindClass(name, found, System.currentTimeMillis() - start)
    }
  }

  /**
   * Called when [ClassLoader.loadClass] stats.
   */
  protected open fun onBeforeLoadClass(fqcn: String) {}

  /**
   * Called when [ClassLoader.loadClass] finishes.
   * @param fqcn the Fully Qualified Name of the class.
   * @param loaded true if the class was loaded or false otherwise.
   * @param durationMs time in milliseconds that the load took.
   */
  protected open fun onAfterLoadClass(fqcn: String, loaded: Boolean, durationMs: Long) {}

  /**
   * Called when [ClassLoader.findClass] stats.
   */
  protected open fun onBeforeFindClass(fqcn: String) {}

  /**
   * Called when [ClassLoader.findClass] ends.
   * @param fqcn the Fully Qualified Name of the class.
   * @param found true if the class was found or false otherwise.
   * @param durationMs time in milliseconds that the lookup took.
   */
  protected open fun onAfterFindClass(fqcn: String, found: Boolean, durationMs: Long) {}
}