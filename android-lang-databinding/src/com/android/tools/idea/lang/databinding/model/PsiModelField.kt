/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.lang.databinding.model

import android.databinding.tool.BindableCompat
import android.databinding.tool.reflection.ModelField
import com.android.tools.idea.databinding.DataBindingMode
import com.intellij.psi.PsiField
import com.intellij.psi.PsiModifier

class PsiModelField(val psiField: PsiField) : ModelField() {

  companion object {
    private val BINDABLE_COMPAT = BindableCompat(arrayOf())
  }

  override fun getBindableAnnotation(): BindableCompat? {
    // we don't care about dependencies in studio so we can return a shared instance.
    return if (psiField.modifierList?.annotations
        ?.any { annotation ->
          DataBindingMode.SUPPORT.bindable == annotation.qualifiedName
          || DataBindingMode.ANDROIDX.bindable == annotation.qualifiedName
        } == true) BINDABLE_COMPAT
    else null
  }

  override fun getName() = psiField.name

  override fun isPublic() = psiField.hasModifierProperty(PsiModifier.PUBLIC)

  override fun isStatic() = psiField.hasModifierProperty(PsiModifier.STATIC)

  override fun isFinal() = psiField.hasModifierProperty(PsiModifier.FINAL)

  override fun getFieldType() = PsiModelClass(psiField.type)
}
