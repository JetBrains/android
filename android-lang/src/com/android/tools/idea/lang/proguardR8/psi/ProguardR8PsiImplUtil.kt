/*
 * Copyright (C) 2019 The Android Open Source Project
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

@file:JvmName("ProguardR8PsiImplUtil")

package com.android.tools.idea.lang.proguardR8.psi

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType

private class ProguardR8JavaClassReferenceProvider(val scope: GlobalSearchScope) : JavaClassReferenceProvider() {

  override fun getScope(project: Project): GlobalSearchScope = scope

  override fun getReferencesByString(str: String, position: PsiElement, offsetInPosition: Int): Array<PsiReference> {
    return if (StringUtil.isEmpty(str)) {
      PsiReference.EMPTY_ARRAY
    }
    else {
      object : JavaClassReferenceSet(str, position, offsetInPosition, true, this) {
        // Allows inner classes be separated by a dollar sign "$", e.g.java.lang.Thread$State
        // We can't just use ALLOW_DOLLAR_NAMES flag because to make JavaClassReferenceSet work in the way we want;
        // language of PsiElement that we parse should be instanceof XMLLanguage.
        override fun isAllowDollarInNames() = true

      }.allReferences as Array<PsiReference>
    }
  }
}

fun getReferences(className: ProguardR8QualifiedName): Array<PsiReference> {
  val provider = ProguardR8JavaClassReferenceProvider(className.resolveScope)

  return provider.getReferencesByElement(className)
}

fun resolveToPsiClass(className: ProguardR8QualifiedName): PsiClass? {
  // We take last reference because it corresponds to PsiClass (or not), previous are for packages
  val lastElement = className.references.lastOrNull()?.resolve()
  return lastElement as? PsiClass
}

fun resolvePsiClasses(classSpecificationHeader: ProguardR8ClassSpecificationHeader): List<PsiClass> {
  return classSpecificationHeader.classNameList.mapNotNull { it.qualifiedName.resolveToPsiClass() }
}

fun getPsiPrimitive(proguardR8JavaPrimitive: ProguardR8JavaPrimitive): PsiPrimitiveType? {
  val primitive = proguardR8JavaPrimitive.node.firstChildNode
  return when (primitive.elementType) {
    ProguardR8PsiTypes.BOOLEAN -> PsiPrimitiveType.BOOLEAN
    ProguardR8PsiTypes.BYTE -> PsiPrimitiveType.BYTE
    ProguardR8PsiTypes.CHAR -> PsiPrimitiveType.CHAR
    ProguardR8PsiTypes.SHORT -> PsiPrimitiveType.SHORT
    ProguardR8PsiTypes.INT -> PsiPrimitiveType.INT
    ProguardR8PsiTypes.LONG -> PsiPrimitiveType.LONG
    ProguardR8PsiTypes.FLOAT -> PsiPrimitiveType.FLOAT
    ProguardR8PsiTypes.DOUBLE -> PsiPrimitiveType.DOUBLE
    ProguardR8PsiTypes.VOID -> PsiPrimitiveType.VOID
    else -> {
      assert(false) { "Couldn't match ProguardR8JavaPrimitive \"${primitive.text}\" to PsiPrimitive" }
      null
    }
  }
}

fun isArray(type: ProguardR8Type): Boolean {
  return !PsiTreeUtil.hasErrorElements(type) && type.text.endsWith("[]")
}

/**
 * Returns true if ProguardR8Type would match given "other" PsiType otherwise returns false
 */
fun matchesPsiType(type: ProguardR8Type, other: PsiType): Boolean {
  var typeToMatch = other
  if (type.isArray) {
    if (other is PsiArrayType) {
      typeToMatch = other.componentType
    }
    else {
      return false
    }
  }
  return when {
    type.javaPrimitive != null -> type.javaPrimitive!!.psiPrimitive == typeToMatch
    type.qualifiedName != null && typeToMatch is PsiClassReferenceType -> type.qualifiedName!!.resolveToPsiClass() == typeToMatch.resolve()
    // "%" matches any primitive type ("boolean", "int", etc, but not "void").
    type.anyPrimitiveType != null -> typeToMatch is PsiPrimitiveType && typeToMatch != PsiPrimitiveType.VOID
    type.anyType != null -> true
    else -> false
  }
}

fun getType(field: ProguardR8FieldName): ProguardR8Type? {
  return field.parentOfType(ProguardR8FieldsSpecification::class)?.type
}

fun getReference(field: ProguardR8FieldName) = if (field.containsWildcards) null else ProguardR8FieldReference(field)
