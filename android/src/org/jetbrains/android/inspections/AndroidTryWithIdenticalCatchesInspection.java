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

import com.android.tools.idea.model.AndroidModuleInfo;
import com.intellij.psi.PsiFile;
import com.siyeh.ig.migration.TryWithIdenticalCatchesInspection;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidTryWithIdenticalCatchesInspection extends TryWithIdenticalCatchesInspection {
  @Override
  public boolean shouldInspect(PsiFile file) {
    AndroidFacet facet = AndroidFacet.getInstance(file);
    if (facet != null && !facet.isDisposed()) {
      AndroidModuleInfo info = AndroidModuleInfo.getInstance(facet);
      return info.getMinSdkVersion().getApiLevel() >= 19;
    }

    // Plain Java module: use IDE language level instead
    return super.shouldInspect(file);
  }

  @NotNull
  @Override
  public String getShortName() {
    return "TryWithIdenticalCatches";
  }
}
