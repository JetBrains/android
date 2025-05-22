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
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.io.DataInput
import java.io.DataOutput

/**
 * Represents a method in a class annotated with @EntryPoint.
 *
 * Example:
 * ```java
 *   @EntryPoint
 *   @InstallIn(SingletonComponent.class)
 *   public interface FooBarInterface {
 *     @Foo Bar bar();
 *   }
 * ```
 *
 * In the above example, `bar` is an entry point method.
 *
 * See [EntryPoint](https://dagger.dev/hilt/entry-points) for details.
 */
object EntryPointMethodDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(methodIndexers = listOf(EntryPointMethodIndexer))
  override val indexValueReaders: List<IndexValue.Reader> =
    listOf(EntryPointMethodIndexValue.Reader)
  override val daggerElementIdentifiers =
    DaggerElementIdentifiers.of(EntryPointMethodIndexValue.identifiers)
}

private object EntryPointMethodIndexer : DaggerConceptIndexer<DaggerIndexMethodWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexMethodWrapper, indexEntries: IndexEntries) {
    if (wrapper.getIsConstructor() || wrapper.getParameters().any()) return

    val containingClass = wrapper.getContainingClass() ?: return
    if (!containingClass.getIsAnnotatedWith(DaggerAnnotation.ENTRY_POINT)) return

    // If the method doesn't have a defined return type, then we don't need to index it - an
    // entry point function is abstract so type inference can't be used to figure out the intended
    // type; any function without a specified return can't be an entry point function.
    val methodReturnTypeSimpleName = wrapper.getReturnType()?.getSimpleName() ?: return

    val classId = containingClass.getClassId()
    val methodSimpleName = wrapper.getSimpleName()

    indexEntries.addIndexValue(
      methodReturnTypeSimpleName,
      EntryPointMethodIndexValue(classId, methodSimpleName),
    )
  }
}

@VisibleForTesting
internal data class EntryPointMethodIndexValue(val classId: ClassId, val methodSimpleName: String) :
  IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(methodSimpleName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.ENTRY_POINT_METHOD

    override fun read(input: DataInput) =
      EntryPointMethodIndexValue(input.readClassId(), input.readString())
  }

  companion object {
    private fun identify(psiElement: KtFunction): DaggerElement? =
      if (
        psiElement !is KtConstructor<*> &&
          !psiElement.hasBody() &&
          psiElement.valueParameters.isEmpty() &&
          psiElement.containingClassOrObject?.hasAnnotation(DaggerAnnotation.ENTRY_POINT) == true
      ) {
        EntryPointMethodDaggerElement(psiElement)
      } else {
        null
      }

    private fun identify(psiElement: PsiMethod): DaggerElement? =
      if (
        !psiElement.isConstructor &&
          psiElement.body == null &&
          !psiElement.hasParameters() &&
          psiElement.containingClass?.hasAnnotation(DaggerAnnotation.ENTRY_POINT) == true
      ) {
        EntryPointMethodDaggerElement(psiElement)
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

internal data class EntryPointMethodDaggerElement(
  override val psiElement: PsiElement,
  override val rawType: PsiType,
) : ConsumerDaggerElementBase() {

  internal constructor(psiElement: KtFunction) : this(psiElement, psiElement.getReturnedPsiType())

  internal constructor(psiElement: PsiMethod) : this(psiElement, psiElement.getReturnedPsiType())

  override val metricsElementType = DaggerEditorEvent.ElementType.ENTRY_POINT_METHOD

  override val relatedElementGrouping = DaggerBundle.message("exposed.by.entry.points")
  override val relationDescriptionKey: String = "navigate.to.provider.from.component"
}
