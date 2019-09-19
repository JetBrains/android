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
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiType
import com.intellij.psi.impl.source.PsiClassReferenceType
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceProvider
import com.intellij.psi.impl.source.resolve.reference.impl.providers.JavaClassReferenceSet
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

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

/**
 * Returns true if ProguardR8Parameters doesn't have errors and matches given "other" PsiParameterList otherwise returns false
 *
 * In general it checks if every type within ProguardR8Parameters [matchesPsiType] type in PsiParameterList at the same position.
 * Tricky case is when ProguardR8Parameters ends with '...' (matches any number of arguments of any type). In this case we need to check
 * that all types at positions before '...' match and after if there are still some types remain at PsiParameterList, we just ignore them.
 */
fun matchesPsiParameterList(parameters: ProguardR8Parameters, psiParameterList: PsiParameterList): Boolean {
  if (PsiTreeUtil.hasErrorElements(parameters)) return false
  if (parameters.isAcceptAnyParameters) return true

  val proguardTypes = PsiTreeUtil.findChildrenOfType(parameters, ProguardR8Type::class.java).toList()
  if (proguardTypes.isEmpty() != psiParameterList.isEmpty) return false
  // endsWithAnyTypAndNumOfArg is true when parameter list looks like (param1, param2, ...).
  val endsWithAnyTypAndNumOfArg = parameters.typeList!!.lastChild?.node?.elementType == ProguardR8PsiTypes.ANY_TYPE_AND_NUM_OF_ARGS
  val psiTypes = psiParameterList.parameters.map { it.type }

  // proguardTypes.size can be less psiTypes because we can match tail of psiTypes to ANY_TYPE_AND_NUM_OF_ARGS.
  if (proguardTypes.size > psiTypes.size) return false

  for (i in psiTypes.indices) {
    // Returns whether we can match tail of psiTypes to ANY_TYPE_AND_NUM_OF_ARGS or not.
    if (i > proguardTypes.lastIndex) return endsWithAnyTypAndNumOfArg
    // Type at the same position in list doesn't much.
    if (!proguardTypes[i].matchesPsiType(psiTypes[i])) return false
  }

  return true
}

fun isAcceptAnyParameters(parameters: ProguardR8Parameters): Boolean {
  return !PsiTreeUtil.hasErrorElements(parameters) &&
         PsiTreeUtil.findChildOfType(parameters, ProguardR8TypeList::class.java) == null &&
         parameters.node.findChildByType(ProguardR8PsiTypes.ANY_TYPE_AND_NUM_OF_ARGS) != null
}
