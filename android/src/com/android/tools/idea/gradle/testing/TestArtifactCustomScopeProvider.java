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

import com.google.common.collect.ImmutableList;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.AbstractPackageSet;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.Colored;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provide two custom {@link NamedScope}s that can be to define the background of a file (https://www.jetbrains.com/idea/help/scope.html)
 * that shows in project tree, file tab, search result, stacktrace, etc:
 * <ul>
 *   <li>AndroidTestsScope: includes android test files</li>
 *   <li>UnitTestsScope: includes unit tests file if android tests exists, otherwise include all tests file
 *       just as {@link com.intellij.psi.search.scope.TestsScope}
 *   </li>
 * </ul>
 *
 */
public class TestArtifactCustomScopeProvider extends CustomScopesProviderEx {
  private static List<NamedScope> SCOPES = ImmutableList.of(new UnitTestsScope(), new AndroidTestsScope());

  @NotNull
  @Override
  public List<NamedScope> getCustomScopes() {
    return SCOPES;
  }

  @Override
  public boolean isVetoed(@NotNull NamedScope scope, @NotNull ScopePlace place) {
    // We want the scopes provided act as built-in scopes so people can't customize those scopes in
    // Setting -> Appearance & Behavior -> Scopes
    if (scope instanceof AndroidTestsScope && place == CustomScopesProviderEx.ScopePlace.ACTION) {
      return true;
    }
    return false;
  }

  @Colored(color = "ffffe4", darkVariant = "494539")
  public static class AndroidTestsScope extends NamedScope {
    public static final String NAME = "Android Instrumentation Tests";
    public AndroidTestsScope() {
      super(NAME, new AbstractPackageSet("test:*..*") {
        @Override
        public boolean contains(@Nullable VirtualFile file, @NotNull NamedScopesHolder holder) {
          return contains(file, holder.getProject(), holder);
        }
        @Override
        public boolean contains(@Nullable VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
          if (file == null) {
            return false;
          }
          Module module = FileIndexFacade.getInstance(project).getModuleForFile(file);
          if (module == null) {
            return false;
          }
          TestArtifactSearchScopes scopes = TestArtifactSearchScopes.get(module);
          if (scopes == null) {
            return false;
          }
          return scopes.isAndroidTestSource(file);
        }
      });
    }
  }

  @Colored(color = "e7fadb", darkVariant = "2A3B2C")
  public static class UnitTestsScope extends NamedScope {
    // Use same name as {@code TestsScope} to override it.
    public static final String NAME = IdeBundle.message("predefined.scope.tests.name");
    public UnitTestsScope() {
      super(NAME, new AbstractPackageSet("test:*..*") {
        @Override
        public boolean contains(@Nullable VirtualFile file, @NotNull NamedScopesHolder holder) {
          return contains(file, holder.getProject(), holder);
        }
        @Override
        public boolean contains(@Nullable VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
          if (file == null) {
            return false;
          }
          Module module = FileIndexFacade.getInstance(project).getModuleForFile(file);
          if (module == null) {
            return false;
          }
          TestArtifactSearchScopes scopes = TestArtifactSearchScopes.get(module);
          if (scopes == null) {
            return ProjectRootManager.getInstance(project).getFileIndex().isInTestSourceContent(file);
          }
          return scopes.isUnitTestSource(file);
        }
      });
    }
  }
}
