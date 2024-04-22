/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.declarative.psi

import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.util.IncorrectOperationException
import org.jetbrains.annotations.NonNls

abstract class DeclarativeIdentifierMixin(node: ASTNode): ASTWrapperPsiElement(node), DeclarativeIdentifier {

  @Throws(IncorrectOperationException::class)
  override fun setName(@NonNls name: String): PsiElement {
    val generator = DeclarativePsiFactory(getProject())
    replace(generator.createIdentifier(StringUtil.unquoteString(name)))
    return this
  }
}