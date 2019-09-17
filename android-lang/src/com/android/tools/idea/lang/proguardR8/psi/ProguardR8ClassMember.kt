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

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.ElementManipulators
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.tree.TokenSet

interface ProguardR8ClassMember : PsiNamedElement {
  val containsWildcards: Boolean
  override fun getReference(): ProguardR8ClassMemberReference?
}

abstract class AbstractProguardR8ClassMember(node: ASTNode) : ASTWrapperPsiElement(node), ProguardR8ClassMember {

  companion object {
    private val wildcardsTokenSet = TokenSet.create(ProguardR8PsiTypes.JAVA_IDENTIFIER_WITH_WILDCARDS, ProguardR8PsiTypes.ASTERISK)
  }

  override val containsWildcards: Boolean
    get() = node.findChildByType(wildcardsTokenSet) != null

  override fun setName(newName: String): ProguardR8ClassMember? {
    if (name == newName) return this
    return ElementManipulators.getManipulator(this).handleContentChange(this, newName)
  }

  override fun getName(): String? {
    return text
  }

  override fun getReference(): ProguardR8ClassMemberReference? = null
}
