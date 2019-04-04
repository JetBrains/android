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
import android.databinding.tool.reflection.ModelField
import android.databinding.tool.reflection.ModelMethod
import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiType
import com.intellij.psi.util.PsiTypesUtil

class PsiModelClass(val type: PsiType) : ModelClass() {
  /**
   * Constructs a [PsiClass] of the given [.type]. Returns null if [.type] is not an instance of [PsiClassType].
   */
  val psiClass: PsiClass?
    get() = (type as? PsiClassType)?.resolve()

  override val componentType: ModelClass?
    get() {
      // TODO: Support list and map type.
      // For list, it's the return type of the method get(int). For method, it's the second generic type.
      return (type as? PsiArrayType)?.let { PsiModelClass(it).componentType }
    }

  override val isArray = type is PsiArrayType

  override val isNullable = !isPrimitive

  override val isPrimitive: Boolean
    get() {
      val canonicalText = type.getCanonicalText(false)
      val boxed = PsiTypesUtil.boxIfPossible(canonicalText)
      return boxed != canonicalText
    }

  override val isBoolean = PsiType.BOOLEAN.equalsToText(type.canonicalText)

  override val isChar = PsiType.CHAR.equalsToText(type.canonicalText)

  override val isByte = PsiType.BYTE.equalsToText(type.canonicalText)

  override val isShort = PsiType.SHORT.equalsToText(type.canonicalText)

  override val isInt = PsiType.INT.equalsToText(type.canonicalText)

  override val isLong = PsiType.LONG.equalsToText(type.canonicalText)

  override val isFloat = PsiType.FLOAT.equalsToText(type.canonicalText)

  override val isDouble = PsiType.DOUBLE.equalsToText(type.canonicalText)

  override val isGeneric = (type as? PsiClassType)?.hasParameters() == true

  // b/129719057 revisit wildcard implementation
  override val isWildcard = false

  override val isVoid = PsiType.VOID.equalsToText(type.canonicalText)

  override val isTypeVar = false

  override val isInterface: Boolean
    get() = (type as? PsiClassType)?.resolve()?.isInterface ?: false

  override val typeArguments: List<ModelClass>
    get() = (type as? PsiClassType)?.parameters
              ?.map { typeParameter -> PsiModelClass(typeParameter) }
            ?: listOf()

  override val superclass: ModelClass?
    get() = type.superTypes
      .takeUnless { supers -> supers.isEmpty() || supers[0] == null }
      ?.let { supers -> PsiModelClass(supers[0]) }

  override val jniDescription = canonicalName

  override val allFields: List<ModelField>
    get() = (type as? PsiClassType)?.resolve()?.allFields?.map { PsiModelField(it) } ?: listOf()

  override val allMethods: List<ModelMethod>
    get() = (type as? PsiClassType)?.resolve()?.allMethods?.map { PsiModelMethod(it) } ?: listOf()

  override fun toJavaCode() = type.canonicalText

  override fun unbox() = this

  override fun box() = this

  override fun isAssignableFrom(that: ModelClass?) = that is PsiModelClass && type.isAssignableFrom(that.type)

  override fun erasure() = this
}
