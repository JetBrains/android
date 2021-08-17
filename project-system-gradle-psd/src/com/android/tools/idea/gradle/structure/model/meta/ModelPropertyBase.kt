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

import com.android.tools.idea.concurrency.transform
import com.android.tools.idea.gradle.structure.model.PsVariablesScope
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors.directExecutor

abstract class ModelPropertyBase<in ModelT, ValueT : Any> {
  abstract val parser: (String) -> Annotated<ParsedValue<ValueT>>
  abstract val formatter: (ValueT) -> String
  abstract val knownValuesGetter: (ModelT) -> ListenableFuture<List<ValueDescriptor<ValueT>>>
  open val variableMatchingStrategy: VariableMatchingStrategy get() = VariableMatchingStrategy.BY_TYPE

  fun bindContext(model: ModelT): ModelPropertyContext<ValueT> = object : ModelPropertyContext<ValueT> {

    override fun parse(value: String): Annotated<ParsedValue<ValueT>> = parser(value)

    override fun format(value: ValueT): String = formatter(value)

    override fun getKnownValues(): ListenableFuture<KnownValues<ValueT>> =
      knownValuesGetter(model).transform(directExecutor()) {
        object : KnownValues<ValueT> {
          private val knownValues = variableMatchingStrategy.prepare(it)
          override val literals: List<ValueDescriptor<ValueT>> = it
          override fun isSuitableVariable(variable: Annotated<ParsedValue.Set.Parsed<ValueT>>): Boolean =
            variableMatchingStrategy.matches(variable.value, knownValues)
        }
      }
  }
}

