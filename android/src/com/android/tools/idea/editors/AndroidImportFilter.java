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
package com.android.tools.idea.editors;

import com.android.resources.ResourceType;
import com.intellij.codeInsight.ImportFilter;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.android.SdkConstants.CLASS_R;

public class AndroidImportFilter extends ImportFilter {

  /** Never import android.R, or inner classes of application R or android.R classes */
  @Override
  public boolean shouldUseFullyQualifiedName(@Nullable PsiFile targetFile, @NotNull String classQualifiedName) {
    if (classQualifiedName.endsWith(".R")) {
      return CLASS_R.equals(classQualifiedName);
    }
    int index = classQualifiedName.lastIndexOf('.');
    if (index > 2 && classQualifiedName.charAt(index - 1) == 'R' && classQualifiedName.charAt(index - 2) == '.') {
      // Only accept R inner classes that look like they really are resource classes, e.g.
      //  foo.bar.R.string and foo.bar.R.layout, but not my.weird.R.pkg
      return classQualifiedName.startsWith(CLASS_R) || ResourceType.getEnum(classQualifiedName.substring(index + 1)) != null;
    }

    return false;
  }
}
