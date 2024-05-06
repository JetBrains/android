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
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.analysis.utils.printer.parentOfType
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtParameter
import java.io.DataInput
import java.io.DataOutput

/**
 * Represents a type created via assisted injection.
 *
 * Example:
 * ```java
 *    final class DataService {
 *      private final DataFetcher dataFetcher;
 *      private final Config config;
 *
 *      @AssistedInject
 *      DataService(DataFetcher dataFetcher, @Assisted Config config) {
 *        this.dataFetcher = dataFetcher;
 *        this.config = config;
 *      }
 *    }
 * ```
 *
 * This concept creates two types of index entries:
 * 1. The assisted inject constructor.
 * 2. The parameters of the assisted inject constructor.
 *
 * See also: [AssistedInject](https://dagger.dev/api/latest/dagger/assisted/AssistedInject.html)
 */
object AssistedInjectConstructorDaggerConcept : DaggerConcept {
  override val indexers =
    DaggerConceptIndexers(methodIndexers = listOf(AssistedInjectConstructorIndexer))
  override val indexValueReaders =
    listOf(
      AssistedInjectConstructorIndexValue.Reader,
      AssistedInjectConstructorUnassistedParameterIndexValue.Reader
    )
  override val daggerElementIdentifiers =
    DaggerElementIdentifiers.of(
      AssistedInjectConstructorIndexValue.identifiers,
      AssistedInjectConstructorUnassistedParameterIndexValue.identifiers
    )
}

private object AssistedInjectConstructorIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) {
    if (
      !wrapper.getIsConstructor() || !wrapper.getIsAnnotatedWith(DaggerAnnotation.ASSISTED_INJECT)
    )
      return

    val classId = wrapper.getContainingClass()?.getClassId() ?: return
    indexEntries.addIndexValue(
      classId.asFqNameString(),
      AssistedInjectConstructorIndexValue(classId)
    )

    for (parameter in wrapper.getParameters()) {
      // We only need to handle unassisted parameters for navigation. But those are identified by
      // the absence of an annotation, and the best we can do at indexing it to say that an
      // annotation *might* be present. Therefore we index all of the parameters, and ensure during
      // resolution at analysis time that we drop any which are assisted.
      val parameterSimpleTypeName = parameter.getType()?.getSimpleName() ?: continue
      val parameterName = parameter.getSimpleName() ?: continue
      indexEntries.addIndexValue(
        parameterSimpleTypeName,
        AssistedInjectConstructorUnassistedParameterIndexValue(classId, parameterName)
      )
    }
  }
}

@VisibleForTesting
internal data class AssistedInjectConstructorIndexValue(val classId: ClassId) : IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.ASSISTED_INJECT_CONSTRUCTOR

    override fun read(input: DataInput) = AssistedInjectConstructorIndexValue(input.readClassId())
  }

  companion object {
    private fun identify(psiElement: KtConstructor<*>): DaggerElement? =
      if (psiElement.hasAnnotation(DaggerAnnotation.ASSISTED_INJECT))
        AssistedInjectConstructorDaggerElement(psiElement)
      else null

    private fun identify(psiElement: PsiMethod): DaggerElement? =
      if (psiElement.isConstructor && psiElement.hasAnnotation(DaggerAnnotation.ASSISTED_INJECT))
        AssistedInjectConstructorDaggerElement(psiElement)
      else null

    internal val identifiers =
      DaggerElementIdentifiers(
        ktConstructorIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
        psiMethodIdentifiers = listOf(DaggerElementIdentifier(this::identify))
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> =
    JavaPsiFacade.getInstance(project)
      .findClass(classId.asFqNameString(), scope)
      ?.constructors
      ?.toList() ?: emptyList()

  override val daggerElementIdentifiers = identifiers
}

@VisibleForTesting
internal data class AssistedInjectConstructorUnassistedParameterIndexValue(
  val classId: ClassId,
  val parameterName: String
) : IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(parameterName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.ASSISTED_INJECT_CONSTRUCTOR_UNASSISTED_PARAMETER

    override fun read(input: DataInput) =
      AssistedInjectConstructorUnassistedParameterIndexValue(
        input.readClassId(),
        input.readString()
      )
  }

  companion object {
    private fun identify(psiElement: KtParameter): DaggerElement? =
      if (
        !psiElement.hasAnnotation(DaggerAnnotation.ASSISTED) &&
          psiElement
            .parentOfType<KtConstructor<*>>()
            ?.hasAnnotation(DaggerAnnotation.ASSISTED_INJECT) == true
      ) {
        ConsumerDaggerElement(psiElement)
      } else {
        null
      }

    private fun identify(psiElement: PsiParameter): DaggerElement? {
      if (psiElement.hasAnnotation(DaggerAnnotation.ASSISTED)) return null

      val parent = psiElement.parentOfType<PsiMethod>() ?: return null
      return if (parent.isConstructor && parent.hasAnnotation(DaggerAnnotation.ASSISTED_INJECT)) {
        ConsumerDaggerElement(psiElement)
      } else {
        null
      }
    }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktParameterIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
        psiParameterIdentifiers = listOf(DaggerElementIdentifier(this::identify))
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> {
    val psiClass =
      JavaPsiFacade.getInstance(project).findClass(classId.asFqNameString(), scope)
        ?: return emptyList()
    return psiClass.constructors.flatMap {
      it.parameterList.parameters.filter { p -> p.name == parameterName }
    }
  }

  override val daggerElementIdentifiers = identifiers
}

internal data class AssistedInjectConstructorDaggerElement(
  override val psiElement: PsiElement,
  internal val constructedType: PsiType,
  internal val methodName: String?
) : DaggerElement() {

  internal constructor(
    psiElement: KtConstructor<*>
  ) : this(psiElement, psiElement.getReturnedPsiType(), psiElement.name)

  internal constructor(
    psiElement: PsiMethod
  ) : this(psiElement, psiElement.getReturnedPsiType(), psiElement.name)

  override val metricsElementType = DaggerEditorEvent.ElementType.ASSISTED_INJECTED_CONSTRUCTOR

  override fun getRelatedDaggerElements(): List<DaggerRelatedElement> {
    return getRelatedDaggerElementsFromIndex<AssistedFactoryMethodDaggerElement>(
        constructedType.getIndexKeys()
      )
      .map {
        DaggerRelatedElement(
          it,
          DaggerBundle.message("assisted.factory"),
          "navigate.to.assisted.factory",
          it.methodName
        )
      }
  }

  override fun filterResolveCandidate(resolveCandidate: DaggerElement) =
    resolveCandidate is AssistedFactoryMethodDaggerElement &&
      resolveCandidate.returnedType == this.constructedType
}
