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
package com.android.tools.idea.gradle.project;

import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.project.facet.gradle.GradleFacet;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.testFramework.IdeaTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import static com.android.builder.model.AndroidProject.*;
import static com.android.ide.common.repository.GradleVersion.parse;
import static com.android.tools.idea.testing.Facets.createAndAddAndroidFacet;
import static com.android.tools.idea.testing.Facets.createAndAddGradleFacet;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ProjectStructure}.
 */
public class ProjectStructureTest extends IdeaTestCase {
  private ProjectStructure myProjectStructure;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProjectStructure = new ProjectStructure(getProject());
  }

  public void testAnalyzeProjectStructure() {
    // Set up modules in the project: 1 Android app, 1 Instant App, 1 Android library and 1 Java library.
    Module appModule = createAndroidModule("app", "3.0", PROJECT_TYPE_APP);
    Module instantAppModule = createAndroidModule("instantApp", "3.1", PROJECT_TYPE_INSTANTAPP);
    createAndroidModule("androidLib", "2.3.1", PROJECT_TYPE_LIBRARY);
    createGradleModule("javaLib");

    // Method to test:
    myProjectStructure.analyzeProjectStructure(new EmptyProgressIndicator());

    // Verify that the app modules where properly identified.
    ImmutableList<Module> appModules = myProjectStructure.getAppModules();
    assertThat(appModules).containsAllOf(appModule, instantAppModule);

    ProjectStructure.AndroidPluginVersionsInProject agpPluginVersions = myProjectStructure.getAndroidPluginVersions();
    assertFalse(agpPluginVersions.isEmpty());

    // Verify that the AGP versions were recorded correctly.
    ImmutableMap<String, GradleVersion> internalMap = agpPluginVersions.getInternalMap();
    assertThat(internalMap).containsEntry(":app", GradleVersion.parse("3.0"));
    assertThat(internalMap).containsEntry(":instantApp", GradleVersion.parse("3.1"));
    assertThat(internalMap).containsEntry(":androidLib", GradleVersion.parse("2.3.1"));
    assertThat(internalMap).doesNotContainKey(":javaLib");
  }

  @NotNull
  private Module createAndroidModule(@NotNull String name, @NotNull String pluginVersion, int projectType) {
    Module module = createGradleModule(name);
    setUpAsAndroidModule(module, pluginVersion, projectType);
    return module;
  }

  @NotNull
  private Module createGradleModule(@NotNull String name) {
    Module module = createModule(name);
    GradleFacet gradleFacet = createAndAddGradleFacet(module);
    gradleFacet.getConfiguration().GRADLE_PROJECT_PATH = ":" + name;
    return module;
  }

  private static void setUpAsAndroidModule(@NotNull Module module, @NotNull String pluginVersion, int projectType) {
    AndroidFacet androidFacet = createAndAddAndroidFacet(module);

    AndroidModuleModel androidModel = mock(AndroidModuleModel.class);
    androidFacet.setAndroidModel(androidModel);

    IdeAndroidProject androidProject = mock(IdeAndroidProject.class);
    when(androidProject.getProjectType()).thenReturn(projectType);

    when(androidModel.getAndroidProject()).thenReturn(androidProject);
    when(androidModel.getModelVersion()).thenReturn(parse(pluginVersion));
  }
}