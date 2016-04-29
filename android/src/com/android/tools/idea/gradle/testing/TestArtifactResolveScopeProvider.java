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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.ResolveScopeProvider;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Extension to control the search scope of resolving a PSI element when multiple test artifacts are enabled simultaneously.
 * For a PSI element inside a unit test, it returns the (module scope) - (the android test source/library scope) and vice versa.
 */
public class TestArtifactResolveScopeProvider extends ResolveScopeProvider {
  @Nullable
  @Override
  public GlobalSearchScope getResolveScope(@NotNull VirtualFile file, @Nullable Project project) {
    if (project == null) {
      return null;
    }
    TestArtifactSearchScopes testScopes = TestArtifactSearchScopes.get(file, project);
    if (testScopes == null) {
      return null;
    }

    GlobalSearchScope scope = GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(testScopes.getModule(), true);
    GlobalSearchScope excludedScope;

    boolean inAndroidTest = testScopes.isAndroidTestSource(file);
    boolean inUnitTest = testScopes.isUnitTestSource(file);

    if (inAndroidTest && inUnitTest) {
      excludedScope = testScopes.getSharedTestsExcludeScope();
    }
    else if (inAndroidTest) {
      excludedScope = testScopes.getAndroidTestExcludeScope();
    }
    else if (inUnitTest) {
      excludedScope = testScopes.getUnitTestExcludeScope();
    }
    else {
      return null;
    }
    return scope.intersectWith(GlobalSearchScope.notScope(excludedScope));
  }
}
