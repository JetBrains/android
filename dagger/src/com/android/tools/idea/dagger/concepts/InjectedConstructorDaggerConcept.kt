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
package com.android.tools.idea.dagger.concepts

import com.android.tools.idea.dagger.concepts.DaggerAnnotations.INJECT
import com.android.tools.idea.dagger.concepts.DaggerElement.Type
import com.android.tools.idea.dagger.index.DaggerConceptIndexer
import com.android.tools.idea.dagger.index.DaggerConceptIndexers
import com.android.tools.idea.dagger.index.IndexEntries
import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.kotlin.hasAnnotation
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import java.io.DataInput
import java.io.DataOutput
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtParameter

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
 * 1. The injected constructor (the `Thermosiphon` constructor), which is a Dagger Provider.
 * 2. The constructor's parameters (`heater`), which are Dagger Consumers.
 */
internal object InjectedConstructorDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(methodIndexers = listOf(InjectedConstructorIndexer))
  override val indexValueReaders =
    listOf(InjectedConstructorIndexValue.Reader, InjectedConstructorParameterIndexValue.Reader)
  override val daggerElementIdentifiers =
    DaggerElementIdentifiers.of(
      InjectedConstructorIndexValue.identifiers,
      InjectedConstructorParameterIndexValue.identifiers
    )
}

private object InjectedConstructorIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) {
    if (!wrapper.getIsConstructor() || !wrapper.getIsAnnotatedWith(INJECT)) return

    val classFqName = wrapper.getContainingClass()?.getFqName() ?: return
    indexEntries.addIndexValue(classFqName, InjectedConstructorIndexValue(classFqName))

    for (parameter in wrapper.getParameters()) {
      val parameterSimpleTypeName = parameter.getType().getSimpleName()
      val parameterName = parameter.getSimpleName()
      indexEntries.addIndexValue(
        parameterSimpleTypeName,
        InjectedConstructorParameterIndexValue(classFqName, parameterName)
      )
    }
  }
}

@VisibleForTesting
internal data class InjectedConstructorIndexValue(val classFqName: String) :
  IndexValue(DataType.INJECTED_CONSTRUCTOR) {
  override fun save(output: DataOutput) {
    output.writeString(classFqName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.INJECTED_CONSTRUCTOR
    override fun read(input: DataInput) = InjectedConstructorIndexValue(input.readString())
  }

  companion object {
    private val identifyInjectedConstructorKotlin =
      DaggerElementIdentifier<KtConstructor<*>> {
        if (it.hasAnnotation(INJECT)) DaggerElement(it, Type.PROVIDER) else null
      }

    private val identifyInjectedConstructorJava =
      DaggerElementIdentifier<PsiMethod> {
        if (it.isConstructor && it.hasAnnotation(INJECT)) DaggerElement(it, Type.PROVIDER) else null
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktConstructorIdentifiers = listOf(identifyInjectedConstructorKotlin),
        psiMethodIdentifiers = listOf(identifyInjectedConstructorJava)
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> =
    JavaPsiFacade.getInstance(project).findClass(classFqName, scope)?.constructors?.toList()
      ?: emptyList()

  override val daggerElementIdentifiers = identifiers
}

@VisibleForTesting
internal data class InjectedConstructorParameterIndexValue(
  val classFqName: String,
  val parameterName: String
) : IndexValue(DataType.INJECTED_CONSTRUCTOR_PARAMETER) {
  override fun save(output: DataOutput) {
    output.writeString(classFqName)
    output.writeString(parameterName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.INJECTED_CONSTRUCTOR_PARAMETER
    override fun read(input: DataInput) =
      InjectedConstructorParameterIndexValue(input.readString(), input.readString())
  }

  companion object {
    private val identifyInjectedConstructorParameterKotlin =
      DaggerElementIdentifier<KtParameter> { psiElement ->
        val parent =
          psiElement.parentOfType<KtConstructor<*>>() ?: return@DaggerElementIdentifier null
        if (parent.hasAnnotation(INJECT)) DaggerElement(psiElement, Type.CONSUMER) else null
      }

    private val identifyInjectedConstructorParameterJava =
      DaggerElementIdentifier<PsiParameter> { psiElement ->
        val parent = psiElement.parentOfType<PsiMethod>() ?: return@DaggerElementIdentifier null
        if (parent.isConstructor && parent.hasAnnotation(INJECT))
          DaggerElement(psiElement, Type.CONSUMER)
        else null
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktParameterIdentifiers = listOf(identifyInjectedConstructorParameterKotlin),
        psiParameterIdentifiers = listOf(identifyInjectedConstructorParameterJava)
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> {
    val psiClass =
      JavaPsiFacade.getInstance(project).findClass(classFqName, scope) ?: return emptyList()
    return psiClass.constructors.flatMap {
      it.parameterList.parameters.filter { p -> p.name == parameterName }
    }
  }

  override val daggerElementIdentifiers = identifiers
}
