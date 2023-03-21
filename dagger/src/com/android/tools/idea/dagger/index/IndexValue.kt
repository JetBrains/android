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
package com.android.tools.idea.dagger.index

import com.android.tools.idea.dagger.concepts.AllConcepts
import com.android.tools.idea.dagger.concepts.DaggerConcept
import com.android.tools.idea.dagger.concepts.DaggerElement
import com.android.tools.idea.dagger.concepts.DaggerElementIdentifiers
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput
import org.jetbrains.annotations.VisibleForTesting

/**
 * An index value for the Dagger index. Each [DaggerConcept] is responsible for defining the exact
 * data that it needs to store for its entries.
 */
abstract class IndexValue {

  /** The [DataType] represented by this [IndexValue] instance. */
  abstract val dataType: DataType

  /**
   * Type of value being stored. This is required to be centralized to ensure that each type has a
   * unique integer representation that can be used for serialization and storage.
   */
  enum class DataType(val daggerElementType: DaggerElement.Type) {
    INJECTED_CONSTRUCTOR(DaggerElement.Type.PROVIDER),
    INJECTED_CONSTRUCTOR_PARAMETER(DaggerElement.Type.CONSUMER),
    PROVIDES_METHOD(DaggerElement.Type.PROVIDER),
    PROVIDES_METHOD_PARAMETER(DaggerElement.Type.CONSUMER),
    INJECTED_FIELD(DaggerElement.Type.CONSUMER),
    COMPONENT_WITH_MODULE(DaggerElement.Type.COMPONENT),
    COMPONENT_WITH_DEPENDENCY(DaggerElement.Type.COMPONENT),
    SUBCOMPONENT_WITH_MODULE(DaggerElement.Type.SUBCOMPONENT),
    MODULE_WITH_INCLUDE(DaggerElement.Type.MODULE),
    MODULE_WITH_SUBCOMPONENT(DaggerElement.Type.MODULE),
    COMPONENT_PROVISION_METHOD(DaggerElement.Type.CONSUMER),
  }

  abstract fun save(output: DataOutput)

  /**
   * Resolve the Dagger element represented by this [IndexValue] into one or more [DaggerElement]s.
   */
  fun resolveToDaggerElements(project: Project, scope: GlobalSearchScope): List<DaggerElement> {
    return getResolveCandidates(project, scope).mapNotNull {
      daggerElementIdentifiers.getDaggerElement(it.navigationElement)
    }
  }

  /**
   * Find any [PsiElement]s represented by this [IndexValue].
   *
   * The elements may or may not represent valid Dagger elements; it's not the responsibility of
   * this method to determine that. Rather, this method is just searching for elements based on
   * whatever data it has from the index. This method is used by [resolveToDaggerElements] to get a
   * candidate set of elements, and that method will then filter based upon which elements are valid
   * Dagger items.
   */
  protected abstract fun getResolveCandidates(
    project: Project,
    scope: GlobalSearchScope
  ): List<PsiElement>

  /**
   * Identifiers that search specifically for the types of [DaggerElement]s represented by this
   * [IndexValue]. The identifiers are responsible for validating that necessary conditions for
   * defining a [DaggerElement] are met.
   *
   * As an example, this method would be responsible for checking that a Component has the correct
   * `@Component` annotation, since the results returned from the index may have had a different
   * `@Component` annotation.
   */
  protected abstract val daggerElementIdentifiers: DaggerElementIdentifiers

  object Externalizer : DataExternalizer<Set<IndexValue>> {
    override fun save(output: DataOutput, value: Set<IndexValue>) {
      output.writeInt(value.size)
      value.forEach {
        output.writeByte(it.dataType.ordinal)
        it.save(output)
      }
    }

    @VisibleForTesting
    internal val readerMap = buildMap {
      AllConcepts.indexValueReaders.forEach { reader ->
        val supportedType = reader.supportedType
        assert(!containsKey(supportedType)) { "$supportedType cannot have two readers" }
        put(supportedType, reader)
      }
    }

    override fun read(input: DataInput): Set<IndexValue> {
      return hashSetOf<IndexValue>().apply {
        repeat(input.readInt()) {
          val dataType = IndexValue.DataType.values()[input.readByte().toInt()]

          val indexValue = readerMap[dataType]!!.read(input)
          assert(indexValue.dataType == dataType)
          add(indexValue)
        }
      }
    }
  }

  interface Reader {
    val supportedType: DataType
    fun read(input: DataInput): IndexValue
  }
}
