/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng;

import com.android.tools.idea.gradle.util.GradleProjectSettingsFinder;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.GradleProject;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.android.tools.idea.gradle.project.sync.ng.ModuleNameGenerator.deduplicateModuleNames;
import static com.android.tools.idea.gradle.project.sync.ng.ModuleNameGenerator.getModuleNameByModulePath;
import static com.google.common.truth.Truth.assertThat;
import static java.util.Collections.emptySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ModuleNameGenerator}.
 */
public class ModuleNameGeneratorTest extends AndroidTestCase {
  public void testDeduplicateModuleNamesWithoutUseQualifiedName() {
    // Do not use qualified module names.
    setUseQualifiedModuleNames(getProject(), false);
    SyncProjectModels projectModels = createProjectModels();
    deduplicateModuleNames(projectModels, getProject());
    Set<String> names = projectModels.getModuleModels().stream().map(SyncModuleModels::getModuleName).collect(Collectors.toSet());

    // Verify module names are unique, and correct path prefix were added for deduplication.
    assertThat(names.size()).isEqualTo(projectModels.getModuleModels().size());
    assertThat(names).containsExactly("project1-app", "project1-nested1-lib", "project1-nested2-lib",
                                      "project2-app", "project2-nested1-lib", "project2-nested2-lib");
  }

  public void testDeduplicateModuleNamesWithUseQualifiedName() {
    // Use qualified module names.
    setUseQualifiedModuleNames(getProject(), true);
    SyncProjectModels projectModels = createProjectModels();
    deduplicateModuleNames(projectModels, getProject());
    Set<String> names = projectModels.getModuleModels().stream().map(SyncModuleModels::getModuleName).collect(Collectors.toSet());

    // Verify module names are unique, and correct path prefix were added for deduplication.
    assertThat(names.size()).isEqualTo(projectModels.getModuleModels().size());
    assertThat(names).containsExactly("project1.app", "project1.nested1.lib", "project1.nested2.lib",
                                      "project2.app", "project2.nested1.lib", "project2.nested2.lib");
  }

  public void testGetModuleNameByModulePath() {
    Collection<String> modulePaths = Arrays.asList(
      "/tmp/project1/app",
      "/tmp/project1/nested1/lib",
      "/tmp/project1/nested2/lib",
      "/tmp/project2/app",
      "/tmp/project2/nested1/lib",
      "/tmp/project2/nested2/lib");
    // Do not use qualified module names.
    setUseQualifiedModuleNames(getProject(), false);

    Map<String, String> names = getModuleNameByModulePath(modulePaths, getProject());
    // Verify module names are unique, and correct path prefix were added for deduplication.
    assertThat(names.get("/tmp/project1/app")).isEqualTo("project1-app");
    assertThat(names.get("/tmp/project1/nested1/lib")).isEqualTo("project1-nested1-lib");
    assertThat(names.get("/tmp/project1/nested2/lib")).isEqualTo("project1-nested2-lib");
    assertThat(names.get("/tmp/project2/app")).isEqualTo("project2-app");
    assertThat(names.get("/tmp/project2/nested1/lib")).isEqualTo("project2-nested1-lib");
    assertThat(names.get("/tmp/project2/nested2/lib")).isEqualTo("project2-nested2-lib");

    // Use qualified module names.
    setUseQualifiedModuleNames(getProject(), true);
    names = getModuleNameByModulePath(modulePaths, getProject());
    // Verify module names are unique, and correct path prefix were added for deduplication.
    assertThat(names.get("/tmp/project1/app")).isEqualTo("project1.app");
    assertThat(names.get("/tmp/project1/nested1/lib")).isEqualTo("project1.nested1.lib");
    assertThat(names.get("/tmp/project1/nested2/lib")).isEqualTo("project1.nested2.lib");
    assertThat(names.get("/tmp/project2/app")).isEqualTo("project2.app");
    assertThat(names.get("/tmp/project2/nested1/lib")).isEqualTo("project2.nested1.lib");
    assertThat(names.get("/tmp/project2/nested2/lib")).isEqualTo("project2.nested2.lib");
  }

  private void setUseQualifiedModuleNames(@NotNull Project project, boolean useQualifiedModuleNames) {
    GradleProjectSettingsFinder finder = IdeComponents.mockApplicationService(GradleProjectSettingsFinder.class, getTestRootDisposable());
    GradleProjectSettings settings = mock(GradleProjectSettings.class);
    when(finder.findGradleProjectSettings(project)).thenReturn(settings);
    when(settings.isUseQualifiedModuleNames()).thenReturn(useQualifiedModuleNames);
  }

  @NotNull
  private static SyncProjectModels createProjectModels() {
    SyncProjectModels projectModels = mock(SyncProjectModels.class);
    List<SyncModuleModels> moduleModels = Arrays.asList(createModuleModels("app", "/tmp/project1/app"),
                                                        createModuleModels("lib", "/tmp/project1/nested1/lib"),
                                                        createModuleModels("lib", "/tmp/project1/nested2/lib"),
                                                        createModuleModels("app", "/tmp/project2/app"),
                                                        createModuleModels("lib", "/tmp/project2/nested1/lib"),
                                                        createModuleModels("lib", "/tmp/project2/nested2/lib"));
    when(projectModels.getModuleModels()).thenReturn(moduleModels);
    return projectModels;
  }

  @NotNull
  private static SyncModuleModels createModuleModels(@NotNull String name, @NotNull String projectPath) {
    GradleProject gradleProject = mock(GradleProject.class);
    when(gradleProject.getProjectDirectory()).thenReturn(new File(projectPath));
    when(gradleProject.getName()).thenReturn(name);
    SyncModuleModels moduleModels =
      new SyncModuleModels(gradleProject, mock(BuildIdentifier.class), emptySet(), emptySet(), new SyncActionOptions());
    moduleModels.addModel(GradleProject.class, gradleProject);
    return moduleModels;
  }
}
