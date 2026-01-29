/*
 * Copyright (C) 2026 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.elements

import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel
import com.android.tools.idea.gradle.dsl.parser.GradleDslParser.DataType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.psi.PsiElement

class GradleDslReferenceExpression(
  parent: GradleDslElement,
  psiElement: PsiElement,
  name: GradleNameElement,
  val propertyExpression: PsiElement,
) : GradleDslLiteral(parent, psiElement, name, propertyExpression, LiteralType.REFERENCE) {

  fun getForcedType(): GradlePropertyModel.ValueType? {
    val type = ApplicationManager.getApplication().runReadAction<DataType> { dslFile.parser.extractResultType(propertyExpression) }
    return when (type) {
      DataType.STRING -> GradlePropertyModel.ValueType.STRING
      DataType.BIG_DECIMAL -> GradlePropertyModel.ValueType.BIG_DECIMAL
      DataType.INTEGER -> GradlePropertyModel.ValueType.INTEGER
      DataType.BOOLEAN -> GradlePropertyModel.ValueType.BOOLEAN
      else -> null
    }
  }
}
