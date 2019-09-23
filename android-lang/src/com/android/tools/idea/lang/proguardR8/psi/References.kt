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

import com.intellij.codeInsight.completion.JavaLookupElementBuilder
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElementResolveResult
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPolyVariantReferenceBase
import com.intellij.psi.PsiSubstitutor
import com.intellij.psi.util.parentOfType

/**
 *  There is one reference type used for both field and method name, even though we have separate PSI nodes for fields and methods,
 *  because getVariants() will may return methods for PSI that is (user haven't typed parentheses yet) a field.
 */
class ProguardR8ClassMemberNameReference(
  member: ProguardR8ClassMemberName
) : PsiPolyVariantReferenceBase<ProguardR8ClassMemberName>(member) {
  val type = member.parentOfType(ProguardR8ClassMember::class)!!.type
  val parameters = member.parentOfType(ProguardR8ClassMember::class)!!.parameters

  fun resolveParentClasses(): List<PsiClass> {
    return element.parentOfType<ProguardR8RuleWithClassSpecification>()
      ?.classSpecificationHeader
      ?.resolvePsiClasses()
      .orEmpty()
  }

  private fun getFields(): Collection<PsiField> {
    return resolveParentClasses().asSequence()
      .flatMap { it.fields.asSequence() }
      .filter { type == null || type.matchesPsiType(it.type) }
      .toList()
  }

  private fun getMethods(): Collection<PsiMethod> {
    return resolveParentClasses().asSequence()
      .flatMap { it.methods.asSequence() }
      .filter { it.returnType != null } // if returnType is null it's constructor
      .filter { type == null || type.matchesPsiType(it.returnType!!) } // match return type
      .filter { parameters == null || parameters.matchesPsiParameterList(it.parameterList) } // match parameters
      .toList()
  }

  /**
   * Returns empty array if there is no class member matching the type and parameters corresponding to this [ProguardR8ClassMemberName]
   * otherwise returns array with found class members.
   * It can be single element array or not (case with overloads/different access modifiers/not specified return type/ etc.)
   */
  override fun multiResolve(incompleteCode: Boolean): Array<PsiElementResolveResult> {
    val members: Collection<PsiNamedElement> = if (parameters == null) getFields() else getMethods()
    return members.filter { it.name == element.text }.map(::PsiElementResolveResult).toTypedArray()
  }

  override fun getVariants(): Array<Any> {
    val fields = (if (parameters == null) getFields() else emptyList()).map(JavaLookupElementBuilder::forField)
    val methods = getMethods().map { JavaLookupElementBuilder.forMethod(it, PsiSubstitutor.EMPTY) }
    return (fields + methods).toTypedArray()
  }
}
