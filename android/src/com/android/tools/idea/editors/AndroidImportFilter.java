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

import com.intellij.codeInsight.ImportFilter;
import org.jetbrains.annotations.NotNull;

import static com.android.SdkConstants.CLASS_R;

public class AndroidImportFilter extends ImportFilter {
  /** Never import android.R */
  @Override
  public boolean shouldUseFullyQualifiedName(@NotNull String classQualifiedName) {
    return classQualifiedName.startsWith(CLASS_R) &&
           (CLASS_R.length() == classQualifiedName.length() || classQualifiedName.charAt(CLASS_R.length()) == '.');
  }
}
