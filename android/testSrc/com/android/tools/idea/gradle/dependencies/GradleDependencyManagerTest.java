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
package com.android.tools.idea.gradle.dependencies;

import static com.android.ide.common.rendering.api.ResourceNamespace.RES_AUTO;
import static com.android.tools.idea.gradle.project.sync.snapshots.TestProjectDefinition.prepareTestProject;
import static com.android.tools.idea.imports.UtilsKt.assertBuildGradle;
import static com.android.tools.idea.projectsystem.ProjectSystemUtil.getModuleSystem;
import static com.android.tools.idea.projectsystem.gradle.GradleModuleSystemKt.CHECK_DIRECT_GRADLE_DEPENDENCIES;
import static com.android.tools.idea.testing.AndroidGradleTestUtilsKt.gradleModule;
import static com.google.common.truth.Truth.assertThat;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import com.android.SdkConstants;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.ide.common.resources.ResourceItem;
import com.android.resources.ResourceType;
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogModel;
import com.android.tools.idea.gradle.dsl.api.GradleVersionCatalogsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.project.sync.snapshots.AndroidCoreTestProject;
import com.android.tools.idea.projectsystem.GoogleMavenArtifactId;
import com.android.tools.idea.res.StudioResourceRepositoryManager;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.android.tools.idea.testing.IntegrationTestEnvironmentRule;
import com.android.tools.idea.testing.TestModuleUtil;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.testFramework.RunsInEdt;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

/**
 * Tests for {@link GradleDependencyManager}.
 */
@RunsInEdt
public class GradleDependencyManagerTest {

  @Rule
  public IntegrationTestEnvironmentRule projectRule = AndroidProjectRule.withIntegrationTestEnvironment();

  private static final GradleCoordinate APP_COMPAT_DEPENDENCY = new GradleCoordinate("com.android.support", "appcompat-v7", "+");
  private static final GradleCoordinate RECYCLER_VIEW_DEPENDENCY = new GradleCoordinate("com.android.support", "recyclerview-v7", "+");
  private static final GradleCoordinate ROOM_DEPENDENCY = new GradleCoordinate("androidx.room","room-ktx", "2.5.1");
  private static final GradleCoordinate DUMMY_DEPENDENCY = new GradleCoordinate("dummy.group", "dummy.artifact", "0.0.0");
  private static final GradleCoordinate VECTOR_DRAWABLE_DEPENDENCY =
    new GradleCoordinate("com.android.support", "support-vector-drawable", "+");

  private static final List<GradleCoordinate> DEPENDENCIES = ImmutableList.of(APP_COMPAT_DEPENDENCY, DUMMY_DEPENDENCY);

  @Test
  public void testFindMissingDependenciesWithRegularProject() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    preparedProject.open(it -> it, project -> {
      Module appModule = TestModuleUtil.findAppModule(project);
      GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(project);
      List<GradleCoordinate> missingDependencies = dependencyManager.findMissingDependencies(appModule, DEPENDENCIES);
      assertThat(missingDependencies).containsExactly(DUMMY_DEPENDENCY);
      return null;
    });
  }

  @Test
  public void testFindMissingDependenciesInProjectWithSplitBuildFiles() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SPLIT_BUILD_FILES);
    preparedProject.open(it -> it, project -> {
      Module appModule = TestModuleUtil.findAppModule(project);
      GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(project);
      List<GradleCoordinate> missingDependencies = dependencyManager.findMissingDependencies(appModule, DEPENDENCIES);
      assertThat(missingDependencies).containsExactly(DUMMY_DEPENDENCY);
      return null;
    });
  }

  @SuppressWarnings("unused")
  @Test
  @Ignore
  public void ignore_testDependencyAarIsExplodedForLayoutLib() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    preparedProject.open(it -> it, project -> {

      Module appModule = TestModuleUtil.findAppModule(project);
      List<GradleCoordinate> dependencies = Collections.singletonList(RECYCLER_VIEW_DEPENDENCY);
      GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(project);
      assertThat(dependencyManager.findMissingDependencies(appModule, dependencies)).isNotEmpty();

      boolean found = dependencyManager.addDependenciesAndSync(appModule, dependencies);
      assertTrue(found);

      List<ResourceItem> items = StudioResourceRepositoryManager
        .getAppResources(AndroidFacet.getInstance(gradleModule(project, ":app")))
        .getResources(RES_AUTO, ResourceType.STYLEABLE, "RecyclerView");
      assertThat(items).isNotEmpty();
      assertThat(dependencyManager.findMissingDependencies(appModule, dependencies)).isEmpty();
      return null;
    });
  }

  @SuppressWarnings("unused")
  @Test
  @Ignore
  public void ignore_testAddDependencyAndSync() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    preparedProject.open(it -> it, project -> {
      Module appModule = TestModuleUtil.findAppModule(project);
      GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(project);
      List<GradleCoordinate> dependencies = Collections.singletonList(RECYCLER_VIEW_DEPENDENCY);

      // Setup:
      // 1. RecyclerView artifact should not be declared in build script.
      // 2. RecyclerView should not be declared or resolved.
      assertThat(dependencyManager.findMissingDependencies(appModule, dependencies)).isNotEmpty();
      assertFalse(isRecyclerViewRegistered(project));
      assertFalse(isRecyclerViewResolved(project));

      boolean result = dependencyManager.addDependenciesAndSync(appModule, dependencies);

      // If addDependencyAndSync worked correctly,
      // 1. findMissingDependencies with the added dependency should return empty.
      // 2. RecyclerView should be declared and resolved (because the required artifact has been synced)
      assertTrue(result);
      assertThat(dependencyManager.findMissingDependencies(appModule, dependencies)).isEmpty();
      assertTrue(isRecyclerViewRegistered(project));
      assertTrue(isRecyclerViewResolved(project));
      return null;
    });
  }

  @SuppressWarnings("unused")
  @Test
  @Ignore
  public void ignore_testAddDependencyWithoutSync() {
    if (!CHECK_DIRECT_GRADLE_DEPENDENCIES) {
      // TODO: b/129297171
      // For now: We are not checking direct dependencies.
      // Re-enable this test when removing this variable.
      return;
    }
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    preparedProject.open(it -> it, project -> {
      Module appModule = TestModuleUtil.findAppModule(project);
      GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(project);
      List<GradleCoordinate> dependencies = Collections.singletonList(RECYCLER_VIEW_DEPENDENCY);

      // Setup:
      // 1. RecyclerView artifact should not be declared in build script.
      //    // 2. RecyclerView should not be declared or resolved.
      assertThat(dependencyManager.findMissingDependencies(appModule, dependencies)).isNotEmpty();
      assertFalse(isRecyclerViewRegistered(project));
      assertFalse(isRecyclerViewResolved(project));

      boolean result = dependencyManager.addDependenciesWithoutSync(appModule, dependencies);

      // If addDependencyWithoutSync worked correctly,
      // 1. findMissingDependencies with the added dependency should return empty.
      // 2. RecyclerView should be declared but NOT yet resolved (because we didn't sync)
      assertTrue(result);
      assertThat(dependencyManager.findMissingDependencies(appModule, dependencies)).isEmpty();
      assertTrue(isRecyclerViewRegistered(project));
      assertFalse(isRecyclerViewResolved(project));
      return null;
    });
  }

  @Test
  public void testAddedSupportDependencySameVersionAsExistingSupportDependency() {
    // Load a library with an explicit appcompat-v7 version that is older than the most recent version:
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APP_WITH_OLDER_SUPPORT_LIB);
    preparedProject.open(it -> it, project -> {

      Module appModule = TestModuleUtil.findAppModule(project);
      List<GradleCoordinate> dependencies = ImmutableList.of(APP_COMPAT_DEPENDENCY, RECYCLER_VIEW_DEPENDENCY);
      GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(project);
      List<GradleCoordinate> missing = dependencyManager.findMissingDependencies(appModule, dependencies);
      assertThat(missing.size()).isEqualTo(1);
      assertThat(missing.get(0).getId()).isEqualTo(SdkConstants.RECYCLER_VIEW_LIB_ARTIFACT);
      assertThat(missing.get(0).toString()).isEqualTo("com.android.support:recyclerview-v7:25.4.0");
      assertFalse(isRecyclerInCatalog(project));
      return null;
    });
  }

  @Test
  public void testCanAddDependencyWhichAlreadyIsAnIndirectDependency() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION);
    preparedProject.open(it -> it, project -> {

      Module appModule = TestModuleUtil.findAppModule(project);
      // Make sure the app module depends on the vector drawable library:
      assertNotNull(getModuleSystem(appModule).getResolvedDependency(VECTOR_DRAWABLE_DEPENDENCY));

      // Now check that the vector drawable library is NOT an explicit dependency:
      List<GradleCoordinate> vectorDrawable = Collections.singletonList(VECTOR_DRAWABLE_DEPENDENCY);
      GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(project);
      List<GradleCoordinate> missing = dependencyManager.findMissingDependencies(appModule, vectorDrawable);
      assertFalse(missing.isEmpty());
      assertFalse(isRecyclerInCatalog(project));
      return null;
    });
  }

  private boolean isRecyclerViewRegistered(@NotNull Project project) {
    return getModuleSystem(TestModuleUtil.findAppModule(project))
             .getRegisteredDependency(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("+")) != null;
  }

  private boolean isRecyclerViewResolved(@NotNull Project project) {
    return getModuleSystem(TestModuleUtil.findAppModule(project))
             .getResolvedDependency(GoogleMavenArtifactId.RECYCLERVIEW_V7.getCoordinate("+")) != null;
  }

  @Test
  public void testAddCatalogDependencyAndSync() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION_VERSION_CATALOG);
    preparedProject.open(it -> it, project -> {
      Module appModule = TestModuleUtil.findAppModule(project);
      GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(project);
      List<GradleCoordinate> dependencies = Collections.singletonList(RECYCLER_VIEW_DEPENDENCY);

      assertThat(dependencyManager.findMissingDependencies(appModule, dependencies)).isNotEmpty();

      boolean result = dependencyManager.addDependenciesAndSync(appModule, dependencies);

      assertTrue(result);
      GradleVersionCatalogsModel catalog = ProjectBuildModel.get(project).getVersionCatalogsModel();
      GradleVersionCatalogModel catalogModel = catalog.getVersionCatalogModel("libs");

      assertThat(
        dependencyManager.computeCatalogDependenciesInfo(appModule.getProject(), dependencies, catalogModel).missingLibraries).isEmpty();
      assertTrue(isRecyclerInCatalog(project));
      assertBuildGradle(project, str -> !str.contains("com.android.support:libs.recyclerview-v7:+"));
      assertBuildGradle(project, str -> str.contains("implementation libs.recyclerview.v7"));
      return null;
    });
  }

  @Test
  public void testFindInCatalogAndAddToBuild() {
    final var preparedProject = prepareTestProject(projectRule, AndroidCoreTestProject.SIMPLE_APPLICATION_VERSION_CATALOG);
    preparedProject.open(it -> it, project -> {
      Module appModule = TestModuleUtil.findAppModule(project);
      GradleDependencyManager dependencyManager = GradleDependencyManager.getInstance(project);
      List<GradleCoordinate> dependencies = Collections.singletonList(ROOM_DEPENDENCY);

      assertThat(dependencyManager.findMissingDependencies(appModule, dependencies)).isNotEmpty();

      boolean result = dependencyManager.addDependenciesAndSync(appModule, dependencies);

      assertTrue(result);
      GradleVersionCatalogsModel catalog = ProjectBuildModel.get(project).getVersionCatalogsModel();
      GradleVersionCatalogModel catalogModel = catalog.getVersionCatalogModel("libs");

      assertThat(
        dependencyManager.computeCatalogDependenciesInfo(appModule.getProject(), dependencies, catalogModel).missingLibraries).isEmpty();
      assertTrue(isInCatalog(project, "group=androidx.room,name=room-ktx,version=2.5.0"));
      assertBuildGradle(project, str -> !str.contains("androidx.room:room-ktx:2.5.0"));
      assertBuildGradle(project, str -> str.contains("implementation libs.androidx.room.ktx"));
      return null;
    });
  }

  private boolean checkFileContent(Project project, VirtualFile file, Predicate<String> check) {
    PsiFile buildGradlePsi = PsiManager.getInstance(project).findFile(file);
    assertNotNull(buildGradlePsi.getText());
    return check.test(buildGradlePsi.getText().replace(" ", "").replace("\"", ""));
  }

  private boolean isRecyclerInCatalog(Project project){
    return isInCatalog(project, "group=com.android.support,name=recyclerview-v7,version=28.0.0");
  }

  private boolean isInCatalog(Project project, String catalogSnippet) {
    GradleVersionCatalogsModel catalog = ProjectBuildModel.get(project).getVersionCatalogsModel();
    GradleVersionCatalogModel catalogModel = catalog.getVersionCatalogModel("libs");
    if (catalogModel == null) return false;
    VirtualFile file = catalogModel.getVirtualFile();
    return checkFileContent(project,
                            file,
                            str -> str.contains(catalogSnippet));
  }
}
