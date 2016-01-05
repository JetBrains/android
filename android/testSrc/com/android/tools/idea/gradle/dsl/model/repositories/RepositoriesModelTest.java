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

import static org.fest.assertions.Assertions.assertThat;

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
    assertEquals("name", "BintrayJCenter2", repositoryModel.name());
    assertEquals("url", "https://jcenter.bintray.com/", repositoryModel.url());
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
    assertEquals("name", "BintrayJCenter2", repositoryModel.name());
    assertEquals("url", "http://jcenter.bintray.com/", repositoryModel.url());
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
    assertEquals("name", "MavenRepo", repositoryModel.name());
    assertEquals("url", "https://repo1.maven.org/maven2/", repositoryModel.url());
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
    assertEquals("name", "myRepoName", repositoryModel.name());
    assertEquals("url", "http://repo.mycompany.com/maven2", repositoryModel.url());
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
    MavenRepositoryModel mavenRepositoryModel = (MavenRepositoryModel)repositoryModel;
    assertEquals("name", "maven", mavenRepositoryModel.name());
    assertEquals("url", "http://repo2.mycompany.com/maven2", mavenRepositoryModel.url());
    assertEquals("artifactUrls",
                 ImmutableList.of("http://repo.mycompany.com/jars", "http://repo.mycompany.com/jars2"),
                 mavenRepositoryModel.artifactUrls());
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
    MavenRepositoryModel mavenRepositoryModel = (MavenRepositoryModel)repositoryModel;
    assertEquals("name", "maven", mavenRepositoryModel.name());
    assertEquals("url", "http://repo.mycompany.com/maven2", mavenRepositoryModel.url());
    assertThat(mavenRepositoryModel.artifactUrls()).isEmpty();

    MavenCredentialsModel credentials = mavenRepositoryModel.credentials();
    assertNotNull(credentials);
    assertEquals("username", "user", credentials.username());
    assertEquals("password", "password123", credentials.password());
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
    assertEquals("name", "BintrayJCenter2", jcenter.name());
    assertEquals("url", "https://jcenter.bintray.com/", jcenter.url());

    RepositoryModel mavenCentral = repositories.get(1);
    assertTrue(mavenCentral instanceof MavenCentralRepositoryModel);
    assertEquals("name", "MavenRepo", mavenCentral.name());
    assertEquals("url", "https://repo1.maven.org/maven2/", mavenCentral.url());
  }
}
