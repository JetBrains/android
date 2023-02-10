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

import com.android.tools.idea.dagger.concepts.DaggerAnnotations.MODULE
import com.android.tools.idea.dagger.concepts.DaggerAnnotations.PROVIDES
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
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

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
 * 1. The provides method (`HeaterModule.provideHeater`), which is a Dagger Provider.
 * 2. The method's parameters (`electricHeater`), which are Dagger Consumers.
 */
internal object ProvidesMethodDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(methodIndexers = listOf(ProvidesMethodIndexer))
  override val indexValueReaders =
    listOf(ProvidesMethodIndexValue.Reader, ProvidesMethodParameterIndexValue.Reader)
  override val daggerElementIdentifiers =
    DaggerElementIdentifiers.of(
      ProvidesMethodIndexValue.identifiers,
      ProvidesMethodParameterIndexValue.identifiers
    )
}

private object ProvidesMethodIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) {
    if (!wrapper.getIsAnnotatedWith(PROVIDES)) return

    val containingClass = wrapper.getContainingClass() ?: return
    if (!containingClass.getIsAnnotatedWith(MODULE)) return

    val classFqName = containingClass.getFqName()
    val methodSimpleName = wrapper.getSimpleName()
    val returnTypeSimpleName = wrapper.getReturnType()?.getSimpleName() ?: ""

    indexEntries.addIndexValue(
      returnTypeSimpleName,
      ProvidesMethodIndexValue(classFqName, methodSimpleName)
    )

    for (parameter in wrapper.getParameters()) {
      val parameterSimpleTypeName = parameter.getType().getSimpleName()
      val parameterName = parameter.getSimpleName()
      indexEntries.addIndexValue(
        parameterSimpleTypeName,
        ProvidesMethodParameterIndexValue(classFqName, methodSimpleName, parameterName)
      )
    }
  }
}

@VisibleForTesting
internal data class ProvidesMethodIndexValue(
  val classFqName: String,
  val methodSimpleName: String
) : IndexValue(DataType.PROVIDES_METHOD) {
  override fun save(output: DataOutput) {
    output.writeString(classFqName)
    output.writeString(methodSimpleName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.PROVIDES_METHOD
    override fun read(input: DataInput) =
      ProvidesMethodIndexValue(input.readString(), input.readString())
  }

  companion object {
    private val identifyProvidesMethodKotlin =
      DaggerElementIdentifier<KtFunction> { psiElement ->
        if (psiElement !is KtConstructor<*> &&
            psiElement.hasAnnotation(PROVIDES) &&
            psiElement.containingClassOrObject?.hasAnnotation(MODULE) == true
        ) {
          DaggerElement(psiElement, Type.PROVIDER)
        } else {
          null
        }
      }

    private val identifyProvidesMethodJava =
      DaggerElementIdentifier<PsiMethod> { psiElement ->
        if (!psiElement.isConstructor &&
            psiElement.hasAnnotation(PROVIDES) &&
            psiElement.containingClass?.hasAnnotation(MODULE) == true
        ) {
          DaggerElement(psiElement, Type.PROVIDER)
        } else {
          null
        }
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktFunctionIdentifiers = listOf(identifyProvidesMethodKotlin),
        psiMethodIdentifiers = listOf(identifyProvidesMethodJava)
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> {
    val psiClass =
      JavaPsiFacade.getInstance(project).findClass(classFqName, scope) ?: return emptyList()
    if (!psiClass.hasAnnotation(MODULE)) return emptyList()

    return psiClass.methods.filter { it.name == methodSimpleName }
  }

  override val daggerElementIdentifiers = identifiers
}

@VisibleForTesting
internal data class ProvidesMethodParameterIndexValue(
  val classFqName: String,
  val methodSimpleName: String,
  val parameterName: String
) : IndexValue(DataType.PROVIDES_METHOD_PARAMETER) {
  override fun save(output: DataOutput) {
    output.writeString(classFqName)
    output.writeString(methodSimpleName)
    output.writeString(parameterName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.PROVIDES_METHOD_PARAMETER
    override fun read(input: DataInput) =
      ProvidesMethodParameterIndexValue(input.readString(), input.readString(), input.readString())
  }

  companion object {
    private val identifyProvidesMethodParameterKotlin =
      DaggerElementIdentifier<KtParameter> { psiElement ->
        val parent = psiElement.parentOfType<KtFunction>() ?: return@DaggerElementIdentifier null
        if (parent !is KtConstructor<*> &&
            parent.hasAnnotation(PROVIDES) &&
            parent.containingClassOrObject?.hasAnnotation(MODULE) == true
        ) {
          DaggerElement(psiElement, Type.CONSUMER)
        } else {
          null
        }
      }

    private val identifyProvidesMethodParameterJava =
      DaggerElementIdentifier<PsiParameter> { psiElement ->
        val parent = psiElement.parentOfType<PsiMethod>() ?: return@DaggerElementIdentifier null
        if (!parent.isConstructor &&
            parent.hasAnnotation(PROVIDES) &&
            parent.containingClass?.hasAnnotation(MODULE) == true
        ) {
          DaggerElement(psiElement, Type.CONSUMER)
        } else {
          null
        }
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktParameterIdentifiers = listOf(identifyProvidesMethodParameterKotlin),
        psiParameterIdentifiers = listOf(identifyProvidesMethodParameterJava)
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> {
    val psiClass =
      JavaPsiFacade.getInstance(project).findClass(classFqName, scope) ?: return emptyList()
    if (!psiClass.hasAnnotation(MODULE)) return emptyList()

    return psiClass.methods.filter { it.name == methodSimpleName }.flatMap {
      it.parameterList.parameters.filter { p -> p.name == parameterName }
    }
  }

  override val daggerElementIdentifiers = identifiers
}
