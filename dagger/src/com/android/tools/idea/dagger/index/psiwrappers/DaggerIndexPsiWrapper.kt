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
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiTypeElement
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeReference

/**
 * A wrapper around a [com.intellij.psi.PsiElement] to be used when constructing the Dagger index.
 *
 * At indexing time, type resolution and other deeper analysis is not available. As such, the
 * wrappers implementing this interface exist to help enforce that they only use the operations that
 * are available at indexing time. Additionally, the use of wrappers allows indexing to be done in a
 * common way for both Java and Kotlin.
 *
 * All wrapper implementations are meant to be lightweight. As such, constructing them creates a new
 * object and stores a reference to the contained [PsiElement], but does not do any other work. This
 * means that there are no properties initialized to values coming from the element, and any calls
 * to get information about the element should be passed through only at the time the call is made.
 * (It's to encourage this design that the wrappers all have explicit "getter" functions, rather
 * than using a more idiomatic Kotlin property.)
 *
 * The interface itself does not have any methods, but rather is used to identify all the wrappers
 * contained in the package.
 */
sealed interface DaggerIndexPsiWrapper {
  class KotlinFactory(ktFile: KtFile) {
    private val importHelper = KotlinImportHelper(ktFile)
    fun of(psiElement: KtAnnotationEntry): DaggerIndexAnnotationWrapper =
      KtAnnotationEntryWrapper(psiElement)
    fun of(psiElement: KtClassOrObject): DaggerIndexClassWrapper =
      KtClassOrObjectWrapper(psiElement, importHelper)
    fun of(psiElement: KtFunction): DaggerIndexMethodWrapper =
      KtFunctionWrapper(psiElement, importHelper)
    fun of(psiElement: KtParameter): DaggerIndexParameterWrapper =
      KtParameterWrapper(psiElement, importHelper)
    fun of(psiElement: KtProperty): DaggerIndexFieldWrapper =
      KtPropertyWrapper(psiElement, importHelper)
    fun of(psiElement: KtTypeReference): DaggerIndexTypeWrapper =
      KtTypeReferenceWrapper(psiElement, importHelper)
  }

  class JavaFactory(psiJavaFile: PsiJavaFile) {
    private val importHelper = JavaImportHelper(psiJavaFile)
    fun of(psiElement: PsiAnnotation): DaggerIndexAnnotationWrapper =
      PsiAnnotationWrapper(psiElement)
    fun of(psiElement: PsiClass): DaggerIndexClassWrapper =
      PsiClassWrapper(psiElement, importHelper)
    fun of(psiElement: PsiField): DaggerIndexFieldWrapper =
      PsiFieldWrapper(psiElement, importHelper)
    fun of(psiElement: PsiMethod): DaggerIndexMethodWrapper =
      PsiMethodWrapper(psiElement, importHelper)
    fun of(psiElement: PsiParameter): DaggerIndexParameterWrapper = PsiParameterWrapper(psiElement)
    fun of(psiElement: PsiTypeElement): DaggerIndexTypeWrapper = PsiTypeElementWrapper(psiElement)
  }
}
