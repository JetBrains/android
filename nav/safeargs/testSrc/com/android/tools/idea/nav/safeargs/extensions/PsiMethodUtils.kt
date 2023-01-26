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
package com.android.tools.idea.nav.safeargs.extensions

import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType

/**
 * A simple sanity check helper for [PsiMethod] instances using String checks.
 */
fun PsiMethod.checkSignaturesAndReturnType(
  name: String,
  returnType: String,
  isReturnTypeNullable: Boolean = false,
  parameters: Collection<Parameter> = emptyList()
) {
  assertThat(this.name).isEqualTo(name)

  if (returnType == "null") {
    assertThat(getTypeName(this.returnType)).isNull()
  }
  else {
    assertThat(getTypeName(this.returnType)).isEqualTo(returnType)

    val nullabilityManager = NullableNotNullManager.getInstance(this.project)
    assertThat(nullabilityManager.isNullable(this, false)).isEqualTo(isReturnTypeNullable)
  }

  assertThat(this.parameters.size).isEqualTo(parameters.size)

  this.parameters.map { parameter ->
    val pName = parameter.name!!
    val pType = getTypeName((parameter as PsiParameter).type)!!
    Parameter(pName, pType)
  }.containsAll(parameters)
}

private fun getTypeName(type: PsiType?): String? {
  type ?: return null
  return when (type) {
    is PsiPrimitiveType -> type.name
    is PsiClassType -> type.name
    is PsiArrayType -> {
      getTypeName(type.componentType)?.let { componentType -> "$componentType[]" }
    }
    else -> null
  }
}

data class Parameter(
  val name: String,
  val type: String
)

