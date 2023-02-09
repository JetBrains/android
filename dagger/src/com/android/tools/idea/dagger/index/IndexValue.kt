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
import com.android.tools.idea.dagger.concepts.getPsiType
import com.android.tools.idea.dagger.unboxed
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput
import org.jetbrains.annotations.VisibleForTesting

/**
 * An index value for the Dagger index. Each [DaggerConcept] is responsible for defining the exact
 * data that it needs to store for its entries.
 */
abstract class IndexValue(val dataType: DataType) {
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
  }

  abstract fun save(output: DataOutput)

  /**
   * Resolve the Dagger element represented by this [IndexValue] into one or more [DaggerElement]s.
   *
   * @param expectedPsiType the expected type of the Dagger element being resolved.
   * @return a list of valid [DaggerElement]s, or the empty list when no elements can be found that
   * match the given type.
   */
  fun resolveToDaggerElements(
    expectedPsiType: PsiType,
    project: Project,
    scope: GlobalSearchScope
  ): List<DaggerElement> {
    val unboxedExpectedType = expectedPsiType.unboxed
    return getResolveCandidates(project, scope)
      .filter { it.getPsiType().unboxed == unboxedExpectedType }
      .mapNotNull { daggerElementIdentifiers.getDaggerElement(it.navigationElement) }
  }

  /**
   * Find any [PsiElement]s represented by this [IndexValue] that match the given [PsiType].
   *
   * The elements may or may not represent valid Dagger elements; it's not the responsibility of
   * this method to determine that. Rather, this method is just searching for elements based on
   * whatever data it has from the index. This method is used by [resolveToDaggerElements] to get a
   * candidate set of elements, and that method will then filter based upon which elements are valid
   * Dagger items.
   *
   * However, this method may choose to do further filtering of candidates if the information is
   * quick and readily available. An example is checking for an attribute on a [PsiClass]; that
   * information is quickly available on the stub returned by [JavaPsiFacade], and so it's okay for
   * this method to check for a required annotation on a class and filter out candidates that don't
   * have it.
   *
   * On the flip side, checking for an annotation on a [PsiMethod] method coming from Kotlin
   * actually *can't* be done quickly, since it requires resolving to the navigation element first.
   * Therefore, that type of check should not be done here, in order to keep this method as
   * lightweight as possible.
   */
  protected abstract fun getResolveCandidates(
    project: Project,
    scope: GlobalSearchScope
  ): List<PsiElement>

  /**
   * Identifiers that search specifically for the types of [DaggerElement]s represented by this
   * [IndexValue].
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
