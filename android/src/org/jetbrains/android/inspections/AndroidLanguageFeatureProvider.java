// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.android.inspections;

import com.android.tools.idea.lint.LintIdeUtils;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageFeatureProvider;
import com.intellij.psi.PsiFile;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;

public class AndroidLanguageFeatureProvider implements LanguageFeatureProvider {
  @NotNull
  @Override
  public ThreeState isFeatureSupported(@NotNull JavaFeature feature, @NotNull PsiFile file) {
    if (JavaFeature.MULTI_CATCH == feature) {
      return ThreeState.fromBoolean(LintIdeUtils.isApiLevelAtLeast(file, 19, true));
    }
    else if (JavaFeature.STREAMS == feature || JavaFeature.ADVANCED_COLLECTIONS_API == feature) {
      return ThreeState.fromBoolean(LintIdeUtils.isApiLevelAtLeast(file, 24, true));
    }
    else if (JavaFeature.THREAD_LOCAL_WITH_INITIAL == feature) {
      return ThreeState.fromBoolean(LintIdeUtils.isApiLevelAtLeast(file, 26, true));
    }
    return ThreeState.UNSURE;
  }
}
