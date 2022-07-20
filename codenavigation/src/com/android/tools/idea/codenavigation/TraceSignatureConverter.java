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
package com.android.tools.idea.codenavigation;

import com.google.common.collect.ImmutableMap;
import com.intellij.psi.PsiArrayType;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.TypeConversionUtil;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

public final class TraceSignatureConverter {
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
   * <p>
   * For example:
   * byte => "B"
   * String[][][] => "[[[Ljava/lang/String;"
   * ArrayList => "Ljava/util/ArrayList;"
   * void => "V"
   */
  @Nullable
  @VisibleForTesting
  public static String convertToString(@NotNull PsiType psiType) {
    PsiType sanitizedType = TypeConversionUtil.erasure(psiType);

    if (sanitizedType instanceof PsiArrayType) {
      return '[' + convertToString(((PsiArrayType)sanitizedType).getComponentType());
    }
    else if (sanitizedType instanceof PsiPrimitiveType) {
      return String.valueOf(PRIMITIVE_TYPES.get(sanitizedType));
    }
    else if (sanitizedType instanceof PsiClassType) {
      return "L" + sanitizedType.getCanonicalText().replace('.', '/') + ";";
    }
    return null;
  }

  /**
   * @return - signature of the given method encoded by java encoding and by applying type erasure.
   * <p>
   * For example:
   * {@code int aMethod(List<String> a, ArrayList<T> b, boolean c, Integer[][] d)}
   * returns (Ljava/util/List;Ljava/util/ArrayList;Z[[Ljava/lang/Integer;)I
   */
  @NotNull
  public static String getTraceSignature(@Nullable PsiType returnType, @NotNull PsiType[] parameterTypes) {
    StringBuilder signature = new StringBuilder("(");
    for (PsiType type : parameterTypes) {
      signature.append(convertToString(type));
    }
    signature.append(")");

    if (returnType != null) {
      signature.append(convertToString(returnType));
    }
    return signature.toString();
  }
}
