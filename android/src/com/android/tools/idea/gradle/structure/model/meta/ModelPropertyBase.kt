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
package com.android.tools.idea.gradle.structure.model.meta

import com.android.tools.idea.projectsystem.transform
import com.google.common.util.concurrent.ListenableFuture

abstract class ModelPropertyBase<in ContextT, in ModelT, ValueT : Any> {
  abstract val parser: (ContextT, String) -> ParsedValue<ValueT>
  abstract val formatter: (ContextT, ValueT) -> String
  abstract val knownValuesGetter: (ContextT, ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>
  open val variableMatchingStrategy: VariableMatchingStrategy get() = VariableMatchingStrategy.BY_TYPE

  fun bindContext(context: ContextT, model: ModelT): ModelPropertyContext<ValueT> = object : ModelPropertyContext<ValueT> {

    override fun parse(value: String): ParsedValue<ValueT> = parser(context, value)

    override fun format(value: ValueT): String = formatter(context, value)

    override fun getKnownValues(): ListenableFuture<KnownValues<ValueT>> =
      knownValuesGetter(context, model).transform {
        object : KnownValues<ValueT> {
          private val knownValues = variableMatchingStrategy.prepare(it)
          override val literals: List<ValueDescriptor<ValueT>> = it
          override fun isSuitableVariable(variable: ParsedValue.Set.Parsed<ValueT>): Boolean =
            variableMatchingStrategy.matches(variable, knownValues)
        }
      }
  }
}

