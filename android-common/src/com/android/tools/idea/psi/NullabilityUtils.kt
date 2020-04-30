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
package com.android.tools.idea.psi

import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiType
import com.intellij.psi.search.GlobalSearchScope

private const val PREFIX_ANDROIDX = "androidx.annotation"
private const val PREFIX_SUPPORT = "android.support.annotation"
private const val NONNULL = "NonNull"
private const val NULLABLE = "Nullable"

private fun Project.findClass(fcqn: String, context: PsiElement?): PsiClass? {
  val facade = JavaPsiFacade.getInstance(this)
  return facade.findClass(fcqn, context?.resolveScope ?: GlobalSearchScope.projectScope(this))
}

fun Project.createNullabilityAnnotation(isNonNull: Boolean, context: PsiElement?): PsiAnnotation {
  // Prefer androidx.annotation as much as possible, as it is the preferred annotation moving
  // forward, but fall back to the support annotation in case we are loading a legacy project
  val prefix = if (findClass("$PREFIX_ANDROIDX.$NONNULL", context) != null || findClass("$PREFIX_SUPPORT.$NONNULL", context) == null) {
    PREFIX_ANDROIDX
  }
  else {
    PREFIX_SUPPORT
  }
  val suffix = if (isNonNull) NONNULL else NULLABLE

  return PsiElementFactory.getInstance(this).createAnnotationFromText("@$prefix.$suffix", context)
}

@Suppress("UNCHECKED_CAST") // Passed in PsiType is returned directly or cloned
fun <T : PsiType> Project.annotateType(type: T, isNonNull: Boolean, context: PsiElement?): T {
  val annotation = arrayOf(createNullabilityAnnotation(isNonNull, context))
  return type.annotate { annotation } as T
}
