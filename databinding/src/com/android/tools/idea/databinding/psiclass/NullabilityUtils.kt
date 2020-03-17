/*
 * Copyright (C) 2020 The Android Open Source Project
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
@file:JvmName("NullabilityUtils")
package com.android.tools.idea.databinding.psiclass

import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiType

fun Project.createNullabilityAnnotation(isNonNull: Boolean, context: PsiElement?): PsiAnnotation {
  val nullabilityManager = NullableNotNullManager.getInstance(this)
  val annotationText = if (isNonNull) nullabilityManager.defaultNotNull else nullabilityManager.defaultNullable
  return PsiElementFactory.getInstance(this).createAnnotationFromText("@$annotationText", context)
}

@Suppress("UNCHECKED_CAST") // Passed in PsiType is returned directly or cloned
fun <T : PsiType> Project.annotateType(type: T, isNonNull: Boolean, context: PsiElement?): T {
  val annotation = arrayOf(createNullabilityAnnotation(isNonNull, context))
  return type.annotate { annotation } as T
}