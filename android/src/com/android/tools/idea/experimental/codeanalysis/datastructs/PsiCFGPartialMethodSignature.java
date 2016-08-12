/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.experimental.codeanalysis.datastructs;

import com.intellij.psi.PsiType;

public class PsiCFGPartialMethodSignature {

  private static final int BIG_PRIME = 536870923;
  public String methodName;

  public PsiType[] parameterTypes;

  public boolean isVarArgs;

  @Override
  public int hashCode() {
    int retHash = 0;
    retHash += methodName.hashCode();
    if (parameterTypes != null) {
      //Calculate the hash
      for (PsiType curType : parameterTypes) {
        retHash = retHash % BIG_PRIME;
        retHash *= 3;
        retHash += curType.hashCode();
      }
    }

    //Calculate the hash
    if (isVarArgs) {
      retHash >>= 1;
      retHash = retHash % BIG_PRIME;
      retHash *= 3;
    }
    return retHash;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || (!(obj instanceof PsiCFGPartialMethodSignature))) {
      return false;
    }
    PsiCFGPartialMethodSignature o = (PsiCFGPartialMethodSignature)obj;
    if (!methodName.equals(o.methodName)) {
      return false;
    }

    if (isVarArgs != o.isVarArgs) {
      return false;
    }

    if (parameterTypes == null && o.parameterTypes == null) {
      return true;
    }

    if (parameterTypes == null || o.parameterTypes == null) {
      return false;
    }

    if (parameterTypes.length != o.parameterTypes.length) {
      return false;
    }

    for (int i = 0; i < parameterTypes.length; i++) {
      if (!parameterTypes[i].equals(o.parameterTypes[i])) {
        return false;
      }
    }
    return true;
  }
}
