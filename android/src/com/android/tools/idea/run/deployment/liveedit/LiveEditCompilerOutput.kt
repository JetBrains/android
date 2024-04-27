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

import com.android.tools.idea.run.deployment.liveedit.analysis.leir.IrClass
import com.intellij.openapi.module.Module

internal enum class LiveEditClassType {
  NORMAL_CLASS,
  SUPPORT_CLASS,
}

internal class LiveEditCompiledClass(val name: String, var data: ByteArray, val module: Module?, val type: LiveEditClassType)

data class LiveEditCompilerOutput internal constructor (internal val classes: List<LiveEditCompiledClass> = emptyList(),
                                                        val irClasses: List<IrClass> = emptyList(),
                                                        val groupIds: List<Int> = emptyList(),
                                                        val resetState: Boolean = false) {

  private fun getMap(type: LiveEditClassType) : Map<String, ByteArray> {
    val map : MutableMap<String, ByteArray> = HashMap()
    classes.forEach{
      if (it.type != type) {
        return@forEach
      }
      map[it.name] = it.data
    }
    return map
  }

  val classesMap by lazy(LazyThreadSafetyMode.NONE) { getMap(LiveEditClassType.NORMAL_CLASS)}
  val supportClassesMap by lazy(LazyThreadSafetyMode.NONE) { getMap(LiveEditClassType.SUPPORT_CLASS)}


  internal class Builder(
    var classes: MutableList<LiveEditCompiledClass> = ArrayList(),
    val groupIds: MutableSet<Int> = mutableSetOf(),
    var resetState: Boolean = false) {

    private val irClasses = mutableListOf<IrClass>()

    fun addClass(clazz: LiveEditCompiledClass) : Builder {
      classes.add(clazz)
      return this
    }

    fun addIrClass(irClass: IrClass) : Builder {
      irClasses.add(irClass)
      return this
    }

    fun addGroupId(id: Int) : Builder{
      groupIds.add(id)
      return this
    }

    fun build() = LiveEditCompilerOutput(classes, irClasses, groupIds.toList(), resetState)
  }
}