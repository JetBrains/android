/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.testartifacts.scopes;

import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.scope.packageSet.AbstractPackageSet;
import com.intellij.psi.search.scope.packageSet.CustomScopesProviderEx;
import com.intellij.psi.search.scope.packageSet.NamedScope;
import com.intellij.psi.search.scope.packageSet.NamedScopesHolder;
import com.intellij.ui.Colored;
import org.jetbrains.android.util.AndroidBundle;
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
    public static final String NAME = AndroidBundle.message("android.test.run.configuration.type.name");
    public AndroidTestsScope() {
      super(NAME, new AbstractPackageSet("test:*..*") {
        @Override
        public boolean contains(@Nullable VirtualFile file, @NotNull NamedScopesHolder holder) {
          return contains(file, holder.getProject(), holder);
        }
        @Override
        public boolean contains(@Nullable VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
          TestArtifactSearchScopes scopes = getTestArtifactSearchScopes(file, project);
          return scopes != null && scopes.isAndroidTestSource(file);
        }
      });
    }
  }

  /**
   * Scope that contains "local" (i.e. running on the JVM) unit tests from Android and Java modules.
   */
  @Colored(color = "e7fadb", darkVariant = "2A3B2C")
  public static class UnitTestsScope extends NamedScope {
    public static final String NAME = "Local Unit Tests";
    public UnitTestsScope() {
      super(NAME, new AbstractPackageSet("test:*..*") {
        @Override
        public boolean contains(@Nullable VirtualFile file, @NotNull NamedScopesHolder holder) {
          return contains(file, holder.getProject(), holder);
        }
        @Override
        public boolean contains(@Nullable VirtualFile file, @NotNull Project project, @Nullable NamedScopesHolder holder) {
          TestArtifactSearchScopes scopes = getTestArtifactSearchScopes(file, project);

          if (scopes != null) {
            // This is an Android project, only show unit tests.
            return scopes.isUnitTestSource(file);
          } else {
            // Otherwise (java module) show all tests.
            return file != null && TestSourcesFilter.isTestSources(file, project);
          }
        }
      });
    }
  }

  private static @Nullable TestArtifactSearchScopes getTestArtifactSearchScopes(@Nullable VirtualFile file, @NotNull Project project) {
    if (file == null) {
      return null;
    }
    Module module = FileIndexFacade.getInstance(project).getModuleForFile(file);
    if (module == null) {
      return null;
    }
    return TestArtifactSearchScopes.get(module);
  }
}
