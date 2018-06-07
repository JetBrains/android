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
package com.android.tools.idea.gradle.project.sync;

import com.android.builder.model.AndroidProject;
import com.android.builder.model.level2.Library;
import com.android.ide.common.gradle.model.IdeAndroidProject;
import com.android.ide.common.gradle.model.IdeAndroidProjectImpl;
import com.android.ide.common.gradle.model.level2.IdeDependencies;
import com.android.tools.idea.Projects;
import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.android.tools.idea.gradle.util.GradleWrapper;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.AndroidGradleTests;
import com.android.tools.idea.testing.TestProjectPaths;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link IdeAndroidProjectImpl}.
 */
public class IdeAndroidProjectIntegrationTest extends AndroidGradleTestCase {
  public void testSyncFromCachedModel() throws Exception {
    loadSimpleApplication();

    AndroidProject androidProject = getAndroidProjectInApp();
    // Verify AndroidProject was copied.
    assertThat(androidProject).isInstanceOf(IdeAndroidProjectImpl.class);

    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.useCachedGradleModels = true;
    SyncListener syncListener = requestSync(request);
    assertTrue(syncListener.isSyncSkipped());

    AndroidProject cached = getAndroidProjectInApp();
    // Verify AndroidProject was deserialized.
    assertThat(cached).isInstanceOf(IdeAndroidProjectImpl.class);

    assertEquals(androidProject, cached);
  }

  public void ignore_testSyncWithGradle2Dot2() throws Exception {
    syncProjectWithGradle2Dot2();

    AndroidProject androidProject = getAndroidProjectInApp();
    // Verify AndroidProject was copied.
    assertThat(androidProject).isInstanceOf(IdeAndroidProjectImpl.class);
  }

  private void syncProjectWithGradle2Dot2() throws Exception {
    syncProjectWithGradle2Dot2(TestProjectPaths.SIMPLE_APPLICATION);
  }

  private void syncProjectWithGradle2Dot2(@NotNull String projectName) throws Exception {
    loadProject(projectName);
    Project project = getProject();
    AndroidGradleTests.updateGradleVersions(Projects.getBaseDirPath(project), "2.2.0");
    GradleWrapper wrapper = GradleWrapper.find(project);
    assertNotNull(wrapper);
    wrapper.updateDistributionUrl("3.5");

    GradleSyncInvoker.Request request = GradleSyncInvoker.Request.projectModified();
    request.generateSourcesOnSuccess = false;
    request.skipAndroidPluginUpgrade = true;
    requestSyncAndWait(request);
  }

  public void ignore_testLevel2DependenciesWithGradle2Dot2() throws Exception {
    syncProjectWithGradle2Dot2();
    verifyIdeLevel2DependenciesPopulated();
  }

  public void testLevel2DependenciesWithHeadPlugin() throws Exception {
    loadSimpleApplication();
    verifyIdeLevel2DependenciesPopulated();
  }

  private void verifyIdeLevel2DependenciesPopulated() {
    IdeAndroidProject androidProject = getAndroidProjectInApp();
    assertNotNull(androidProject);

    // Verify IdeLevel2Dependencies are populated for each variant.
    androidProject.forEachVariant(variant -> {
      IdeDependencies level2Dependencies = variant.getMainArtifact().getLevel2Dependencies();
      assertThat(level2Dependencies).isNotNull();
      assertThat(level2Dependencies.getAndroidLibraries()).isNotEmpty();
      assertThat(level2Dependencies.getJavaLibraries()).isNotEmpty();
    });
  }

  // TODO: Enable when plugin 2.2 is added to prebuilts.
  public void ignore_testLocalAarsAsModulesWithGradle2Dot2() throws Exception {
    syncProjectWithGradle2Dot2(TestProjectPaths.LOCAL_AARS_AS_MODULES);
    verifyAarModuleShowsAsAndroidLibrary("testLocalAarsAsModulesWithGradle2Dot2:library-debug:unspecified@aar");
  }

  public void testLocalAarsAsModulesWithHeadPlugin() throws Exception {
    loadProject(TestProjectPaths.LOCAL_AARS_AS_MODULES);
    verifyAarModuleShowsAsAndroidLibrary("artifacts:library-debug:unspecified@jar");
  }

  private void verifyAarModuleShowsAsAndroidLibrary(String expectedLibraryName) {
    IdeAndroidProject androidProject = getAndroidProjectInApp();
    assertNotNull(androidProject);

    // Aar module should show up as android library dependency, not module dependency for app module.
    androidProject.forEachVariant(variant -> {
      IdeDependencies level2Dependencies = variant.getMainArtifact().getLevel2Dependencies();
      assertThat(level2Dependencies).isNotNull();
      assertThat(level2Dependencies.getModuleDependencies()).isEmpty();
      List<String> androidLibraries =
        level2Dependencies.getAndroidLibraries().stream().map(Library::getArtifactAddress).collect(Collectors.toList());
      assertThat(level2Dependencies.getAndroidLibraries()).isNotEmpty();
      assertThat(androidLibraries).contains(expectedLibraryName);
    });
  }

  @Nullable
  private IdeAndroidProject getAndroidProjectInApp() {
    Module appModule = myModules.getAppModule();
    AndroidModuleModel androidModel = AndroidModuleModel.get(appModule);
    return androidModel != null ? androidModel.getAndroidProject() : null;
  }
}
