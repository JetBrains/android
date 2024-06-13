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
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexFieldWrapper
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.index.psiwrappers.KtPropertyWrapper
import com.android.tools.idea.dagger.index.psiwrappers.hasAnnotation
import com.android.tools.idea.dagger.index.readClassId
import com.android.tools.idea.dagger.index.writeClassId
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.android.tools.idea.kotlin.psiType
import com.google.wireless.android.sdk.stats.DaggerEditorEvent
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import org.jetbrains.kotlin.psi.psiUtil.findPropertyByName
import java.io.DataInput
import java.io.DataOutput

/**
 * Represents a Component's
 * [provision method](https://dagger.dev/api/latest/dagger/Component.html#provision-methods).
 *
 * Example:
 * ```java
 *   @Component
 *   interface ApplicationComponent {
 *     Foo getFoo();
 *   }
 * ```
 *
 * The `getFoo` method is a Component provision method, and is considered a Consumer of the type
 * `Foo`.
 *
 * Kotlin properties can also be used as provision methods (in the JVM, they are methods as well):
 * ```kotlin
 *   @Component
 *   interface ApplicationComponent {
 *     val foo: Foo
 *   }
 * ```
 */
internal object ComponentProvisionMethodConcept : DaggerConcept {
  override val indexers =
    DaggerConceptIndexers(
      methodIndexers = listOf(ComponentProvisionMethodIndexer),
      fieldIndexers = listOf(ComponentProvisionPropertyIndexer),
    )
  override val indexValueReaders: List<IndexValue.Reader> =
    listOf(ComponentProvisionMethodIndexValue.Reader, ComponentProvisionPropertyIndexValue.Reader)
  override val daggerElementIdentifiers =
    DaggerElementIdentifiers.of(
      ComponentProvisionMethodIndexValue.identifiers,
      ComponentProvisionPropertyIndexValue.identifiers,
    )
}

private object ComponentProvisionMethodIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) {
    // A provision method must specify a type and have no parameters.
    if (wrapper.getParameters().isNotEmpty()) return
    val returnType = wrapper.getReturnType() ?: return

    // A provision method must be on a @Component or @Subcomponent.
    val containingClass = wrapper.getContainingClass() ?: return
    if (
      containingClass.getIsAnnotatedWithAnyOf(
        DaggerAnnotation.COMPONENT,
        DaggerAnnotation.SUBCOMPONENT,
      )
    ) {
      indexEntries.addIndexValue(
        returnType.getSimpleName() ?: "",
        ComponentProvisionMethodIndexValue(containingClass.getClassId(), wrapper.getSimpleName()),
      )
    }
  }
}

private object ComponentProvisionPropertyIndexer : DaggerConceptIndexer<DaggerIndexFieldWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexFieldWrapper, indexEntries: IndexEntries) {
    // Component properties can only be defined in Kotlin.
    if (wrapper !is KtPropertyWrapper) return

    val propertyType = wrapper.getType() ?: return

    // A provision method must be on a @Component or @Subcomponent.
    val containingClass = wrapper.getContainingClass() ?: return
    if (
      containingClass.getIsAnnotatedWithAnyOf(
        DaggerAnnotation.COMPONENT,
        DaggerAnnotation.SUBCOMPONENT,
      )
    ) {
      indexEntries.addIndexValue(
        propertyType.getSimpleName() ?: "",
        ComponentProvisionPropertyIndexValue(containingClass.getClassId(), wrapper.getSimpleName()),
      )
    }
  }
}

@VisibleForTesting
internal data class ComponentProvisionMethodIndexValue(
  val classId: ClassId,
  val methodSimpleName: String,
) : IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(methodSimpleName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType: DataType = DataType.COMPONENT_PROVISION_METHOD

    override fun read(input: DataInput) =
      ComponentProvisionMethodIndexValue(input.readClassId(), input.readString())
  }

  companion object {
    private fun identify(psiElement: KtFunction): DaggerElement? =
      if (
        psiElement !is KtConstructor<*> &&
          !psiElement.hasBody() &&
          psiElement.valueParameters.isEmpty() &&
          psiElement.containingClassOrObject?.isComponentOrSubcomponent() == true
      ) {
        ComponentProvisionMethodDaggerElement(psiElement)
      } else {
        null
      }

    private fun identify(psiElement: PsiMethod): DaggerElement? =
      if (
        !psiElement.isConstructor &&
          psiElement.body == null &&
          !psiElement.hasParameters() &&
          psiElement.containingClass?.isComponentOrSubcomponent() == true
      ) {
        ComponentProvisionMethodDaggerElement(psiElement)
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
internal data class ComponentProvisionPropertyIndexValue(
  val classId: ClassId,
  val propertySimpleName: String,
) : IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(propertySimpleName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.COMPONENT_PROVISION_PROPERTY

    override fun read(input: DataInput) =
      ComponentProvisionPropertyIndexValue(input.readClassId(), input.readString())
  }

  companion object {
    private fun identify(psiElement: KtProperty): DaggerElement? =
      if (
        !psiElement.hasBody() &&
          psiElement.containingClassOrObject?.isComponentOrSubcomponent() == true
      ) {
        psiElement.psiType?.unboxed?.let { propertyPsiType ->
          ComponentProvisionMethodDaggerElement(psiElement, propertyPsiType)
        }
      } else {
        null
      }

    internal val identifiers: DaggerElementIdentifiers =
      DaggerElementIdentifiers(
        ktPropertyIdentifiers = listOf(DaggerElementIdentifier(this::identify))
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope) =
    KotlinFullClassNameIndex[classId.asFqNameString(), project, scope].asSequence().mapNotNull {
      it.findPropertyByName(propertySimpleName)
    }

  override val daggerElementIdentifiers = identifiers
}

internal data class ComponentProvisionMethodDaggerElement(
  override val psiElement: PsiElement,
  override val rawType: PsiType,
) : ConsumerDaggerElementBase() {

  internal constructor(psiElement: KtFunction) : this(psiElement, psiElement.getReturnedPsiType())

  internal constructor(psiElement: PsiMethod) : this(psiElement, psiElement.getReturnedPsiType())

  override val metricsElementType = DaggerEditorEvent.ElementType.COMPONENT_METHOD

  override val relatedElementGrouping: String = DaggerBundle.message("exposed.by.components")
  override val relationDescriptionKey: String = "navigate.to.provider.from.component"
}

private fun KtClassOrObject.isComponentOrSubcomponent() =
  hasAnnotation(DaggerAnnotation.COMPONENT) || hasAnnotation(DaggerAnnotation.SUBCOMPONENT)

private fun PsiClass.isComponentOrSubcomponent() =
  hasAnnotation(DaggerAnnotation.COMPONENT) || hasAnnotation(DaggerAnnotation.SUBCOMPONENT)
