/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.build;

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.ArtifactDependencyTest.ExpectedArtifactDependency;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependenciesModel;
import com.android.tools.idea.gradle.dsl.model.repositories.JCenterDefaultRepositoryModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoryModel;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link BuildScriptModel}.
 */
public class BuildScriptModelTest extends GradleFileModelTestCase {
  public void testParseDependencies() throws IOException {
    String text = "buildscript {\n" +
                  "  dependencies {\n" +
                  "    classpath 'com.android.tools.build:gradle:2.0.0-alpha2'\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.buildscript().dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.0-alpha2");
    expected.assertMatches(dependencies.get(0));
  }

  public void testAddDependency() throws IOException {
    String text = "";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    BuildScriptModel buildScriptModel = buildModel.buildscript();
    DependenciesModel dependenciesModel = buildScriptModel.dependencies();

    assertFalse(buildScriptModel.hasValidPsiElement());
    assertFalse(dependenciesModel.hasValidPsiElement());
    assertThat(dependenciesModel.artifacts()).isEmpty();

    dependenciesModel.addArtifact("classpath", "com.android.tools.build:gradle:2.0.0-alpha2");

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.0-alpha2");
    expected.assertMatches(dependencies.get(0));

    assertTrue(buildModel.isModified());
    applyChanges(buildModel);
    assertFalse(buildModel.isModified());

    buildModel.reparse();
    buildScriptModel = buildModel.buildscript();
    dependenciesModel = buildScriptModel.dependencies();

    assertTrue(buildScriptModel.hasValidPsiElement());
    assertTrue(dependenciesModel.hasValidPsiElement());
    dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);
    expected.assertMatches(dependencies.get(0));
  }

  public void testEditDependency() throws IOException {
    String text = "buildscript {\n" +
                  "  dependencies {\n" +
                  "    classpath 'com.android.tools.build:gradle:2.0.0-alpha2'\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    DependenciesModel dependenciesModel = buildModel.buildscript().dependencies();

    List<ArtifactDependencyModel> dependencies = dependenciesModel.artifacts();
    assertThat(dependencies).hasSize(1);

    ExpectedArtifactDependency expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.0-alpha2");
    ArtifactDependencyModel actual = dependencies.get(0);
    expected.assertMatches(actual);

    actual.setVersion("2.0.1");

    expected = new ExpectedArtifactDependency("classpath", "gradle", "com.android.tools.build", "2.0.1");
    expected.assertMatches(actual);

    assertTrue(buildModel.isModified());
    applyChanges(buildModel);
    assertFalse(buildModel.isModified());

    buildModel.reparse();
    dependencies = buildModel.buildscript().dependencies().artifacts();
    assertThat(dependencies).hasSize(1);
    expected.assertMatches(dependencies.get(0));
  }

  public void testParseRepositories() throws IOException {
    String text = "buildscript {\n" +
                  "  repositories {\n" +
                  "    jcenter()\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().buildscript().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof JCenterDefaultRepositoryModel);
    JCenterDefaultRepositoryModel repository = (JCenterDefaultRepositoryModel)repositoryModel;
    assertEquals("name", "BintrayJCenter2", repository.name());
    assertEquals("url", "https://jcenter.bintray.com/", repository.url());
  }
}
