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

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiType;

public class PsiCFGPartialMethodSignatureBuilder {
  public static PsiCFGPartialMethodSignature buildFromPsiMethod(PsiMethod psiMethod) {
    PsiCFGPartialMethodSignature retSignature = new PsiCFGPartialMethodSignature();
    retSignature.methodName = psiMethod.getName().trim();
    if (psiMethod.isVarArgs()) {
      retSignature.isVarArgs = true;
    }

    PsiParameter[] paramList = psiMethod.getParameterList().getParameters();
    if (paramList == null || paramList.length == 0) {
      retSignature.parameterTypes = PsiType.EMPTY_ARRAY;
    }
    else {
      retSignature.parameterTypes = new PsiType[paramList.length];
      for (int i = 0; i < paramList.length; i++) {
        PsiType curParamType = paramList[i].getType();
        retSignature.parameterTypes[i] = curParamType;
      }
    }
    return retSignature;
  }

  public static PsiCFGPartialMethodSignature buildFromScratch(String name, PsiType[] paramTypes, boolean isVarArgs) {
    PsiCFGPartialMethodSignature retSignature = new PsiCFGPartialMethodSignature();
    retSignature.methodName = name.trim();
    retSignature.isVarArgs = isVarArgs;

    if (paramTypes == null || paramTypes.length == 0) {
      retSignature.parameterTypes = PsiType.EMPTY_ARRAY;
    }
    else {
      retSignature.parameterTypes = paramTypes.clone();
    }

    return retSignature;
  }

}
