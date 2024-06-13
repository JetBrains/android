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
package com.android.tools.idea.databinding.utils

import com.google.common.truth.Truth.assertThat
import com.intellij.codeInsight.NullableNotNullManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiParameter
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType

internal fun PsiType.assertExpected(
  project: Project,
  typeName: String,
  isNullable: Boolean = false,
) {
  assertThat(presentableText).isEqualTo(typeName)
  if (this !is PsiPrimitiveType) {
    val nullabilityManager = NullableNotNullManager.getInstance(project)
    val managerAnnotations =
      if (isNullable) nullabilityManager.nullables else nullabilityManager.notNulls
    assertThat(annotations.map { it.text }).containsAnyIn(managerAnnotations.map { "@$it" })
  }
}

internal fun PsiParameter.assertExpected(
  typeName: String,
  name: String,
  isNullable: Boolean = false,
) {
  assertThat(this.name).isEqualTo(name)
  assertThat(type.presentableText).isEqualTo(typeName)
  if (type !is PsiPrimitiveType) {
    val nullabilityManager = NullableNotNullManager.getInstance(project)
    if (isNullable) {
      assertThat(nullabilityManager.isNullable(this, false)).isTrue()
    } else {
      assertThat(nullabilityManager.isNotNull(this, false)).isTrue()
    }
  }
}
