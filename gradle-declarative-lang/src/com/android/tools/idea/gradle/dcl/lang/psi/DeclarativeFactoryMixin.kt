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
package com.android.tools.idea.gradle.dcl.lang.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.CompositePsiElement
import com.intellij.psi.tree.IElementType

abstract class DeclarativeFactoryMixin(type: IElementType) : CompositePsiElement(type), DeclarativeAbstractFactory {
  override fun addArgument(value: DeclarativeValue, name: String?): PsiElement {
    val generator = DeclarativePsiFactory(getProject())
    if (argumentsList == null) this.add(generator.createArgumentList())

    val parameter = generator.createArgument(value, name)
    argumentsList?.let {
      if (it.arguments.isNotEmpty()) {
        it.add(generator.createComma())
      }
      it.add(parameter)
    } ?: error("Cannot addArgument as argument list is empty for $text")
    return this
  }
}