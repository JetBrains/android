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
package com.android.tools.idea.gradle.project.sync.hyperlink;

import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.junit.Assume.assumeTrue;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.repositories.GoogleDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.UrlBasedRepositoryModel;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.ServiceContainerUtil;
import java.io.IOException;
import java.util.List;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link AddGoogleMavenRepositoryHyperlink}.
 */
public class AddGoogleMavenRepositoryHyperlinkTest extends AndroidGradleTestCase {

  @Override
  public void setUp() throws Exception {
    super.setUp();
  }

  public void testExecuteWithGradle4dot0() throws Exception {
    // Check that quickfix adds google maven repository using method name when gradle version is 4.0 or higher
    verifyExecute("4.0");
  }

  public void testExecuteWithModule() throws Exception {
    // Check that quickfix adds google maven repository to module when the repositories are defined in the module
    loadProject(SIMPLE_APPLICATION);
    Project project = getProject();
    // Remove repositories from project and add a repository block to app
    removeRepositories(project);
    Module appModule = getModule("app");
    GradleBuildModel buildModel = GradleBuildModel.get(appModule);
    removeRepositories(buildModel);

    // Verify that execute is applied to app build file
    // Generate hyperlink and execute quick fix
    AddGoogleMavenRepositoryHyperlink hyperlink =
      new AddGoogleMavenRepositoryHyperlink(ImmutableList.of(buildModel.getVirtualFile()), /* no sync */ false);
    hyperlink.execute(project);

    // Verify it added the repository to app
    buildModel = GradleBuildModel.get(appModule);
    assertThat(buildModel).isNotNull();
    List<? extends RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    assertThat(repositories.get(0)).isInstanceOf(GoogleDefaultRepositoryModel.class);

    // verify it did not add repository to project
    buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
  }

  // Check that quickfix adds google maven correctly when no build file is passed
  public void testExecuteNullBuildFile() throws Exception {
    // Prepare project and mock version
    prepareProjectForImport(SIMPLE_APPLICATION);
    Project project = getProject();

    // Make sure no repositories are listed
    removeRepositories(project);
    GradleBuildModel buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();

    // Generate hyperlink and execute quick fix
    AddGoogleMavenRepositoryHyperlink hyperlink =
      new AddGoogleMavenRepositoryHyperlink(ImmutableList.of(buildModel.getVirtualFile()), /* no sync */ false);
    hyperlink.execute(project);

    // Verify it added the repository
    buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    List<? extends RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);

    // Verify it was added in buildscript
    repositories = buildModel.buildscript().repositories().repositories();
    assertThat(repositories).hasSize(1);
  }

  public void testAddToMulipleBuildFiles() throws Exception {
    loadProject(DEPENDENT_MODULES);
    Project project = getProject();
    removeRepositories(project);

    ProjectBuildModel pbm = ProjectBuildModel.get(project);
    GradleBuildModel projectBuildModel = pbm.getProjectBuildModel();

    Module appModule = getModule("app");
    GradleBuildModel appBuildModel = pbm.getModuleBuildModel(appModule);
    removeRepositories(appBuildModel);

    Module libModule = getModule("lib");
    GradleBuildModel libBuildModel = pbm.getModuleBuildModel(libModule);


    // Generate hyperlink and execute quick fix
    AddGoogleMavenRepositoryHyperlink hyperlink = new AddGoogleMavenRepositoryHyperlink(
      ImmutableList.of(appBuildModel.getVirtualFile(), libBuildModel.getVirtualFile(), projectBuildModel.getVirtualFile()), /* no sync */
      false);
    hyperlink.execute(project);

    pbm = ProjectBuildModel.get(project);
    projectBuildModel = pbm.getProjectBuildModel();
    appBuildModel = pbm.getModuleBuildModel(appModule);
    libBuildModel = pbm.getModuleBuildModel(libModule);

    // Verify it added the repository
    assertThat(appBuildModel).isNotNull();
    List<? extends RepositoryModel> repositories = appBuildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    assertNull(appBuildModel.buildscript().getPsiElement());

    // And of the second module
    assertThat(libBuildModel).isNotNull();
    repositories = libBuildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    assertNull(libBuildModel.buildscript().getPsiElement());

    // Since we passed the project build model file it should be present there as well
    repositories = projectBuildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);

    // Verify it was added in buildscript
    repositories = projectBuildModel.buildscript().repositories().repositories();
    assertThat(repositories).hasSize(1);
  }

  private static void verifyRepositoryForm(RepositoryModel repository, boolean byMethod) {
    assertInstanceOf(repository, UrlBasedRepositoryModel.class);
    UrlBasedRepositoryModel urlRepository = (UrlBasedRepositoryModel) repository;
    if (byMethod) {
      assertNull("url", urlRepository.url().getPsiElement());
    }
    else {
      assertNotNull("url", urlRepository.url().getPsiElement());
    }
  }

  private void verifyExecute(@NotNull String version) throws IOException {
    assumeTrue(version.equals("4.0"));
    // Prepare project and mock version
    prepareProjectForImport(SIMPLE_APPLICATION);
    Project project = getProject();
    GradleVersions spyVersions = spy(GradleVersions.getInstance());
    ServiceContainerUtil.replaceService(ApplicationManager.getApplication(), GradleVersions.class, spyVersions, getTestRootDisposable());
    when(spyVersions.getGradleVersion(project)).thenReturn(GradleVersion.version(version));

    // Make sure no repositories are listed
    removeRepositories(project);
    GradleBuildModel buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();

    // Generate hyperlink and execute quick fix
    AddGoogleMavenRepositoryHyperlink hyperlink =
      new AddGoogleMavenRepositoryHyperlink(ImmutableList.of(buildModel.getVirtualFile()), /* no sync */ false);
    hyperlink.execute(project);

    // Verify it added the repository
    buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    List<? extends RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    verifyRepositoryForm(repositories.get(0), true);

    // Verify it was added in buildscript
    repositories = buildModel.buildscript().repositories().repositories();
    assertThat(repositories).hasSize(1);
    verifyRepositoryForm(repositories.get(0), true);
  }

  private void removeRepositories(@NotNull Project project) {
    GradleBuildModel buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    buildModel.removeRepositoriesBlocks();
    buildModel.buildscript().removeRepositoriesBlocks();
    assertTrue(buildModel.isModified());
    runWriteCommandAction(getProject(), buildModel::applyChanges);
    buildModel.reparse();
    assertFalse(buildModel.isModified());
    buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    assertThat(buildModel.repositories().repositories()).hasSize(0);
    assertThat(buildModel.buildscript().repositories().repositories()).hasSize(0);
  }

  private void removeRepositories(@NotNull GradleBuildModel buildModel) {
    assertThat(buildModel).isNotNull();
    buildModel.removeRepositoriesBlocks();
    assertTrue(buildModel.isModified());
    runWriteCommandAction(getProject(), buildModel::applyChanges);
    buildModel.reparse();
    assertFalse(buildModel.isModified());
  }
}
