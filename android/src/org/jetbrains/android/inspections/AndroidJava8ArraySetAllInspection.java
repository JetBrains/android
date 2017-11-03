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
package org.jetbrains.android.inspections;

import com.android.tools.idea.lint.LintIdeUtils;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.java18api.Java8ArraySetAllInspection;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

/** Subclass which makes the parent inspection only apply for API level 24 or higher */
public class AndroidJava8ArraySetAllInspection extends Java8ArraySetAllInspection {
  @NotNull
  @Override
  public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
    // java.util.Arrays.setAll requires API 24
    if (!LintIdeUtils.isApiLevelAtLeast(holder.getFile(), 24, true)) {
      return PsiElementVisitor.EMPTY_VISITOR;
    }

    return super.buildVisitor(holder, isOnTheFly);
  }

  /**
   * Override the short name registration since it can't be deduced from the classname anymore
   * (we've added the prefix Android)
   */
  @NotNull
  @Override
  public String getShortName() {
    return "Java8ArraySetAll";
  }
}