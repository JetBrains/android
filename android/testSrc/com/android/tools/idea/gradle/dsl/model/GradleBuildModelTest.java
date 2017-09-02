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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.model.build.BuildScriptModel;
import com.android.tools.idea.gradle.dsl.model.repositories.RepositoryModel;

import java.io.IOException;
import java.util.List;

import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link GradleBuildModel}.
 */
public class GradleBuildModelTest extends GradleFileModelTestCase{
  public void testRemoveRepositoriesSingleBlock() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(2);
    buildModel.removeRepositoriesBlocks();
    repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
  }

  public void testRemoveRepositoriesMultipleBlocks() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "}\n" +
                  "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(2);
    buildModel.removeRepositoriesBlocks();
    repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
  }

  public void testRemoveRepositoriesWithBuildscriptRepositories() throws IOException {
    String text = "buildscript {\n" +
                  "  repositories {\n" +
                  "    jcenter()\n" +
                  "    google()\n" +
                  "  }\n" +
                  "}\n" +
                  "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    BuildScriptModel buildscript = getGradleBuildModel().buildscript();
    repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(2);

    buildModel.removeRepositoriesBlocks();
    repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
    buildscript = buildModel.buildscript();
    repositories = buildscript.repositories().repositories();
    assertThat(repositories).hasSize(2);
  }

  public void testRemoveRepositoriesWithAllprojectsBlock() throws IOException {
    String text = "allprojects {\n" +
                  "  repositories {\n" +
                  "    jcenter()\n" +
                  "  }\n" +
                  "}\n" +
                  "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(2);

    buildModel.removeRepositoriesBlocks();
    repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
  }
}
