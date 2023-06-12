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
package com.android.tools.idea.navigator;

import com.android.tools.idea.projectsystem.IdeaSourceProvider;
import com.android.tools.idea.projectsystem.NamedIdeaSourceProvider;
import com.android.tools.idea.projectsystem.SourceProviderManager;
import com.android.tools.idea.projectsystem.SourceProviders;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.intellij.ide.projectView.ProjectView;
import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.project.Project;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

public class AndroidViewNodes {

  public void refresh(@NotNull Project project) {
    ProjectView projectView = ProjectView.getInstance(project);
    AbstractProjectViewPane androidViewPane = projectView.getProjectViewPaneById(AndroidProjectViewPane.ID);
    assert androidViewPane != null;
    androidViewPane.updateFromRoot(true);
  }

  @NotNull
  public static Iterable<NamedIdeaSourceProvider> getSourceProviders(@NotNull AndroidFacet facet) {
    SourceProviders sourceProviderManager = SourceProviderManager.getInstance(facet);
    return getSourceProviders(sourceProviderManager);
  }

  @NotNull
  public static Iterable<NamedIdeaSourceProvider> getSourceProviders(@NotNull SourceProviders sourceProviders) {
    return Iterables.concat(
      sourceProviders.getCurrentSourceProviders(),
      sourceProviders.getCurrentUnitTestSourceProviders(),
      sourceProviders.getCurrentAndroidTestSourceProviders(),
      sourceProviders.getCurrentTestFixturesSourceProviders());
  }

  @NotNull
  public static Iterable<IdeaSourceProvider> getGeneratedSourceProviders(@NotNull SourceProviders sourceProviders) {
    return ImmutableList.of(
      sourceProviders.getGeneratedSources(),
      sourceProviders.getGeneratedUnitTestSources(),
      sourceProviders.getGeneratedAndroidTestSources(),
      sourceProviders.getGeneratedTestFixturesSources());
  }
}
