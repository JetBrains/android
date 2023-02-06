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
import com.android.tools.idea.dagger.index.concepts.DaggerElement.Type
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexFieldWrapper
import com.android.tools.idea.kotlin.hasAnnotation
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.io.DataInput
import java.io.DataOutput

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
internal object InjectedFieldDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(fieldIndexers = listOf(InjectedFieldIndexer))
  override val indexValueReaders: List<IndexValue.Reader> = listOf(InjectedFieldIndexValue.Reader)
  override val daggerElementIdentifiers = InjectedFieldIndexValue.identifiers
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

  companion object {
    private val identifyInjectedFieldKotlin = DaggerElementIdentifier<KtProperty> {
      if (it.containingClassOrObject != null && it.hasAnnotation(INJECT)) DaggerElement(it, Type.CONSUMER) else null
    }

    private val identifyInjectedFieldJava = DaggerElementIdentifier<PsiField> {
      if (it.hasAnnotation(INJECT)) DaggerElement(it, Type.CONSUMER) else null
    }

    internal val identifiers = DaggerElementIdentifiers(
      ktPropertyIdentifiers = listOf(identifyInjectedFieldKotlin),
      psiFieldIdentifiers = listOf(identifyInjectedFieldJava))
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> {
    val psiClass = JavaPsiFacade.getInstance(project).findClass(classFqName, scope) ?: return emptyList()
    return psiClass.fields.filter { it.name == fieldName }
  }

  override val daggerElementIdentifiers = identifiers
}
