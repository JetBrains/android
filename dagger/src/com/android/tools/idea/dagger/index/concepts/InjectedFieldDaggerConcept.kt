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
import com.android.tools.idea.dagger.index.IndexEntries
import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.concepts.DaggerAttributes.INJECT
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexFieldWrapper
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import java.io.DataInput
import java.io.DataOutput
import org.jetbrains.annotations.VisibleForTesting

/**
 * Represents an injected field in Dagger.
 *
 * Example:
 * ```java
 * class CoffeeMaker {
 *   @Inject Heater heater;
 *   @Inject Pump pump;
 *   ...
 * }
 * ```
 *
 * This concept deals with one type of index entry:
 *   1. The injected field (`heater` and `pump`).
 */
object InjectedFieldDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(fieldIndexers = listOf(InjectedFieldIndexer))
  override val indexValueReaders: List<IndexValue.Reader> = listOf(InjectedFieldIndexValue.Reader)
}

private object InjectedFieldIndexer : DaggerConceptIndexer<DaggerIndexFieldWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexFieldWrapper, indexEntries: IndexEntries) {
    if (!wrapper.getIsAnnotatedWith(INJECT)) return

    val fieldTypeSimpleName = wrapper.getType()?.getSimpleName() ?: ""
    val classFqName = wrapper.getContainingClass().getFqName()
    val fieldName = wrapper.getSimpleName()
    indexEntries.addIndexValue(fieldTypeSimpleName, InjectedFieldIndexValue(classFqName, fieldName))
  }
}

@VisibleForTesting
internal data class InjectedFieldIndexValue(val classFqName: String, val fieldName: String) : IndexValue(DataType.INJECTED_FIELD) {
  override fun save(output: DataOutput) {
    output.writeString(classFqName)
    output.writeString(fieldName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.INJECTED_FIELD
    override fun read(input: DataInput) = InjectedFieldIndexValue(input.readString(), input.readString())
  }
}
