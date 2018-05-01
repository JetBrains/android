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
import com.android.tools.idea.gradle.structure.model.meta.DslMode
import com.android.tools.idea.gradle.structure.model.meta.DslText
import com.android.tools.idea.gradle.structure.model.meta.ModelPropertyContext
import com.android.tools.idea.gradle.structure.model.meta.ParsedValue

class PsVariables(private val module: PsModule) : VariablesProvider {
  override fun <ContextT, ModelT, ValueT : Any> getAvailableVariablesFor(
    context: ContextT,
    property: ModelPropertyContext<ContextT, ModelT, ValueT>
  ): List<ParsedValue.Set.Parsed<ValueT>> =
  // TODO(solodkyy): Merge with variables available at the project level.
    module.parsedModel?.inScopeProperties.orEmpty()
      .map { it.key to it.value.resolve() }
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
        it.first to it.second.getValue(GradlePropertyModel.OBJECT_TYPE)?.let { property.parse(context, it.toString()) }
      }
      .mapNotNull {
        val value = it.second
        when(value) {
          is ParsedValue.Set.Parsed<ValueT> -> ParsedValue.Set.Parsed(value.value, DslText(DslMode.REFERENCE, it.first))
          else -> null
        }
      }

  fun getModuleVariables(): List<PsVariable> = module.parsedModel?.ext()?.properties?.map { PsVariable(it, module) }.orEmpty()
}