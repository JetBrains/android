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
import com.android.tools.idea.dagger.index.getAliasSimpleNames
import com.android.tools.idea.dagger.index.psiwrappers.DaggerAnnotation
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.index.psiwrappers.hasAnnotation
import com.android.tools.idea.dagger.index.readClassId
import com.android.tools.idea.dagger.index.writeClassId
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.base.util.projectScope
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.io.DataInput
import java.io.DataOutput

/**
 * Represents a @BindsOptionalOf method in Dagger.
 *
 * Example:
 * ```java
 *   @Module
 *   interface HeaterModule {
 *     @BindsOptionalOf
 *     Heater bindHeater();
 *   }
 * ```
 *
 * The above method indicates that there may or may not be a binding for `Heater` present, and
 * consumers may bind to an `Optional<Heater>`.
 *
 * Dagger supports using either `com.google.common.base.Optional` or `java.util.Optional`.
 *
 * See https://dagger.dev/api/latest/dagger/BindsOptionalOf.html for full details.
 */
internal object BindsOptionalOfDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(methodIndexers = listOf(BindsOptionalOfIndexer))
  override val indexValueReaders = listOf(BindsOptionalOfIndexValue.Reader)
  override val daggerElementIdentifiers = BindsOptionalOfIndexValue.identifiers
}

private object BindsOptionalOfIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) {
    if (
      !wrapper.getIsAnnotatedWith(DaggerAnnotation.BINDS_OPTIONAL_OF) ||
        wrapper.getParameters().isNotEmpty()
    )
      return

    val containingClass = wrapper.getContainingClass() ?: return
    if (!containingClass.getIsSelfOrCompanionParentAnnotatedWith(DaggerAnnotation.MODULE)) return

    val classId = containingClass.getClassId()
    val methodSimpleName = wrapper.getSimpleName()

    indexEntries.addIndexValue("Optional", BindsOptionalOfIndexValue(classId, methodSimpleName))
  }
}

@VisibleForTesting
internal data class BindsOptionalOfIndexValue(val classId: ClassId, val methodSimpleName: String) :
  IndexValue() {

  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(methodSimpleName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.BINDS_OPTIONAL_OF_METHOD

    override fun read(input: DataInput) =
      BindsOptionalOfIndexValue(input.readClassId(), input.readString())
  }

  companion object {
    private fun identify(psiElement: KtFunction): DaggerElement? =
      if (
        psiElement !is KtConstructor<*> &&
          psiElement.hasAnnotation(DaggerAnnotation.BINDS_OPTIONAL_OF) &&
          !psiElement.hasBody() &&
          psiElement.valueParameters.isEmpty() &&
          psiElement.containingClassOrObject?.selfOrCompanionParentIsModule() == true
      ) {
        BindsOptionalOfProviderDaggerElement(psiElement)
      } else {
        null
      }

    private fun identify(psiElement: PsiMethod): DaggerElement? =
      if (
        !psiElement.isConstructor &&
          psiElement.hasAnnotation(DaggerAnnotation.BINDS_OPTIONAL_OF) &&
          psiElement.body == null &&
          psiElement.parameters.isEmpty() &&
          psiElement.containingClass?.hasAnnotation(DaggerAnnotation.MODULE) == true
      ) {
        BindsOptionalOfProviderDaggerElement(psiElement)
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

internal data class BindsOptionalOfProviderDaggerElement(
  override val psiElement: PsiElement,
  private val providedPsiType: PsiType,
) : ProviderDaggerElementBase() {

  constructor(psiElement: KtFunction) : this(psiElement, psiElement.getReturnedPsiType())

  constructor(psiElement: PsiMethod) : this(psiElement, psiElement.getReturnedPsiType())

  override val metricsElementType = DaggerEditorEvent.ElementType.PROVIDER

  override fun getIndexKeys(): List<String> {
    val project = psiElement.project
    val scope = project.projectScope()
    return listOf(optionalSimpleName) + getAliasSimpleNames(optionalSimpleName, project, scope)
  }

  override fun canProvideFor(consumer: ConsumerDaggerElementBase): Boolean {
    val innerType = consumer.consumedType.typeInsideOptionalWrapper() ?: return false
    return innerType.matchesProvidedType(providedPsiType) && qualifierInfo == consumer.qualifierInfo
  }

  companion object {
    private const val optionalSimpleName = "Optional"
  }
}

private fun KtClassOrObject.selfOrCompanionParentIsModule() =
  hasAnnotation(DaggerAnnotation.MODULE) ||
    (this is KtObjectDeclaration &&
      isCompanion() &&
      containingClassOrObject?.hasAnnotation(DaggerAnnotation.MODULE) == true)
