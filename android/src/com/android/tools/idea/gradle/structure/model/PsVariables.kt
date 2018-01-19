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
package com.android.tools.idea.gradle.structure.model

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.model.ext.ResolvedPropertyModelImpl
import java.io.File

class PsVariables(private val module: PsModule) : VariablesProvider {
  override fun <T> getAvailableVariablesForType(type: Class<T>): List<Pair<String, T?>> =
      // TODO(solodkyy): Merge with variables available at the project level.
    module.parsedModel?.ext()?.properties.orEmpty()
      .map { it.name to ResolvedPropertyModelImpl(it) }
      .flatMap {
        when (it.second.valueType) {
          GradlePropertyModel.ValueType.LIST -> listOf()
          GradlePropertyModel.ValueType.MAP ->
            it.second.getValue(GradlePropertyModel.MAP_TYPE)?.map { entry ->
              "${it.first}.${entry.key}" to entry.value
            }.orEmpty()
          else -> listOf(it)
        }
      }
      .map {
        val value = it.second.getValue(GradlePropertyModel.OBJECT_TYPE)
          ?.let {
            when {
              // TODO(solodkyy): Find out how to abstract this.
              it is Int && type == String::class.java -> it.toString()
              it is String && type == File::class.java -> File(it)
              // Int and Integer are not exactly the same in Kotlin and the 'cast' below does not work.
              it is Int &&type == Int::class.java -> it
              else -> it
            }
          }
        it.first to (if (type.isInstance(value)) type.cast(value) else null)
      }
      .filter { it.second != null }

  fun getModuleVariables(): List<PsVariable> = module.parsedModel?.ext()?.properties?.map { PsVariable(it, module) }.orEmpty()
}