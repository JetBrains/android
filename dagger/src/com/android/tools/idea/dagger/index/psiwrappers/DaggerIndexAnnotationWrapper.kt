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
package com.android.tools.idea.dagger.index.psiwrappers

import com.intellij.psi.PsiAnnotation
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/** A [DaggerIndexPsiWrapper] representing an annotation. */
interface DaggerIndexAnnotationWrapper : DaggerIndexPsiWrapper {
  /** Name of the annotation in source. Can be a fully-qualified or simple name. */
  fun getAnnotationNameInSource(): String
}

internal class KtAnnotationEntryWrapper(private val ktAnnotationEntry: KtAnnotationEntry) :
  DaggerIndexAnnotationWrapper {
  override fun getAnnotationNameInSource(): String = ktAnnotationEntry.typeReference!!.text
}

internal class PsiAnnotationWrapper(private val psiAnnotation: PsiAnnotation) :
  DaggerIndexAnnotationWrapper {
  override fun getAnnotationNameInSource(): String = psiAnnotation.nameReferenceElement!!.text
}
