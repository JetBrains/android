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
import com.android.tools.idea.dagger.index.psiwrappers.DaggerIndexMethodWrapper
import com.android.tools.idea.dagger.localization.DaggerBundle
import com.android.tools.idea.kotlin.hasAnnotation
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import java.io.DataInput
import java.io.DataOutput
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject

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
 */
internal object ComponentProvisionMethodConcept : DaggerConcept {
  override val indexers =
    DaggerConceptIndexers(methodIndexers = listOf(ComponentProvisionMethodIndexer))
  override val indexValueReaders: List<IndexValue.Reader> =
    listOf(ComponentProvisionMethodIndexValue.Reader)
  override val daggerElementIdentifiers = ComponentProvisionMethodIndexValue.identifiers
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
        DaggerAnnotations.COMPONENT,
        DaggerAnnotations.SUBCOMPONENT
      )
    ) {
      indexEntries.addIndexValue(
        returnType.getSimpleName(),
        ComponentProvisionMethodIndexValue(containingClass.getFqName(), wrapper.getSimpleName())
      )
    }
  }
}

@VisibleForTesting
internal data class ComponentProvisionMethodIndexValue(
  val classFqName: String,
  val methodSimpleName: String
) : IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeString(classFqName)
    output.writeString(methodSimpleName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType: DataType = DataType.COMPONENT_PROVISION_METHOD
    override fun read(input: DataInput) =
      ComponentProvisionMethodIndexValue(input.readString(), input.readString())
  }

  companion object {
    private val identifyComponentProvisionMethodKotlin =
      DaggerElementIdentifier<KtFunction> { psiElement ->
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
      }

    private val identifyComponentProvisionMethodJava =
      DaggerElementIdentifier<PsiMethod> { psiElement ->
        if (
          !psiElement.isConstructor &&
            psiElement.body == null &&
            psiElement.parameters.isEmpty() &&
            psiElement.containingClass?.isComponentOrSubcomponent() == true
        ) {
          ComponentProvisionMethodDaggerElement(psiElement)
        } else {
          null
        }
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktFunctionIdentifiers = listOf(identifyComponentProvisionMethodKotlin),
        psiMethodIdentifiers = listOf(identifyComponentProvisionMethodJava)
      )

    private fun KtClassOrObject.isComponentOrSubcomponent() =
      hasAnnotation(DaggerAnnotations.COMPONENT) || hasAnnotation(DaggerAnnotations.SUBCOMPONENT)

    private fun PsiClass.isComponentOrSubcomponent() =
      hasAnnotation(DaggerAnnotations.COMPONENT) || hasAnnotation(DaggerAnnotations.SUBCOMPONENT)
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> {
    val psiClass =
      JavaPsiFacade.getInstance(project).findClass(classFqName, scope) ?: return emptyList()
    return psiClass.methods.filter { it.name == methodSimpleName }
  }

  override val daggerElementIdentifiers = identifiers
}

internal data class ComponentProvisionMethodDaggerElement(
  override val psiElement: PsiElement,
  override val rawType: PsiType,
) : ConsumerDaggerElementBase() {

  internal constructor(psiElement: KtFunction) : this(psiElement, psiElement.getReturnedPsiType())
  internal constructor(psiElement: PsiMethod) : this(psiElement, psiElement.getReturnedPsiType())

  override val relatedElementGrouping: String = DaggerBundle.message("exposed.by.components")
}
