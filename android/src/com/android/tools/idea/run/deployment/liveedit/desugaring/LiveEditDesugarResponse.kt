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
package com.android.tools.idea.run.deployment.liveedit.desugaring

import com.android.tools.idea.run.deployment.liveedit.LiveEditCompilerOutput
import com.android.tools.idea.run.deployment.liveedit.LiveEditUpdateException.Companion.desugarFailure

class LiveEditDesugarResponse(val compilerOutput: LiveEditCompilerOutput) {
  private val apiToClasses: MutableMap<MinApiLevel, Map<ClassName, ByteCode>> = mutableMapOf()
  val classes: Map<MinApiLevel, Map<ClassName, ByteCode>>
    get() = apiToClasses

  internal fun addOutputSet(minApiLevel: MinApiLevel, classes: Map<ClassName, ByteCode>) {
    apiToClasses[minApiLevel] = classes
  }

  fun resetState() : Boolean {
    return compilerOutput.resetState
  }

  fun groupIds(): List<Int> {
    return compilerOutput.groupIds
  }

  private fun getClasses(classNames : Set<String>, apiLevel: MinApiLevel): MutableMap<String, ByteArray> {
    if (!apiToClasses.containsKey(apiLevel)) {
      desugarFailure("No desugared classes for api=$apiLevel")
    }

    val classes: MutableMap<String, ByteArray> = java.util.HashMap()
    for (className in classNames) {
      if (!apiToClasses[apiLevel]!!.containsKey(className)) {
        desugarFailure("Desugared classes api $apiLevel does not contain $className")
      }
      classes[className] = apiToClasses[apiLevel]!![className]!!
    }
    return classes
  }

  fun classes(apiLevel: MinApiLevel): MutableMap<String, ByteArray> {
    return getClasses(compilerOutput.classesMap.keys, apiLevel)
  }

  fun supportClasses(apiLevel: MinApiLevel): MutableMap<String, ByteArray> {
    return getClasses(compilerOutput.supportClassesMap.keys, apiLevel)
  }
}