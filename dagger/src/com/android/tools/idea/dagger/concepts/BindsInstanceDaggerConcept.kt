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
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexClassWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.index.psiwrappers.hasAnnotation
import com.android.tools.idea.dagger.index.readClassId
import com.android.tools.idea.dagger.index.writeClassId
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.parentOfType
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.io.DataInput
import java.io.DataOutput

/**
 * Represents a method or parameter marked with @BindsInstance in Dagger.
 *
 * Example:
 * ```java
 *    @Component.Builder
 *    interface Builder {
 *      @BindsInstance Builder foo(Foo foo);
 *      @BindsInstance Builder bar(@Blue Bar bar);
 *      ...
 *    }
 * ```
 *
 * Methods on a @Component.Builder and parameters in a method on a @Component.Factory may be marked
 * with this attribute.
 *
 * See https://dagger.dev/api/latest/dagger/BindsInstance.html for full details.
 */
internal object BindsInstanceDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(methodIndexers = listOf(BindsInstanceIndexer))
  override val indexValueReaders =
    listOf(
      BindsInstanceBuilderMethodIndexValue.Reader,
      BindsInstanceFactoryMethodParameterIndexValue.Reader,
    )
  override val daggerElementIdentifiers =
    DaggerElementIdentifiers.of(
      BindsInstanceBuilderMethodIndexValue.identifiers,
      BindsInstanceFactoryMethodParameterIndexValue.identifiers,
    )
}

private object BindsInstanceIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) {
    val containingClass = wrapper.getContainingClass() ?: return

    when {
      containingClass.getIsAnnotatedWith(DaggerAnnotation.COMPONENT_BUILDER) ->
        addIndexEntriesForBuilder(wrapper, containingClass, indexEntries)
      containingClass.getIsAnnotatedWith(DaggerAnnotation.COMPONENT_FACTORY) ->
        addIndexEntriesForFactory(wrapper, containingClass, indexEntries)
    }
  }

  private fun addIndexEntriesForBuilder(
    wrapper: DaggerIndexMethodWrapper,
    containingClass: DaggerIndexClassWrapper,
    indexEntries: IndexEntries,
  ) {
    if (!wrapper.getIsAnnotatedWith(DaggerAnnotation.BINDS_INSTANCE)) return

    val singleParameter = wrapper.getParameters().singleOrNull() ?: return

    indexEntries.addIndexValue(
      singleParameter.getType()?.getSimpleName() ?: "",
      BindsInstanceBuilderMethodIndexValue(containingClass.getClassId(), wrapper.getSimpleName()),
    )
  }

  private fun addIndexEntriesForFactory(
    wrapper: DaggerIndexMethodWrapper,
    containingClass: DaggerIndexClassWrapper,
    indexEntries: IndexEntries,
  ) {
    val bindsInstanceParameters =
      wrapper.getParameters().filter { it.getIsAnnotatedWith(DaggerAnnotation.BINDS_INSTANCE) }
    for (parameter in bindsInstanceParameters) {
      val parameterTypeSimpleName = parameter.getType()?.getSimpleName() ?: continue
      val parameterSimpleName = parameter.getSimpleName() ?: continue
      indexEntries.addIndexValue(
        parameterTypeSimpleName,
        BindsInstanceFactoryMethodParameterIndexValue(
          containingClass.getClassId(),
          wrapper.getSimpleName(),
          parameterSimpleName,
        ),
      )
    }
  }
}

@VisibleForTesting
internal data class BindsInstanceBuilderMethodIndexValue(
  val classId: ClassId,
  val methodSimpleName: String,
) : IndexValue() {

  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(methodSimpleName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.BINDS_INSTANCE_BUILDER_METHOD

    override fun read(input: DataInput) =
      BindsInstanceBuilderMethodIndexValue(input.readClassId(), input.readString())
  }

  companion object {
    private fun identify(psiElement: KtFunction): DaggerElement? {
      return if (
        psiElement.hasAnnotation(DaggerAnnotation.BINDS_INSTANCE) &&
          psiElement.containingClassOrObject?.hasAnnotation(DaggerAnnotation.COMPONENT_BUILDER) ==
            true &&
          psiElement.valueParameters.size == 1
      ) {
        // We store the method in the index, but the single parameter is really the provider here.
        ProviderDaggerElement(psiElement.valueParameters[0])
      } else {
        null
      }
    }

    private fun identify(psiElement: PsiMethod): DaggerElement? {
      return if (
        psiElement.hasAnnotation(DaggerAnnotation.BINDS_INSTANCE) &&
          psiElement.containingClass?.hasAnnotation(DaggerAnnotation.COMPONENT_BUILDER) == true &&
          psiElement.parameterList.parameters.size == 1
      ) {
        // We store the method in the index, but the single parameter is really the provider here.
        ProviderDaggerElement(psiElement.parameterList.parameters[0])
      } else {
        null
      }
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
internal data class BindsInstanceFactoryMethodParameterIndexValue(
  val classId: ClassId,
  val methodSimpleName: String,
  val parameterSimpleName: String,
) : IndexValue() {

  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(methodSimpleName)
    output.writeString(parameterSimpleName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.BINDS_INSTANCE_FACTORY_METHOD_PARAMETER

    override fun read(input: DataInput) =
      BindsInstanceFactoryMethodParameterIndexValue(
        input.readClassId(),
        input.readString(),
        input.readString(),
      )
  }

  companion object {
    private fun identify(psiElement: KtParameter): DaggerElement? {
      if (!psiElement.hasAnnotation(DaggerAnnotation.BINDS_INSTANCE)) return null

      val containingClass = psiElement.parentOfType<KtClassOrObject>()
      return if (containingClass?.hasAnnotation(DaggerAnnotation.COMPONENT_FACTORY) == true) {
        return ProviderDaggerElement(psiElement)
      } else {
        null
      }
    }

    private fun identify(psiElement: PsiParameter): DaggerElement? {
      if (!psiElement.hasAnnotation(DaggerAnnotation.BINDS_INSTANCE)) return null

      val containingClass = psiElement.parentOfType<PsiClass>()
      return if (containingClass?.hasAnnotation(DaggerAnnotation.COMPONENT_FACTORY) == true) {
        return ProviderDaggerElement(psiElement)
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
      ?.flatMap {
        it.parameterList.parameters.asSequence().filter { p -> p.name == parameterSimpleName }
      } ?: emptySequence()

  override val daggerElementIdentifiers = identifiers
}
