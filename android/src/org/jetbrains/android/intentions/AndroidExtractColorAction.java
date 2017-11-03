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
package org.jetbrains.android.intentions;

import com.android.resources.ResourceType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class AndroidExtractColorAction extends AndroidAddStringResourceAction {
  @Override
  @NotNull
  public String getText() {
    return "Extract color resource";
  }

  @Override
  protected ResourceType getType() {
    return ResourceType.COLOR;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (file.getFileType() == StdFileTypes.XML) {
      AndroidFacet facet = AndroidFacet.getInstance(file);
      if (facet != null) {
        PsiElement element = getPsiElement(file, editor);
        if (element != null) {
          String value = getStringLiteralValue(project, element, file, getType());
          if (value != null && value.length() >= 4 && value.length() <= 9 && '#' == value.charAt(0) && isHexString(value.substring(1))) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isHexString(final String s) {
    for (int i = 0; i < s.length(); i++) {
      if (!StringUtil.isHexDigit(s.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}
