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
package com.android.tools.idea.mlkit.lightpsi;

import static com.intellij.psi.CommonClassNames.JAVA_UTIL_LIST;

import com.android.tools.mlkit.TensorInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Utility methods for generating light class.
 */
public final class CodeUtils {

  /**
   * Gets {@link PsiClassType} based on {@link TensorInfo}
   *
   * If {@param generateFallbackApiOnly} is true, always return generic {@code TensorBuffer}. This implies the underlying AGP doesn't
   * fully support this model.
   */
  @NotNull
  public static PsiClassType getPsiClassType(@NotNull TensorInfo tensorInfo,
                                             @NotNull Project project,
                                             @NotNull GlobalSearchScope scope,
                                             boolean generateFallbackApiOnly) {
    if (generateFallbackApiOnly) {
      return PsiType.getTypeByName(ClassNames.TENSOR_BUFFER, project, scope);
    }
    if (tensorInfo.isRGBImage()) {
      // Only RGB image is supported right now.
      return PsiType.getTypeByName(ClassNames.TENSOR_IMAGE, project, scope);
    }
    else if (tensorInfo.getFileType() == TensorInfo.FileType.TENSOR_AXIS_LABELS) {
      // Returns List<Category>.
      PsiClass listClass = JavaPsiFacade.getInstance(project).findClass(JAVA_UTIL_LIST, scope);
      PsiType value = PsiType.getTypeByName(ClassNames.CATEGORY, project, scope);
      return PsiElementFactory.getInstance(project).createType(listClass, value);
    }
    else {
      return PsiType.getTypeByName(ClassNames.TENSOR_BUFFER, project, scope);
    }
  }

  /**
   * Gets type name from {@link PsiType}.
   *
   * <p>If it has parameters, add parameter name to type name. So {@code List<Category>} will become {@code CategoryList}.
   */
  @NotNull
  public static String getTypeName(@NotNull PsiType psiType) {
    if (psiType instanceof PsiClassType) {
      PsiClassType psiClassType = (PsiClassType)psiType;
      PsiType[] psiTypes = psiClassType.getParameters();
      if (psiTypes.length == 0) {
        return psiClassType.getClassName();
      }
      else {
        StringBuilder stringBuilder = new StringBuilder();
        for (PsiType paramType : psiTypes) {
          stringBuilder.append(paramType.getPresentableText());
        }
        stringBuilder.append(psiClassType.getClassName());
        return stringBuilder.toString();
      }
    }
    else {
      return psiType.getPresentableText();
    }
  }
}
