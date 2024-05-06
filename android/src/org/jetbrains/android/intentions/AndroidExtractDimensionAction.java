/*
 * Copyright (C) 2013 The Android Open Source Project
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
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

public class AndroidExtractDimensionAction extends AndroidAddStringResourceAction {
  @Override
  @NotNull
  public String getText() {
    return AndroidBundle.message("extract.dimension.intention.text");
  }

  @Override
  protected ResourceType getType() {
    return ResourceType.DIMEN;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    // We don't support extracting dimensions in Java files yet. Unlike strings, the
    // presence of a number doesn't necessarily imply that it's a dimension -- it could
    // be a color integer, it could be a loop index, it could be a bitmask, ... And
    // even if it's a dimension, it's not clear how we would convert it from a pixel
    // (which is used in most APIs) to a dimension, since we shouldn't put px resources
    // into resource files; they should be dips, but of course the pixel to dp depends
    // on the screen dpi.
    if (file.getFileType() == XmlFileType.INSTANCE) {
      AndroidFacet facet = AndroidFacet.getInstance(file);
      if (facet != null) {
        PsiElement element = getPsiElement(file, editor);
        if (element != null) {
          String value = getStringLiteralValue(project, element, file, getType());
          if (value != null && !value.isEmpty()) {
            // Only allow conversions on numbers (e.g. "50px") such that we don't
            // offer to extract @dimen/foo or wrap_content, and also only allow
            // conversions on dimensions (numbers followed by a unit) so we don't
            // for example offer to extract an integer into a dimension
            if (Character.isDigit(value.charAt(0)) && !Character.isDigit(value.charAt(value.length() - 1))) {
              // Only allow conversions on dimensions (numbers with units)
              return true;
            }
          }
        }
      }
    }

    return false;
  }
}
