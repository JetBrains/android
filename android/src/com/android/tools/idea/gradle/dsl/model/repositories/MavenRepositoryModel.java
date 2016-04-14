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

import com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement;
import com.android.tools.idea.gradle.dsl.parser.repositories.MavenRepositoryDslElement;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.parser.repositories.MavenCredentialsDslElement.CREDENTIALS_BLOCK_NAME;

/**
 * Represents a repository defined with maven {}.
 */
public class MavenRepositoryModel extends UrlBasedRepositoryModel {
  @NonNls private static final String URL = "url";
  @NonNls private static final String NAME = "name";
  @NonNls private static final String ARTIFACT_URLS = "artifactUrls";

  @NotNull private final MavenRepositoryDslElement myDslElement;
  @NotNull private final String myDefaultRepoName;
  @NotNull private final String myDefaultRepoUrl;

  public MavenRepositoryModel(@NotNull MavenRepositoryDslElement dslElement) {
    this(dslElement, "maven", "https://repo1.maven.org/maven2/");
  }

  protected MavenRepositoryModel(@NotNull MavenRepositoryDslElement dslElement,
                                 @NotNull String defaultRepoName,
                                 @NotNull String defaultRepoUrl) {
    myDslElement = dslElement;
    myDefaultRepoName = defaultRepoName;
    myDefaultRepoUrl = defaultRepoUrl;
  }

  @NotNull
  @Override
  public String name() {
    String name = myDslElement.getProperty(NAME, String.class);
    return name != null ? name : myDefaultRepoName;
  }

  @NotNull
  @Override
  public String url() {
    String url = myDslElement.getProperty(URL, String.class);
    return url != null ? url : myDefaultRepoUrl;
  }

  @NotNull
  public List<String> artifactUrls() {
    List<String> artifactUrls = myDslElement.getListProperty(ARTIFACT_URLS, String.class);
    return artifactUrls != null ? artifactUrls : ImmutableList.<String>of();
  }

  @Nullable
  public MavenCredentialsModel credentials() {
    MavenCredentialsDslElement credentials = myDslElement.getProperty(CREDENTIALS_BLOCK_NAME, MavenCredentialsDslElement.class);
    return credentials != null ? new MavenCredentialsModel(credentials) : null;
  }
}
