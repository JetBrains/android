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

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.repositories.GoogleDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.testing.AndroidGradleTestCase;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel.RepositoryType;
import static com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel.RepositoryType.GOOGLE_DEFAULT;
import static com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel.RepositoryType.MAVEN;
import static com.android.tools.idea.testing.TestProjectPaths.SIMPLE_APPLICATION;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AddGoogleMavenRepositoryHyperlink}.
 */
public class AddGoogleMavenRepositoryHyperlinkTest extends AndroidGradleTestCase {
  private IdeComponents myIdeComponents;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIdeComponents = new IdeComponents(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      myIdeComponents.restore();
    }
    finally {
      super.tearDown();
    }
  }

  public void testExecuteWithGradle3dot5() throws Exception {
    // Check that quickfix adds google maven repository using url when gradle version is lower than 4.0
    verifyExecute("3.5", MAVEN);
  }

  public void testExecuteWithGradle4dot0() throws Exception {
    // Check that quickfix adds google maven repository using method name when gradle version is 4.0 or higher
    verifyExecute("4.0", GOOGLE_DEFAULT);
  }

  public void testExecuteWithModule() throws Exception {
    // Check that quickfix adds google maven repository to module when the repositories are defined in the module
    loadProject(SIMPLE_APPLICATION);
    Project project = getProject();
    // Remove repositories from project and add a repository block to app
    removeRepositories(project);
    Module appModule = getModule("app");
    GradleBuildModel buildModel = GradleBuildModel.get(appModule);
    assertThat(buildModel).isNotNull();
    buildModel.removeRepositoriesBlocks();
    assertTrue(buildModel.isModified());
    runWriteCommandAction(getProject(), buildModel::applyChanges);
    buildModel.reparse();
    assertFalse(buildModel.isModified());

    // Verify that execute is applied to app build file
    // Generate hyperlink and execute quick fix
    AddGoogleMavenRepositoryHyperlink hyperlink = new AddGoogleMavenRepositoryHyperlink(buildModel.getVirtualFile(), /* no sync */ false);
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

    // Verify it was added in buildscript
    GradleBuildModel buildModelProject = GradleBuildModel.get(project);
    repositories = buildModelProject.buildscript().repositories().repositories();
    assertThat(repositories).hasSize(1);
    assertThat(repositories.get(0)).isInstanceOf(GoogleDefaultRepositoryModel.class);
  }

  private void verifyExecute(@NotNull String version, @NotNull RepositoryType type) throws IOException {
    // Prepare project and mock version
    prepareProjectForImport(SIMPLE_APPLICATION);
    Project project = getProject();
    GradleVersions spyVersions = spy(GradleVersions.getInstance());
    myIdeComponents.replaceService(GradleVersions.class, spyVersions);
    when(spyVersions.getGradleVersion(project)).thenReturn(GradleVersion.parse(version));

    // Make sure no repositories are listed
    removeRepositories(project);
    GradleBuildModel buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();

    // Generate hyperlink and execute quick fix
    AddGoogleMavenRepositoryHyperlink hyperlink = new AddGoogleMavenRepositoryHyperlink(buildModel.getVirtualFile(), /* no sync */ false);
    hyperlink.execute(project);

    // Verify it added the repository
    buildModel = GradleBuildModel.get(project);
    assertThat(buildModel).isNotNull();
    List<? extends RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    assertEquals(type, repositories.get(0).getType());

    // Verify it was added in buildscript
    repositories = buildModel.buildscript().repositories().repositories();
    assertThat(repositories).hasSize(1);
    assertEquals(type, repositories.get(0).getType());
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
}
