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
import com.android.tools.idea.gradle.dsl.api.GradleSettingsModel;
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
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.ServiceContainerUtil;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;

/**
 * Tests for {@link AddGoogleMavenRepositoryHyperlink}.
 */
public class AddGoogleMavenRepositoryHyperlinkTest extends AndroidGradleTestCase {

  public void testExecuteWithGradle4dot0() throws Exception {
    // Check that quickfix adds google maven repository using method name when gradle version is 4.0 or higher
    verifyExecute("4.0");
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
}

