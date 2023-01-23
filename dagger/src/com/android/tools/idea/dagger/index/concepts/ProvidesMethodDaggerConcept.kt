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
package com.android.tools.idea.dagger.index.concepts

import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.concepts.DaggerAttributes.MODULE
import com.android.tools.idea.dagger.index.concepts.DaggerAttributes.PROVIDES
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import org.jetbrains.annotations.VisibleForTesting

/**
 * Represents a provides method in Dagger.
 *
 * Example:
 * ```java
 *   @Module
 *   interface HeaterModule {
 *     @Provides
 *     static Heater provideHeater(ElectricHeater electricHeater) { ... }
 *   }
 * ```
 *
 * This concept deals with two types of index entries:
 *   1. The provides method (`HeaterModule.provideHeater`), which is a Dagger Provider.
 *   2. The method's parameters (`electricHeater`), which are Dagger Consumers.
 */
object ProvidesMethodDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(methodIndexers = listOf(ProvidesMethodIndexer))
}

private object ProvidesMethodIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: MutableMap<String, MutableSet<IndexValue>>) {
    if (!wrapper.getIsAnnotatedWith(PROVIDES)) return

    val containingClass = wrapper.getContainingClass() ?: return
    if (!containingClass.getIsAnnotatedWith(MODULE)) return

    val classFqName = containingClass.getFqName()
    val methodSimpleName = wrapper.getSimpleName()
    val returnTypeSimpleName = wrapper.getReturnType()?.getSimpleName() ?: ""

    indexEntries.addIndexValue(returnTypeSimpleName,
                               ProvidesMethodIndexValue(classFqName, methodSimpleName))

    for (parameter in wrapper.getParameters()) {
      val parameterSimpleTypeName = parameter.getType().getSimpleName()
      val parameterName = parameter.getSimpleName()
      indexEntries.addIndexValue(parameterSimpleTypeName,
                                 ProvidesMethodParameterIndexValue(classFqName, methodSimpleName, parameterName))
    }
  }
}

@VisibleForTesting
internal data class ProvidesMethodIndexValue(val classFqName: String, val methodSimpleName: String) : IndexValue(DataType.PROVIDES_METHOD)

@VisibleForTesting
internal data class ProvidesMethodParameterIndexValue(val classFqName: String,
                                                      val methodSimpleName: String,
                                                      val parameterName: String) : IndexValue(DataType.PROVIDES_METHOD_PARAMETER)
