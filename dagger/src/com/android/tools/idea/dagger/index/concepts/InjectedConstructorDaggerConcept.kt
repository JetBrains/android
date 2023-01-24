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

import com.android.tools.idea.dagger.index.DaggerConceptIndexer
import com.android.tools.idea.dagger.index.DaggerConceptIndexers
import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.concepts.DaggerAttributes.INJECT
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import java.io.DataInput
import java.io.DataOutput
import org.jetbrains.annotations.VisibleForTesting

/**
 * Represents an injected constructor in Dagger.
 *
 * Example:
 * ```java
 *   class Thermosiphon {
 *     @Inject
 *     Thermosiphon(Heater heater) { ... }
 *   }
 * ```
 *
 * This concept deals with two types of index entries:
 *   1. The injected constructor (the `Thermosiphon` constructor), which is a Dagger Provider.
 *   2. The constructor's parameters (`heater`), which are Dagger Consumers.
 */
object InjectedConstructorDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(methodIndexers = listOf(InjectedConstructorIndexer))
  override val indexValueReaders = listOf(InjectedConstructorIndexValue.Reader, InjectedConstructorParameterIndexValue.Reader)
}

private object InjectedConstructorIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: MutableMap<String, MutableSet<IndexValue>>) {
    if (!wrapper.getIsConstructor() || !wrapper.getIsAnnotatedWith(INJECT)) return

    val classFqName = wrapper.getContainingClass()?.getFqName() ?: return
    indexEntries.addIndexValue(classFqName, InjectedConstructorIndexValue(classFqName))

    for (parameter in wrapper.getParameters()) {
      val parameterSimpleTypeName = parameter.getType().getSimpleName()
      val parameterName = parameter.getSimpleName()
      indexEntries.addIndexValue(parameterSimpleTypeName,
                                 InjectedConstructorParameterIndexValue(classFqName, parameterName))
    }
  }
}

@VisibleForTesting
internal data class InjectedConstructorIndexValue(val classFqName: String) : IndexValue(DataType.INJECTED_CONSTRUCTOR) {
  override fun save(output: DataOutput) {
    output.writeString(classFqName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.INJECTED_CONSTRUCTOR
    override fun read(input: DataInput) = InjectedConstructorIndexValue(input.readString())
  }
}

@VisibleForTesting
internal data class InjectedConstructorParameterIndexValue(val classFqName: String, val parameterName: String) : IndexValue(
  DataType.INJECTED_CONSTRUCTOR_PARAMETER) {
  override fun save(output: DataOutput) {
    output.writeString(classFqName)
    output.writeString(parameterName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.INJECTED_CONSTRUCTOR_PARAMETER
    override fun read(input: DataInput) = InjectedConstructorParameterIndexValue(input.readString(), input.readString())
  }
}
