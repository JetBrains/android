// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.facet;

import com.intellij.facet.ProjectFacetListener;
import org.jetbrains.android.compiler.AndroidAutogeneratorMode;
import org.jetbrains.android.compiler.ModuleSourceAutogenerating;
import org.jetbrains.annotations.NotNull;

public class AndroidModuleAutogenerationListener implements ProjectFacetListener<AndroidFacet> {
  @Override
  public void facetConfigurationChanged(@NotNull AndroidFacet facet) {
    String aidlGenOutputPath = facet.getConfiguration().getState().GEN_FOLDER_RELATIVE_PATH_AIDL;
    String prevAidlGenOutputPath = AndroidFacetEditorTab.PREV_AIDL_GEN_OUTPUT_PATH.get(facet, aidlGenOutputPath);

    if (!aidlGenOutputPath.equals(prevAidlGenOutputPath)) {
      ModuleSourceAutogenerating sourceAutoGenerator = ModuleSourceAutogenerating.getInstance(facet);

      if (sourceAutoGenerator != null) {
        sourceAutoGenerator.scheduleSourceRegenerating(AndroidAutogeneratorMode.AIDL);
      }
    }
  }
}
