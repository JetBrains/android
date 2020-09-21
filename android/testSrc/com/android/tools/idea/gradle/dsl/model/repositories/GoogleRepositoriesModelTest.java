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
package com.android.tools.idea.gradle.dsl.model.repositories;

import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_DUPLICATE_TO_EXISTING_FLAT_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_DUPLICATE_TO_EXISTING_FLAT_REPOSITORY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_FLAT_REPOSITORY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_FLAT_REPOSITORY_FROM_EMPTY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_EMPTY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_EMPTY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_METHOD_CALL_PRESENT;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_EMPTY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_EMPTY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_BY_URL_PRESENT;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_TO_EMPTY_BUILDSCRIPT;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_TO_EMPTY_BUILDSCRIPT_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_TO_EXISTING_FLAT_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_TO_EXISTING_FLAT_REPOSITORY_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_MULTIPLE_LOCAL_REPOS;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_CUSTOM_MAVEN_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_FLAT_DIR_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_FLAT_DIR_REPOSITORY_WITH_DIR_LIST_ARGUMENT;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_FLAT_DIR_REPOSITORY_WITH_SINGLE_DIR_ARGUMENT;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_GOOGLE_DEFAULT_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_J_CENTER_CUSTOM_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_J_CENTER_DEFAULT_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_MAVEN_CENTRAL_REPOSITORY;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_MAVEN_CENTRAL_REPOSITORY_WITH_MULTIPLE_ARTIFACT_URLS;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_MAVEN_CENTRAL_REPOSITORY_WITH_SINGLE_ARTIFACT_URLS;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_MAVEN_REPOSITORY_WITH_ARTIFACT_URLS;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_MAVEN_REPOSITORY_WITH_CREDENTIALS;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_PARSE_MULTIPLE_REPOSITORIES;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_SET_ARTIFACT_URLS_IN_MAVEN;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_SET_ARTIFACT_URLS_IN_MAVEN_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_SET_NAME_FOR_METHOD_CALL;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_SET_NAME_FOR_METHOD_CALL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_SET_URL_FOR_METHOD_CALL;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_WITH_WITH;
import static com.android.tools.idea.gradle.dsl.TestFileName.REPOSITORIES_MODEL_SET_URL_FOR_METHOD_CALL_EXPECTED;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_DEFAULT_REPO_NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_DEFAULT_REPO_URL;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModelImpl.GOOGLE_METHOD_NAME;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.ide.common.repository.GradleVersion;
import com.android.tools.idea.gradle.dsl.TestFileName;
import com.android.tools.idea.gradle.dsl.api.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.api.ext.GradlePropertyModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModel;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoriesModelExtensionKt;
import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

/**
 * Tests for {@link RepositoriesModelImpl}.
 */
public class GoogleRepositoriesModelTest extends GradleFileModelTestCase {


  @NotNull private static final String TEST_DIR = "hello/i/am/a/dir";
  @NotNull private static final String OTHER_TEST_DIR = "/this/is/also/a/dir";

  @Test
  public void testMultipleGoogleRepos() throws IOException {
    writeToBuildFile(REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_WITH_WITH);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.buildscript().repositories();

    RepositoriesModelExtensionKt.addGoogleMavenRepository(repositoriesModel, GradleVersion.tryParse("4.10.1"));
    applyChangesAndReparse(buildModel);

    verifyFileContents(myBuildFile, REPOSITORIES_MODEL_ADD_GOOGLE_REPOSITORY_WITH_WITH);
  }
}
