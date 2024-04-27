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
import com.android.tools.idea.dagger.index.psiwrappers.hasAnnotation
import com.android.tools.idea.dagger.index.readClassId
import com.android.tools.idea.dagger.index.writeClassId
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.search.GlobalSearchScope
import org.jetbrains.annotations.VisibleForTesting
import org.jetbrains.kotlin.idea.core.util.readString
import org.jetbrains.kotlin.idea.core.util.writeString
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.containingClassOrObject
import java.io.DataInput
import java.io.DataOutput

/**
 * Represents an injected field in Dagger.
 *
 * Example:
 * ```java
 * class CoffeeMaker {
 *   @Inject Heater heater;
 *   @Inject Pump pump;
 *   ...
 * }
 * ```
 *
 * This concept deals with one type of index entry:
 * 1. The injected field (`heater` and `pump`).
 */
internal object InjectedFieldDaggerConcept : DaggerConcept {
  override val indexers = DaggerConceptIndexers(fieldIndexers = listOf(InjectedFieldIndexer))
  override val indexValueReaders: List<IndexValue.Reader> = listOf(InjectedFieldIndexValue.Reader)
  override val daggerElementIdentifiers = InjectedFieldIndexValue.identifiers
}

private object InjectedFieldIndexer : DaggerConceptIndexer<DaggerIndexFieldWrapper> {
  override fun addIndexEntries(wrapper: DaggerIndexFieldWrapper, indexEntries: IndexEntries) {
    if (!wrapper.getIsAnnotatedWith(DaggerAnnotation.INJECT)) return
    val classId = wrapper.getContainingClass()?.getClassId() ?: return

    val fieldTypeSimpleName = wrapper.getType()?.getSimpleName() ?: ""
    val fieldName = wrapper.getSimpleName()
    indexEntries.addIndexValue(fieldTypeSimpleName, InjectedFieldIndexValue(classId, fieldName))
  }
}

@VisibleForTesting
internal data class InjectedFieldIndexValue(val classId: ClassId, val fieldName: String) :
  IndexValue() {
  override val dataType = Reader.supportedType

  override fun save(output: DataOutput) {
    output.writeClassId(classId)
    output.writeString(fieldName)
  }

  object Reader : IndexValue.Reader {
    override val supportedType = DataType.INJECTED_FIELD

    override fun read(input: DataInput) =
      InjectedFieldIndexValue(input.readClassId(), input.readString())
  }

  companion object {
    private fun identify(psiElement: KtProperty): DaggerElement? =
      if (
        psiElement.containingClassOrObject != null &&
          psiElement.hasAnnotation(DaggerAnnotation.INJECT)
      ) {
        ConsumerDaggerElement(psiElement)
      } else {
        null
      }

    private fun identify(psiElement: PsiField): DaggerElement? =
      if (psiElement.hasAnnotation(DaggerAnnotation.INJECT)) {
        ConsumerDaggerElement(psiElement)
      } else {
        null
      }

    internal val identifiers =
      DaggerElementIdentifiers(
        ktPropertyIdentifiers = listOf(DaggerElementIdentifier(this::identify)),
        psiFieldIdentifiers = listOf(DaggerElementIdentifier(this::identify))
      )
  }

  override fun getResolveCandidates(project: Project, scope: GlobalSearchScope): List<PsiElement> {
    val psiClass =
      JavaPsiFacade.getInstance(project).findClass(classId.asFqNameString(), scope)
        ?: return emptyList()
    return psiClass.fields.filter { it.name == fieldName }
  }

  override val daggerElementIdentifiers = identifiers
}
