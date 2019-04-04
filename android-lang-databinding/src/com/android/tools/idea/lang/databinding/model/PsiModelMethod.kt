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

import android.databinding.tool.reflection.ModelClass
import android.databinding.tool.reflection.ModelMethod
import com.intellij.psi.*
import com.intellij.psi.util.PsiTypesUtil

class PsiModelMethod(val psiMethod: PsiMethod) : ModelMethod() {

  override fun getDeclaringClass() = psiMethod.containingClass?.let { PsiModelClass(PsiTypesUtil.getClassType(it)) }

  override fun getParameterTypes() = psiMethod.parameterList.parameters.map { PsiModelClass(it.type) }.toTypedArray()

  override fun getName() = psiMethod.name

  override fun getReturnType(list: List<ModelClass>?): ModelClass? = psiMethod.returnType?.let { PsiModelClass(it) }

  override fun isVoid() = PsiType.VOID == psiMethod.returnType

  override fun isPublic() = psiMethod.hasModifierProperty(PsiModifier.PUBLIC)

  override fun isProtected() = psiMethod.hasModifierProperty(PsiModifier.PROTECTED)

  override fun isStatic() = psiMethod.hasModifierProperty(PsiModifier.STATIC)

  override fun isAbstract() = psiMethod.hasModifierProperty(PsiModifier.ABSTRACT)

  override fun getMinApi() = 0

  override fun getJniDescription() = psiMethod.name

  override fun isVarArgs() = psiMethod.isVarArgs
}
