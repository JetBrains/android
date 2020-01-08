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

import com.android.tools.idea.mlkit.MlkitUtils;
import com.android.tools.mlkit.Param;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;

/**
 * Utility method to generate light class.
 */
public class CodeUtils {

  /**
   * Get qualified type name based on {@link Param}
   */
  @NotNull
  public static String getTypeQualifiedName(@NotNull Param param) {
    if (param.getContentType() == Param.ContentType.IMAGE) {
      return ClassNames.VISION_IMAGE;
    } else {
      return ClassNames.BYTE_BUFFER;
    }
  }

}
