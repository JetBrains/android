/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.run.deployment.liveedit

import java.net.URL
import java.net.URLClassLoader

/**
 * Loads the target class of the generator's output in a throwaway classloader.
 *
 * Support classes will also be loaded in the SAME classloader.
 */
internal fun loadClass(output: AndroidLiveEditCodeGenerator.CodeGeneratorOutput, target : String = output.className) : Class<*> {
  // We use a temp classloader so we can have the same class name across different classes without conflict.
  val tempLoader = object : URLClassLoader(arrayOf(URL("jar:file:$composeRuntimePath!/"))) {
    override fun findClass(name: String): Class<*>? {
      return if (name == output.className) {
        // load it from the target
        defineClass(name, output.classData, 0, output.classData.size)
      } else if (output.supportClasses.containsKey(name)) {
        // try to see if it is one of the support classes
        defineClass(name, output.supportClasses[name], 0, output.supportClasses[name]!!.size)
      } else {
        return super.findClass(name)
      }
    }
  }
  return tempLoader.loadClass(target)
}

/**
 * Invoke a given function of a given class and return the return value.
 */
internal fun invokeStatic(name: String, clazz: Class<*>) : Any {
  return clazz.getMethod(name).invoke(null)
}