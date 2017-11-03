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
import com.intellij.psi.PsiFile;
import com.siyeh.ig.migration.TryWithIdenticalCatchesInspection;
import org.jetbrains.annotations.NotNull;

/** Subclass which makes the parent inspection only apply for API level 19 or higher */
public class AndroidTryWithIdenticalCatchesInspection extends TryWithIdenticalCatchesInspection {
  @Override
  public boolean shouldInspect(PsiFile file) {
    // Default true: plain Java module: use IDE language level instead
    return LintIdeUtils.isApiLevelAtLeast(file, 19, true) && super.shouldInspect(file);
  }

  /**
   * Override the short name registration since it can't be deduced from the classname anymore
   * (we've added the prefix Android)
   */
  @NotNull
  @Override
  public String getShortName() {
    return "TryWithIdenticalCatches";
  }
}
