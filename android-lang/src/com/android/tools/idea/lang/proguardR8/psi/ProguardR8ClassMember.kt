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
package com.android.tools.idea.lang.proguardR8.psi

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType

interface ProguardR8ClassMember : PsiElement {
  val type: ProguardR8Type?
  val parameters: ProguardR8Parameters?
  val accessModifierList: List<ProguardR8AccessModifier>
}

fun ProguardR8ClassMember.resolveParentClasses(): List<PsiClass> {
  return parentOfType<ProguardR8RuleWithClassSpecification>()
    ?.classSpecificationHeader
    ?.resolvePsiClasses()
    .orEmpty()
}

/**
 * Returns true if the class member refers to a constructor.
 *
 * Check this may require resolving other references in the file, so is potentially expensive.
 */
fun ProguardR8ClassMember.isConstructor(): Boolean {
  val name = PsiTreeUtil.findChildOfType(this, ProguardR8ClassMemberName::class.java) ?: return false
  // ProguardR8ClassMemberName could be just java identifier or qualified name. In both cases last child is java identifier.
  val shortName = PsiTreeUtil.lastChild(name).text
  // Constructors don't have return type, but always have parameters, even if it's empty list.
  if (type != null || parameters == null) return false

  val qualifiedName = PsiTreeUtil.findChildOfType(name, ProguardR8QualifiedName::class.java)
  if (qualifiedName != null) {
    val psiClass = qualifiedName.resolveToPsiClass() ?: return false
    return resolveParentClasses().any { it == psiClass }
  } else {
    return resolveParentClasses().any { it.name == shortName }
  }
}
