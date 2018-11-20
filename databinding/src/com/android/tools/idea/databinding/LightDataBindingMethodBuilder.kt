/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.databinding

import com.intellij.lang.Language
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiModifierList
import com.intellij.psi.PsiParameterList
import com.intellij.psi.PsiReferenceList
import com.intellij.psi.PsiTypeParameterList
import com.intellij.psi.impl.light.LightMethodBuilder
import com.intellij.psi.impl.source.javadoc.PsiDocCommentImpl
import com.intellij.psi.javadoc.PsiDocComment

/**
 * A LightMethodBuilder that supports deprecation.
 *
 * TODO(b/119872892): Investigate upstreaming fixes to LightMethodBuilder so we don't need this class.
 */
class LightDataBindingMethodBuilder(
  manager: PsiManager,
  language: Language,
  name: String
) : LightMethodBuilder(manager, language, name) {

  private var deprecated: Boolean = false

  override fun isDeprecated() = deprecated

  fun setDeprecated(deprecated: Boolean) {
    this.deprecated = deprecated
  }

  override fun equals(other: Any?): Boolean {
    return  super.equals(other) &&
            (other as? LightDataBindingMethodBuilder)?.deprecated == deprecated
  }

  override fun hashCode(): Int {
    var result = super.hashCode()
    result = 31 * result + deprecated.hashCode()
    return result
  }
}