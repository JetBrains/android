/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.testing;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UseScopeOptimizer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Controls the search scope for finding usages of a PSI element.
 */
public class TestArtifactUseScopeOptimizer extends UseScopeOptimizer {
  @Nullable
  @Override
  public GlobalSearchScope getScopeToExclude(@NotNull PsiElement element) {
    VirtualFile file = findVirtualFile(element);
    if (file == null) {
      return null;
    }
    TestArtifactSearchScopes testScopes = TestArtifactSearchScopes.get(file, element.getProject());
    if (testScopes == null) {
      return null;
    }

    boolean inAndroidTest = testScopes.isAndroidTestSource(file);
    boolean inUnitTest = testScopes.isUnitTestSource(file);

    if (inAndroidTest && inUnitTest) {
      return null;
    }
    else if (inAndroidTest) {
      return testScopes.getUnitTestSourceScope();
    }
    if (inUnitTest) {
      return testScopes.getAndroidTestSourceScope();
    }
    return null;
  }

  @Nullable
  private static VirtualFile findVirtualFile(@NotNull PsiElement element) {
    PsiFile psiFile = element.getContainingFile();
    return psiFile != null ? psiFile.getVirtualFile() : null;
  }
}
