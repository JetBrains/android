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

import com.android.tools.idea.gradle.dsl.model.GradleFileModelTestCase;
import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;

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
    RepositoryModel repositoryModel = repositories.get(0);
    assertTrue(repositoryModel instanceof JCenterDefaultRepositoryModel);
    JCenterDefaultRepositoryModel repository = (JCenterDefaultRepositoryModel)repositoryModel;
    assertNull("name", repository.name().getPsiElement());
    assertEquals("name", "BintrayJCenter2", repository.name().value());
    assertNull("url", repository.url().getPsiElement());
    assertEquals("url", "https://jcenter.bintray.com/", repository.url().value());
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
    RepositoryModel jcenter = repositories.get(0);
    assertTrue(jcenter instanceof JCenterDefaultRepositoryModel);
    JCenterDefaultRepositoryModel jCenterRepository = (JCenterDefaultRepositoryModel)jcenter;
    assertNull("name", jCenterRepository.name().getPsiElement());
    assertEquals("name", "BintrayJCenter2", jCenterRepository.name().value());
    assertNull("url", jCenterRepository.url().getPsiElement());
    assertEquals("url", "https://jcenter.bintray.com/", jCenterRepository.url().value());

    RepositoryModel mavenCentral = repositories.get(1);
    assertTrue(mavenCentral instanceof MavenCentralRepositoryModel);
    MavenCentralRepositoryModel mavenCentralRepository = (MavenCentralRepositoryModel)mavenCentral;
    assertEquals("name", "MavenRepo", mavenCentralRepository.name());
    assertEquals("url", "https://repo1.maven.org/maven2/", mavenCentralRepository.url());
  }
}
