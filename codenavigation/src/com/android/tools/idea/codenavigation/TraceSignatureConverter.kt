/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.codenavigation

import com.intellij.psi.PsiArrayType
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiPrimitiveType
import com.intellij.psi.PsiType
import com.intellij.psi.util.TypeConversionUtil
import org.jetbrains.annotations.VisibleForTesting

object TraceSignatureConverter {
  /**
   * Mapping from java primitive type encoding to PsiPrimitiveType. More about java primitive type
   * encoding: https://docs.oracle.com/javase/7/docs/api/java/lang/Class.html#getName()
   */
  private val primitiveTypes = mapOf(
    PsiType.BYTE to "B",
    PsiType.CHAR to "C",
    PsiType.DOUBLE to "D",
    PsiType.FLOAT to "F",
    PsiType.INT to "I",
    PsiType.LONG to "J",
    PsiType.SHORT to "S",
    PsiType.BOOLEAN to "Z",
    PsiType.VOID to "V",
  )

  /**
   * @return - java encoding of the given PsiType.
   *
   * For example:
   * byte => "B"
   * String[][][] => "[[[Ljava/lang/String;"
   * ArrayList => "Ljava/util/ArrayList;"
   * void => "V"
   */
  @VisibleForTesting
  fun convertToString(psiType: PsiType): String {
    return when (val type = TypeConversionUtil.erasure(psiType)) {
      is PsiArrayType -> "[${convertToString(type.componentType)}"
      is PsiPrimitiveType -> primitiveTypes.getOrDefault(type, "")
      is PsiClassType -> "L${type.getCanonicalText().replace('.', '/')};"
      else -> ""
    }
  }

  /**
   * @return - signature of the given method encoded by java encoding and by applying type erasure.
   *
   * For example:
   * `int aMethod(List<String> a, ArrayList<T> b, boolean c, Integer[][] d)`
   * returns (Ljava/util/List;Ljava/util/ArrayList;Z[[Ljava/lang/Integer;)I
   */
  fun getTraceSignature(returnType: PsiType?, parameterTypes: Array<PsiType>): String {
    return if (returnType == null) {
      "(${parameterTypes.joinToString("") { convertToString(it) }})"
    } else {
      "(${parameterTypes.joinToString("") { convertToString(it) }})${convertToString(returnType)}"
    }
  }
}