/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.google.common.collect.ImmutableMap;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class TraceSignatureConverter  {
  /**
   * Mapping from java primitive type encoding to PsiPrimitiveType
   * More about java primitive type encoding: https://docs.oracle.com/javase/7/docs/api/java/lang/Class.html#getName()
   */
  private static final Map<PsiType, Character> PRIMITIVE_TYPES =
    new ImmutableMap.Builder<PsiType, Character>()
      .put(PsiType.BYTE, 'B')
      .put(PsiType.CHAR, 'C')
      .put(PsiType.DOUBLE, 'D')
      .put(PsiType.FLOAT, 'F')
      .put(PsiType.INT, 'I')
      .put(PsiType.LONG, 'J')
      .put(PsiType.SHORT, 'S')
      .put(PsiType.BOOLEAN, 'Z')
      .put(PsiType.VOID, 'V').build();

  /**
   * @return - java encoding of the given PsiType.
   *
   * For example:
   * byte => "B"
   * String[][][] => "[[[Ljava/lang/String;"
   * ArrayList => "Ljava/util/ArrayList;"
   * void => "V"
   */
  @Nullable
  private static String convertToString(@NotNull PsiType psiType) {
    if (psiType instanceof PsiArrayType) {
      return '[' + convertToString(((PsiArrayType)psiType).getComponentType());
    }
    else if (psiType instanceof PsiPrimitiveType) {
      return String.valueOf(PRIMITIVE_TYPES.get(psiType));
    }
    else if (psiType instanceof PsiClassType) {
      return "L" + psiType.getCanonicalText().replaceAll("\\.", "/") + ";";
    }
    return null;
  }

  /**
   * @return - signature of the given method encoded by java encoding and by applying type erasure.
   *
   * For example:
   * {@code int aMethod(List<String> a, ArrayList<T> b, boolean c, Integer[][] d)}
   * returns (Ljava/util/List;Ljava/util/ArrayList;Z[[Ljava/lang/Integer;)I
   */
  @NotNull
  public static String getTraceSignature(@NotNull PsiMethod method) {
    StringBuilder signature = new StringBuilder("(");
    for (PsiType type : method.getSignature(PsiSubstitutor.EMPTY).getParameterTypes()) {
      String converted = convertToString(TypeConversionUtil.erasure(type));
      signature.append(converted);
    }
    signature.append(")");

    PsiType returnType = method.getReturnType();
    if (returnType != null) {
      String converted = convertToString(TypeConversionUtil.erasure(returnType));
      signature.append(converted);
    }
    return signature.toString();
  }
}
