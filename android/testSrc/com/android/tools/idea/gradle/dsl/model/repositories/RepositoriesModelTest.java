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

import com.android.tools.idea.gradle.dsl.model.GradleBuildModel;
import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;

import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_NAME;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel.GOOGLE_DEFAULT_REPO_URL;
import static com.android.tools.idea.gradle.dsl.model.repositories.GoogleDefaultRepositoryModel.GOOGLE_METHOD_NAME;
import static com.google.common.truth.Truth.assertThat;

/**
 * Tests for {@link RepositoriesModel}.
 */
public class RepositoriesModelTest extends GradleFileModelTestCase {
  public void testParseJCenterDefaultRepository() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
  }

  public void testParseJCenterCustomRepository() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter {\n" +
                  "    url \"http://jcenter.bintray.com/\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof JCenterRepositoryModel);
    JCenterRepositoryModel repository = (JCenterRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "BintrayJCenter2", repository.name().value());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://jcenter.bintray.com/", repository.url().value());
  }

  public void testParseMavenCentralRepository() throws IOException {
    String text = "repositories {\n" +
                  "  mavenCentral()\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof MavenCentralRepositoryModel);
    MavenCentralRepositoryModel repository = (MavenCentralRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "MavenRepo", repository.name().value());
    assertNull("url", repository.url().getPsiElement());
    assertEquals("url", "https://repo1.maven.org/maven2/", repository.url().value());
  }

  public void testParseMavenCentralRepositoryWithMultipleArtifactUrls() throws IOException {
    String text = "repositories {\n" +
                  "  mavenCentral name: \"nonDefaultName\", " +
                  "artifactUrls: [\"http://www.mycompany.com/artifacts1\", \"http://www.mycompany.com/artifacts2\"]\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof MavenCentralRepositoryModel);
    MavenCentralRepositoryModel repository = (MavenCentralRepositoryModel)repositoryModel;
    assertNotNull("name", repository.name().getPsiElement());
    assertEquals("name", "nonDefaultName", repository.name().value());
    assertNull("url", repository.url().getPsiElement());
    assertEquals("url", "https://repo1.maven.org/maven2/", repository.url().value());
    assertEquals("artifactUrls",
                 ImmutableList.of("http://www.mycompany.com/artifacts1", "http://www.mycompany.com/artifacts2"),
                 repository.artifactUrls());
  }

  public void testParseMavenCentralRepositoryWithSingleArtifactUrls() throws IOException {
    String text = "repositories {\n" +
                  "  mavenCentral artifactUrls: \"http://www.mycompany.com/artifacts\"\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof MavenCentralRepositoryModel);
    MavenCentralRepositoryModel repository = (MavenCentralRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "MavenRepo", repository.name().value());
    assertNull("url", repository.url().getPsiElement());
    assertEquals("url", "https://repo1.maven.org/maven2/", repository.url().value());
    assertEquals("artifactUrls", ImmutableList.of("http://www.mycompany.com/artifacts"), repository.artifactUrls());
  }

  public void testParseCustomMavenRepository() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    name \"myRepoName\"\n" +
                  "    url \"http://repo.mycompany.com/maven2\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof MavenRepositoryModel);
    MavenRepositoryModel repository = (MavenRepositoryModel)repositoryModel;
    assertNotNull("name", repository.name().getPsiElement());
    assertEquals("name", "myRepoName", repository.name().value());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://repo.mycompany.com/maven2", repository.url().value());
  }

  public void testParseMavenRepositoryWithArtifactUrls() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    // Look for POMs and artifacts, such as JARs, here\n" +
                  "    url \"http://repo2.mycompany.com/maven2\"\n" +
                  "    // Look for artifacts here if not found at the above location\n" +
                  "    artifactUrls \"http://repo.mycompany.com/jars\"\n" +
                  "    artifactUrls \"http://repo.mycompany.com/jars2\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof MavenRepositoryModel);
    MavenRepositoryModel repository = (MavenRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "maven", repository.name().value());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://repo2.mycompany.com/maven2", repository.url().value());
    assertEquals("artifactUrls",
                 ImmutableList.of("http://repo.mycompany.com/jars", "http://repo.mycompany.com/jars2"),
                 repository.artifactUrls());
  }

  public void testParseMavenRepositoryWithCredentials() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    credentials {\n" +
                  "      username 'user'\n" +
                  "      password 'password123'\n" +
                  "    }\n" +
                  "    url \"http://repo.mycompany.com/maven2\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof MavenRepositoryModel);
    MavenRepositoryModel repository = (MavenRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "maven", repository.name().value());
    assertNotNull("url", repository.url().getPsiElement());
    assertEquals("url", "http://repo.mycompany.com/maven2", repository.url().value());
    assertThat(repository.artifactUrls()).isEmpty();

    MavenCredentialsModel credentials = repository.credentials();
    assertNotNull(credentials);
    assertEquals("username", "user", credentials.username());
    assertEquals("password", "password123", credentials.password());
  }

  public void testParseFlatDirRepository() throws IOException {
    String text = "repositories {\n" +
                  "  flatDir {\n" +
                  "    dirs 'lib1', 'lib2'\n" +
                  "   }\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof FlatDirRepositoryModel);
    FlatDirRepositoryModel repository = (FlatDirRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "flatDir", repository.name().value());
    assertEquals("dirs", ImmutableList.of("lib1", "lib2"), repository.dirs());
  }

  public void testParseFlatDirRepositoryWithSingleDirArgument() throws IOException {
    String text = "repositories {\n" +
                  "   flatDir name: 'libs', dirs: \"libs\"\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof FlatDirRepositoryModel);
    FlatDirRepositoryModel repository = (FlatDirRepositoryModel)repositoryModel;
    assertNotNull("name", repository.name().getPsiElement());
    assertEquals("name", "libs", repository.name().value());
    assertEquals("dirs", ImmutableList.of("libs"), repository.dirs());
  }

  public void testParseFlatDirRepositoryWithDirListArgument() throws IOException {
    String text = "repositories {\n" +
                  "   flatDir dirs: [\"libs1\", \"libs2\"]\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof FlatDirRepositoryModel);
    FlatDirRepositoryModel repository = (FlatDirRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "flatDir", repository.name().value());
    assertEquals("dirs", ImmutableList.of("libs1", "libs2"), repository.dirs());
  }

  public void testParseMultipleRepositories() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "  mavenCentral()\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(2);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));

    RepositoryModel mavenCentral = repositories.get(1);
    assertTrue(mavenCentral instanceof MavenCentralRepositoryModel);
    MavenCentralRepositoryModel mavenCentralRepository = (MavenCentralRepositoryModel)mavenCentral;
    assertEquals("name", "MavenRepo", mavenCentralRepository.name());
    assertEquals("url", "https://repo1.maven.org/maven2/", mavenCentralRepository.url());
  }

  public void testParseGoogleDefaultRepository() throws IOException {
    String text = "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);

    RepositoriesModel repositoriesModel = getGradleBuildModel().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleDefaultRepositoryModel(repositories.get(0));
  }

  public void testAddGoogleRepositoryByMethodCall() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
    verifyAddGoogleRepositoryByMethodCall();
    repositories = buildModel.repositories().repositories();
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
  }

  public void testAddGoogleRepositoryByMethodCallEmpty() throws IOException {
    String text = "repositories {\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
    verifyAddGoogleRepositoryByMethodCall();
  }

  public void testAddGoogleRepositoryToEmptyBuildscript() throws IOException {
    String text = "buildscript {\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.buildscript().repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(0);

    repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);

    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.buildscript().repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleDefaultRepositoryModel(repositories.get(0));
  }

  public void testAddGoogleRepositoryByMethodCallPresent() throws IOException {
    String text = "repositories {\n" +
                  "  google()\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleDefaultRepositoryModel(repositories.get(0));

    repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    assertFalse(buildModel.isModified());
  }

  public void testAddGoogleRepositoryByUrl() throws IOException {
    String text = "repositories {\n" +
                  "  jcenter()\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(1);
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
    verifyAddGoogleRepositoryByUrl();
    repositories = buildModel.repositories().repositories();
    verifyJCenterDefaultRepositoryModel(repositories.get(0));
  }

  public void testAddGoogleRepositoryByUrlEmpty() throws IOException {
    String text = "repositories {\n" +
                  "}";
    writeToBuildFile(text);
    GradleBuildModel buildModel = getGradleBuildModel();
    List<RepositoryModel> repositories = buildModel.repositories().repositories();
    assertThat(repositories).hasSize(0);
    verifyAddGoogleRepositoryByUrl();
  }

  public void testAddGoogleRepositoryByUrlPresent() throws IOException {
    String text = "repositories {\n" +
                  "  maven {\n" +
                  "    url \"" + GOOGLE_DEFAULT_REPO_URL + "\"\n" +
                  "    name \"" + GOOGLE_DEFAULT_REPO_NAME + "\"\n" +
                  "  }\n" +
                  "}";
    writeToBuildFile(text);

    GradleBuildModel buildModel = getGradleBuildModel();
    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(1);
    verifyGoogleMavenRepositoryModel(repositories.get(0));

    repositoriesModel.addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    assertFalse(buildModel.isModified());
  }

  private static void verifyGoogleDefaultRepositoryModel(RepositoryModel google) {
    assertTrue(google instanceof GoogleDefaultRepositoryModel);
    GoogleDefaultRepositoryModel googleRepository = (GoogleDefaultRepositoryModel)google;
    assertEquals("name", GOOGLE_DEFAULT_REPO_NAME, googleRepository.name());
    assertEquals("url", GOOGLE_DEFAULT_REPO_URL, googleRepository.url());
  }

  private static void verifyJCenterDefaultRepositoryModel(RepositoryModel jcenter) {
    assertTrue(jcenter instanceof JCenterDefaultRepositoryModel);
    JCenterDefaultRepositoryModel jCenterRepository = (JCenterDefaultRepositoryModel)jcenter;
    assertNull("name", jCenterRepository.name().getPsiElement());
    assertEquals("name", "BintrayJCenter2", jCenterRepository.name().value());
    assertNull("url", jCenterRepository.url().getPsiElement());
    assertEquals("url", "https://jcenter.bintray.com/", jCenterRepository.url().value());
  }

  private void verifyAddGoogleRepositoryByMethodCall() {
    GradleBuildModel buildModel = getGradleBuildModel();

    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    int prevSize = repositories.size();

    repositoriesModel.addRepositoryByMethodName(GOOGLE_METHOD_NAME);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(prevSize + 1);
    verifyGoogleDefaultRepositoryModel(repositories.get(prevSize));
  }

  private void verifyAddGoogleRepositoryByUrl() {
    GradleBuildModel buildModel = getGradleBuildModel();

    RepositoriesModel repositoriesModel = buildModel.repositories();
    List<RepositoryModel> repositories = repositoriesModel.repositories();
    int prevSize = repositories.size();

    repositoriesModel.addMavenRepositoryByUrl(GOOGLE_DEFAULT_REPO_URL, GOOGLE_DEFAULT_REPO_NAME);
    assertTrue(buildModel.isModified());
    applyChangesAndReparse(buildModel);
    assertFalse(buildModel.isModified());

    repositoriesModel = buildModel.repositories();
    repositories = repositoriesModel.repositories();
    assertThat(repositories).hasSize(prevSize + 1);
    verifyGoogleMavenRepositoryModel(repositories.get(prevSize));
  }

   private static void verifyGoogleMavenRepositoryModel(RepositoryModel repository) {
     assertTrue(repository instanceof MavenRepositoryModel);
     MavenRepositoryModel mavenRepository = (MavenRepositoryModel)repository;
     assertNotNull("url", mavenRepository.url().getPsiElement());
     assertEquals("url", GOOGLE_DEFAULT_REPO_URL, mavenRepository.url().value());
     assertNotNull("name", mavenRepository.name().getPsiElement());
     assertEquals("name", GOOGLE_DEFAULT_REPO_NAME, mavenRepository.name().value());
   }
}
