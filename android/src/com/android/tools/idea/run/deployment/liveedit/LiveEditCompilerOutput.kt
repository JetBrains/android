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

import com.intellij.openapi.module.Module

class LiveEditCompiledClass(val name: String, val data: ByteArray, val module: Module?)

data class LiveEditCompilerOutput private constructor (val classes: List<LiveEditCompiledClass>,
                                                       val supportClasses: List<LiveEditCompiledClass>,
                                                       val groupIds: List<Int>,
                                                       val resetState: Boolean) {

  private fun getMap(cls : List<LiveEditCompiledClass>) : Map<String, ByteArray> {
    val map : MutableMap<String, ByteArray> = HashMap()
    cls.forEach{
      map[it.name] = it.data
    }
    return map
  }

  val classesMap by lazy(LazyThreadSafetyMode.NONE) { getMap(classes)}
  val supportClassesMap by lazy(LazyThreadSafetyMode.NONE) { getMap(supportClasses)}


  class Builder(
    var classes: MutableList<LiveEditCompiledClass> = ArrayList(),
    var supportClasses: MutableList<LiveEditCompiledClass> = ArrayList(),
    var groupIds: ArrayList<Int> = ArrayList(),
    var resetState: Boolean = false) {

    fun addClass(clazz: LiveEditCompiledClass) : Builder {
      classes.add(clazz)
      return this
    }

    fun addSupportClass(clazz: LiveEditCompiledClass): Builder {
      supportClasses.add(clazz)
      return this
    }

    fun addGroupId(id: Int) : Builder{
      groupIds.add(id)
      return this
    }

    fun build() = LiveEditCompilerOutput(classes, supportClasses, groupIds, resetState)
  }
}