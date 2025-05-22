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
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiParameter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtAnnotated
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.io.DataInput
import java.io.DataOutput

/**
 * Represents a @Provides or @Binds method in Dagger.
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
 * 1. The provides method (`HeaterModule.provideHeater`), which is a Dagger Provider.
 * 2. The method's parameters (`electricHeater`), which are Dagger Consumers.
 *
 * Methods with @Binds are syntactically very similar to @Provides. For the purpose of Dagger
 * navigation, we treat them equivalently.
 */
internal object ProvidesMethodDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(methodIndexers = listOf(ProvidesMethodIndexer))
  override val indexValueReaders =
    listOf(ProvidesMethodIndexValue.Reader, ProvidesMethodParameterIndexValue.Reader)
  override val daggerElementIdentifiers =
    DaggerElementIdentifiers.of(
      ProvidesMethodIndexValue.identifiers,
      ProvidesMethodParameterIndexValue.identifiers,
    )
}

private object ProvidesMethodIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) {
    if (!wrapper.getIsAnnotatedWithAnyOf(DaggerAnnotation.PROVIDES, DaggerAnnotation.BINDS)) return

    val containingClass = wrapper.getContainingClass() ?: return
    if (!containingClass.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.MODULE)) return

    val classId = containingClass.getClassId()
    val methodSimpleName = wrapper.getSimpleName()
    val returnTypeSimpleName = wrapper.getReturnType()?.getSimpleName() ?: ""

    indexEntries.addIndexValue(
      returnTypeSimpleName,
      ProvidesMethodIndexValue(classId, methodSimpleName),
    )

    for (parameter in wrapper.getParameters()) {
      val parameterSimpleTypeName = parameter.getType()?.getSimpleName() ?: continue
      val parameterName = parameter.getSimpleName() ?: continue
      indexEntries.addIndexValue(
        parameterSimpleTypeName,
        ProvidesMethodParameterIndexValue(classId, methodSimpleName, parameterName),
      )
    }
  }
}

@VisibleForTesting
internal data class ProvidesMethodIndexValue(val classId: ClassId, val methodSimpleName: String) :
  IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(methodSimpleName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.PROVIDES_METHOD

    override fun read(input: DataInput) =
      ProvidesMethodIndexValue(input.readClassId(), input.readString())
  }

  companion object {
    private fun identify(psiElement: KtFunction): DaggerElement? =
      if (
        psiElement !is KtConstructor<*> &&
          psiElement.hasProvidesOrBindsAnnotation() &&
          psiElement.containingClassOrObject?.selfOrCompanionParentIsModule() == true
      ) {
        ProviderDaggerElement(psiElement)
      } else {
        null
      }

    private fun identify(psiElement: PsiMethod): DaggerElement? =
      if (
        !psiElement.isConstructor &&
          psiElement.hasProvidesOrBindsAnnotation() &&
          psiElement.containingClass?.hasAnnotation(DaggerAnnotation.MODULE) == true
      ) {
        ProviderDaggerElement(psiElement)
      } else {
        null
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktFunctionIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
        psiMethodIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope) =
    JavaPsiFacade.getInstance(project)
      .findClass(classId.asFqNameString(), scope)
      ?.methods
      ?.asSequence()
      ?.filter { it.name == methodSimpleName } ?: emptySequence()

  override val daggerElementIdentifiers = identifiers
}

@VisibleForTesting
internal data class ProvidesMethodParameterIndexValue(
  val classId: ClassId,
  val methodSimpleName: String,
  val parameterName: String,
) : IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(methodSimpleName)
    output.writeString(parameterName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.PROVIDES_METHOD_PARAMETER

    override fun read(input: DataInput) =
      ProvidesMethodParameterIndexValue(input.readClassId(), input.readString(), input.readString())
  }

  companion object {
    private fun identify(psiElement: KtParameter): DaggerElement? {
      val parent = psiElement.parentOfType<KtFunction>() ?: return null
      return if (
        parent !is KtConstructor<*> &&
          parent.hasProvidesOrBindsAnnotation() &&
          parent.containingClassOrObject?.selfOrCompanionParentIsModule() == true
      ) {
        ConsumerDaggerElement(psiElement)
      } else {
        null
      }
    }

    private fun identify(psiElement: PsiParameter): DaggerElement? {
      val parent = psiElement.parentOfType<PsiMethod>() ?: return null
      return if (
        !parent.isConstructor &&
          parent.hasProvidesOrBindsAnnotation() &&
          parent.containingClass?.hasAnnotation(DaggerAnnotation.MODULE) == true
      ) {
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
      ?.methods
      ?.asSequence()
      ?.filter { it.name == methodSimpleName }
      ?.flatMap { it.parameterList.parameters.asSequence().filter { p -> p.name == parameterName } }
      ?: emptySequence()

  override val daggerElementIdentifiers = identifiers
}

private fun KtClassOrObject.selfOrCompanionParentIsModule() =
  hasAnnotation(DaggerAnnotation.MODULE) ||
    (this is KtObjectDeclaration &&
      isCompanion() &&
      containingClassOrObject?.hasAnnotation(DaggerAnnotation.MODULE) == true)

private fun KtAnnotated.hasProvidesOrBindsAnnotation() =
  hasAnnotation(DaggerAnnotation.PROVIDES) || hasAnnotation(DaggerAnnotation.BINDS)

private fun PsiModifierListOwner.hasProvidesOrBindsAnnotation() =
  hasAnnotation(DaggerAnnotation.PROVIDES) || hasAnnotation(DaggerAnnotation.BINDS)
