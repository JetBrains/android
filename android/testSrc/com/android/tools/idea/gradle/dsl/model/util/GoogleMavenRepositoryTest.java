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
package com.android.tools.idea.gradle.dsl.model.util;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.MavenRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoryModel;
import com.android.tools.idea.gradle.util.GradleVersions;
import com.android.tools.idea.testing.IdeComponents;
import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_URL;
import static com.android.tools.idea.gradle.dsl.model.util.GoogleMavenRepository.addGoogleRepository;
import static com.android.tools.idea.gradle.dsl.model.util.GoogleMavenRepository.hasGoogleMavenRepository;
import static com.google.common.truth.Truth.assertThat;
import static com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GoogleMavenRepository}.
 */
public class GoogleMavenRepositoryTest extends GradleFileModelTestCase {
  private IdeComponents myIdeComponents;
  private GradleVersions myGradleVersions;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    myIdeComponents = new IdeComponents(getProject());
    myGradleVersions = spy(GradleVersions.getInstance());
    myIdeComponents.replaceService(GradleVersions.class, myGradleVersions);
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

  public void testhasGoogleMavenRepositoryEmpty() throws IOException {
    String text = "repositories {\n" +
                  "}";
    writeToBuildFile(text);
    assertFalse(hasGoogleMavenRepository(getGradleBuildModel().repositories()));
  }

  public void testhasGoogleMavenRepositoryName3dot5() throws IOException {
    String text = "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);
    when(myGradleVersions.getGradleVersion(getProject())).thenReturn(GradleVersion.parse("3.5"));
    assertFalse(hasGoogleMavenRepository(getGradleBuildModel().repositories()));
  }

  public void testhasGoogleMavenRepositoryName4dot0() throws IOException {
    String text = "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);
    when(myGradleVersions.getGradleVersion(getProject())).thenReturn(GradleVersion.parse("4.0"));
    assertTrue(hasGoogleMavenRepository(getGradleBuildModel().repositories()));
  }

  public void testhasGoogleMavenRepositoryUrl3dot5() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    url \"" + GOOGLE_DEFAULT_REPO_URL + "\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);
    when(myGradleVersions.getGradleVersion(getProject())).thenReturn(GradleVersion.parse("3.5"));
    assertTrue(hasGoogleMavenRepository(getGradleBuildModel().repositories()));
  }

  public void testhasGoogleMavenRepositoryUrl4dot0() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    url \"" + GOOGLE_DEFAULT_REPO_URL + "\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);
    when(myGradleVersions.getGradleVersion(getProject())).thenReturn(GradleVersion.parse("4.0"));
    assertTrue(hasGoogleMavenRepository(getGradleBuildModel().repositories()));
  }

  public void testAddGoogleRepositoryEmpty3dot5() throws IOException {
    // Prepare repositories
    String text = "repositories {\n" +
                  "}";
    writeToBuildFile(text);
    Project project = getProject();
    when(myGradleVersions.getGradleVersion(project)).thenReturn(GradleVersion.parse("3.5"));
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    assertThat(repositoriesModel.repositories()).isEmpty();

    // add repository
    addGoogleRepository(repositoriesModel, project);
    assertTrue(buildModel.isModified());
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    // Verify
    buildModel = getGradleBuildModel();
    repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    assertThat(repositories.get(0)).isInstanceOf(MavenRepositoryModel.class);
  }

  public void testAddGoogleRepositoryEmpty4dot0() throws IOException {
    // Prepare repositories
    String text = "repositories {\n" +
                  "}";
    writeToBuildFile(text);
    Project project = getProject();
    when(myGradleVersions.getGradleVersion(project)).thenReturn(GradleVersion.parse("4.0"));
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    assertThat(repositoriesModel.repositories()).isEmpty();

    // add repository
    addGoogleRepository(repositoriesModel, project);
    assertTrue(buildModel.isModified());
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    // Verify
    buildModel = getGradleBuildModel();
    repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    assertThat(repositories.get(0)).isInstanceOf(GoogleDefaultRepositoryModel.class);
  }

  public void testAddGoogleRepositoryWithUrlAlready() throws IOException {
    // Prepare repositories
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    url \"" + GOOGLE_DEFAULT_REPO_URL + "\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);
    Project project = getProject();
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    assertThat(repositoriesModel.repositories()).hasSize(1);

    // add repository
    addGoogleRepository(repositoriesModel, project);

    // Verify
    assertFalse(buildModel.isModified());
  }

  public void testAddGoogleRepositoryWithGoogleAlready3dot5() throws IOException {
    // Prepare repositories
    String text = "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);
    Project project = getProject();
    when(myGradleVersions.getGradleVersion(project)).thenReturn(GradleVersion.parse("3.5"));
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    assertThat(repositoriesModel.repositories()).hasSize(1);

    // add repository
    addGoogleRepository(repositoriesModel, project);
    assertTrue(buildModel.isModified());
    runWriteCommandAction(getProject(), buildModel::applyChanges);

    // Verify
    buildModel = getGradleBuildModel();
    repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    assertThat(repositories.get(0)).isInstanceOf(GoogleDefaultRepositoryModel.class);
    assertThat(repositories.get(1)).isInstanceOf(MavenRepositoryModel.class);
  }

  public void testAddGoogleRepositoryWithGoogleAlready4dot0() throws IOException {
    // Prepare repositories
    String text = "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);
    Project project = getProject();
    when(myGradleVersions.getGradleVersion(project)).thenReturn(GradleVersion.parse("4.0"));
    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    assertThat(repositoriesModel.repositories()).hasSize(1);

    // add repository
    addGoogleRepository(repositoriesModel, project);

    // Verify
    assertFalse(buildModel.isModified());
  }
}
