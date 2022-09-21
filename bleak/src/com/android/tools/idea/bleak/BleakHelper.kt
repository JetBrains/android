/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.bleak

import java.util.Vector

abstract class BleakHelper {
  private val loaderToClasses = mutableMapOf<ClassLoader?, MutableList<Class<*>>>()

  fun allClassLoaders(): Collection<ClassLoader?> {
    computeLoadedClasses().forEach {
      val klass = it as Class<*>
      val loader = klass.classLoader
      loaderToClasses.computeIfAbsent(loader) { mutableListOf() }.add(klass)
    }
    return loaderToClasses.keys
  }

  fun classesFor(cl: ClassLoader?) = loaderToClasses[cl]

  abstract fun computeLoadedClasses(): Collection<Any>
  abstract fun pauseThreads()
  abstract fun resumeThreads()
}

// non-JNI-based implementation so that at least something can be done without an agent or native code
class JavaBleakHelper: BleakHelper() {
  override fun pauseThreads() {}
  override fun resumeThreads() {}

  // this isn't all the classes, just the ones from this class loader and the system class loader.
  // this only works up to JDK 11. In JDK 17, the classes field is obscured from reflective access,
  // so JniBleakHelper is required.
  override fun computeLoadedClasses(): Collection<Any> {
    val loaders = listOf(JavaBleakHelper::class.java.classLoader, ClassLoader.getSystemClassLoader()).distinct()
    val classesField = ClassLoader::class.java.getDeclaredField("classes")
    classesField.isAccessible = true
    return loaders.flatMap { classesField.get(it) as Vector<*> }
  }

}