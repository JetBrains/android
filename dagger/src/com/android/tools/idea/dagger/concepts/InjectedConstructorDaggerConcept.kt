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

import com.android.tools.idea.dagger.index.DaggerConceptIndexer
import com.android.tools.idea.dagger.index.DaggerConceptIndexers
import com.android.tools.idea.dagger.index.IndexEntries
import com.android.tools.idea.dagger.index.IndexValue
import com.android.tools.idea.dagger.index.psiwrappers.DaggerAnnotation
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.index.psiwrappers.hasAnnotation
import com.android.tools.idea.dagger.index.readClassId
import com.android.tools.idea.dagger.index.writeClassId
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtParameter
import java.io.DataInput
import java.io.DataOutput

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
      InjectedConstructorParameterIndexValue.identifiers,
    )
}

private object InjectedConstructorIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) {
    if (!wrapper.getIsConstructor() || !wrapper.getIsAnnotatedWith(DaggerAnnotation.INJECT)) return

    val classId = wrapper.getContainingClass()?.getClassId() ?: return
    indexEntries.addIndexValue(classId.asFqNameString(), InjectedConstructorIndexValue(classId))

    for (parameter in wrapper.getParameters()) {
      val parameterSimpleTypeName = parameter.getType()?.getSimpleName() ?: continue
      val parameterName = parameter.getSimpleName() ?: continue
      indexEntries.addIndexValue(
        parameterSimpleTypeName,
        InjectedConstructorParameterIndexValue(classId, parameterName),
      )
    }
  }
}

@VisibleForTesting
internal data class InjectedConstructorIndexValue(val classId: ClassId) : IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.INJECTED_CONSTRUCTOR

    override fun read(input: DataInput) = InjectedConstructorIndexValue(input.readClassId())
  }

  companion object {
    private fun identify(psiElement: KtConstructor<*>): DaggerElement? =
      if (psiElement.hasAnnotation(DaggerAnnotation.INJECT)) {
        ProviderDaggerElement(psiElement)
      } else {
        null
      }

    private fun identify(psiElement: PsiMethod): DaggerElement? =
      if (psiElement.isConstructor && psiElement.hasAnnotation(DaggerAnnotation.INJECT)) {
        ProviderDaggerElement(psiElement)
      } else {
        null
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktConstructorIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
        psiMethodIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope) =
    JavaPsiFacade.getInstance(project)
      .findClass(classId.asFqNameString(), scope)
      ?.constructors
      ?.asSequence() ?: emptySequence()

  override val daggerElementIdentifiers = identifiers
}

@VisibleForTesting
internal data class InjectedConstructorParameterIndexValue(
  val classId: ClassId,
  val parameterName: String,
) : IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(parameterName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.INJECTED_CONSTRUCTOR_PARAMETER

    override fun read(input: DataInput) =
      InjectedConstructorParameterIndexValue(input.readClassId(), input.readString())
  }

  companion object {
    private fun identify(psiElement: KtParameter): DaggerElement? =
      if (
        psiElement.parentOfType<KtConstructor<*>>()?.hasAnnotation(DaggerAnnotation.INJECT) == true
      ) {
        ConsumerDaggerElement(psiElement)
      } else {
        null
      }

    private fun identify(psiElement: PsiParameter): DaggerElement? {
      val parent = psiElement.parentOfType<PsiMethod>() ?: return null
      return if (parent.isConstructor && parent.hasAnnotation(DaggerAnnotation.INJECT)) {
        ConsumerDaggerElement(psiElement)
      } else {
        null
      }
    }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktParameterIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
        psiParameterIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope) =
    JavaPsiFacade.getInstance(project)
      .findClass(classId.asFqNameString(), scope)
      ?.constructors
      ?.asSequence()
      ?.flatMap { it.parameterList.parameters.asSequence().filter { p -> p.name == parameterName } }
      ?: emptySequence()

  override val daggerElementIdentifiers = identifiers
}
