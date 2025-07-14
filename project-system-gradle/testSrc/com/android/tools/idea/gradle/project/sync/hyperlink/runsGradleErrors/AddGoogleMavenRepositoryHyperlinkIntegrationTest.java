/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.hyperlink.runsGradleErrors;

import static com.android.SdkConstants.FN_SETTINGS_GRADLE;
import static com.android.tools.idea.testing.TestModuleUtil.findAppModule;
import static com.android.tools.idea.testing.TestModuleUtil.findModule;
import static com.android.tools.idea.testing.TestProjectPaths.DEPENDENT_MODULES;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;

import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
import com.android.tools.idea.gradle.dsl.api.ProjectBuildModel;
import com.android.tools.idea.gradle.dsl.api.repositories.GoogleDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.project.sync.hyperlink.AddGoogleMavenRepositoryHyperlink;
import com.android.tools.idea.testing.AndroidGradleProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtRule;
import com.intellij.testFramework.RunsInEdt;
import java.io.File;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@RunsInEdt
public class AddGoogleMavenRepositoryHyperlinkIntegrationTest {
  public AndroidGradleProjectRule projectRule = new AndroidGradleProjectRule();

  @Rule
  public RuleChain rule = RuleChain.outerRule(new EdtRule()).around(projectRule);

  @Test
  public void testExecuteWithModule() throws Exception {
    // Check that quickfix adds google maven repository to module when the repositories are defined in the module
    projectRule.loadProject(SIMPLE_APPLICATION);
    Project project = projectRule.getProject();
    // Remove repositories from project and add a repository block to app
    removeRepositories(project);
    Module appModule = findAppModule(project);
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

  @Test
  public void testNoRepoAdditionToBuildFilesWhenRepoAlreadyExistsInSettings() throws Exception {
    projectRule.loadProject(SIMPLE_APPLICATION);
    Project project = projectRule.getProject();
    removeRepositories(project);
    ProjectBuildModel pbm = ProjectBuildModel.get(project);
    GradleBuildModel projectBuildModel = pbm.getProjectBuildModel();
    Module appModule = findAppModule(project);
    GradleBuildModel appBuildModel = pbm.getModuleBuildModel(appModule);
    removeRepositories(appBuildModel);

    GradleSettingsModel settingsModel = pbm.getProjectSettingsModel();
    settingsModel.dependencyResolutionManagement().repositories().addGoogleMavenRepository();
    runWriteCommandAction(project, settingsModel::applyChanges);

    AddGoogleMavenRepositoryHyperlink hyperlink = new AddGoogleMavenRepositoryHyperlink(
      ImmutableList.of(appBuildModel.getVirtualFile(), projectBuildModel.getVirtualFile()), /* no sync */
      false);
    hyperlink.execute(project);

    pbm = ProjectBuildModel.get(project);
    projectBuildModel = pbm.getProjectBuildModel();
    appBuildModel = pbm.getModuleBuildModel(appModule);

    // Verify it did not add the repository because it is present in dependencyResolutionManagement
    assertThat(appBuildModel).isNotNull();
    List<? extends RepositoryModel> repositories = appBuildModel.repositories().repositories();
    assertThat(repositories).isEmpty();
    assertThat(appBuildModel.buildscript().getPsiElement()).isNull();

    repositories = projectBuildModel.repositories().repositories();
    assertThat(repositories).isEmpty();

    repositories = projectBuildModel.buildscript().repositories().repositories();
    assertThat(repositories).isEmpty();
  }

  @Test
  public void testGoogleRepoAdditionToSettingsFileWhenRepoBlockExistsInSettings() throws Exception {
    projectRule.loadProject(SIMPLE_APPLICATION);
    Project project = projectRule.getProject();
    removeRepositories(project);
    ProjectBuildModel pbm = ProjectBuildModel.get(project);
    GradleBuildModel projectBuildModel = pbm.getProjectBuildModel();
    Module appModule = findAppModule(project);
    GradleBuildModel appBuildModel = pbm.getModuleBuildModel(appModule);
    removeRepositories(appBuildModel);
    File settingsFile = new File(project.getBasePath(), FN_SETTINGS_GRADLE);
    VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByIoFile(settingsFile);
    ApplicationManager.getApplication().runWriteAction((ThrowableComputable<Void, Exception>)() -> {
      VfsUtil.saveText(virtualFile, VfsUtilCore.loadText(virtualFile) + """
        dependencyResolutionManagement {
          repositories {
          }
        }
        """);
      return null;
    });
    AddGoogleMavenRepositoryHyperlink hyperlink = new AddGoogleMavenRepositoryHyperlink(
      ImmutableList.of(appBuildModel.getVirtualFile(), projectBuildModel.getVirtualFile()), /* no sync */
      false);
    hyperlink.execute(project);

    pbm = ProjectBuildModel.get(project);
    projectBuildModel = pbm.getProjectBuildModel();
    appBuildModel = pbm.getModuleBuildModel(appModule);
    GradleSettingsModel settingsModel = pbm.getProjectSettingsModel();
    assertThat(settingsModel.dependencyResolutionManagement().repositories().hasGoogleMavenRepository()).isTrue();

    // Ensure that the Google repo is only added in settings file.
    List<? extends RepositoryModel> repositories = appBuildModel.repositories().repositories();
    assertThat(repositories).isEmpty();
    assertThat(appBuildModel.buildscript().getPsiElement()).isNull();

    repositories = projectBuildModel.repositories().repositories();
    assertThat(repositories).isEmpty();

    repositories = projectBuildModel.buildscript().repositories().repositories();
    assertThat(repositories).isEmpty();
  }

  @Test
  public void testAddToMultipleBuildFiles() throws Exception {
    projectRule.loadProject(DEPENDENT_MODULES);
    Project project = projectRule.getProject();
    removeRepositories(project);

    ProjectBuildModel pbm = ProjectBuildModel.get(project);
    GradleBuildModel projectBuildModel = pbm.getProjectBuildModel();

    Module appModule = findModule(project, "app");
    GradleBuildModel appBuildModel = pbm.getModuleBuildModel(appModule);
    removeRepositories(appBuildModel);

    Module libModule = findModule(project, "lib");
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
    assertThat(appBuildModel.buildscript().getPsiElement()).isNull();

    // And of the second module
    assertThat(libBuildModel).isNotNull();
    repositories = libBuildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    assertThat(libBuildModel.buildscript().getPsiElement()).isNull();

    // Since we passed the project build model file it should be present there as well
    repositories = projectBuildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);

    // Verify it was added in buildscript
    repositories = projectBuildModel.buildscript().repositories().repositories();
    assertThat(repositories).hasSize(1);
  }

  private void removeRepositories(@NotNull Project project) {
    GradleBuildModel buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    buildModel.removeRepositoriesBlocks();
    buildModel.buildscript().removeRepositoriesBlocks();
    assertThat(buildModel.isModified()).isTrue();
    runWriteCommandAction(projectRule.getProject(), buildModel::applyChanges);
    buildModel.reparse();
    assertThat(buildModel.isModified()).isFalse();
    buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    assertThat(buildModel.repositories().repositories()).hasSize(0);
    assertThat(buildModel.buildscript().repositories().repositories()).hasSize(0);
  }

  private void removeRepositories(@NotNull GradleBuildModel buildModel) {
    assertThat(buildModel).isNotNull();
    buildModel.removeRepositoriesBlocks();
    assertThat(buildModel.isModified()).isTrue();
    runWriteCommandAction(projectRule.getProject(), buildModel::applyChanges);
    buildModel.reparse();
    assertThat(buildModel.isModified()).isFalse();
  }
}
