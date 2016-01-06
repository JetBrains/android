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

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a repository defined with mavenCentral().
 */
public class MavenCentralRepositoryModel extends UrlBasedRepositoryModel {
  @NonNls public static final String MAVEN_CENTRAL_METHOD_NAME = "mavenCentral";

  @NonNls private static final String DEFAULT_REPO_NAME = "MavenRepo";
  @NonNls private static final String DEFAULT_REPO_URL = "https://repo1.maven.org/maven2/";

  @NonNls private static final String NAME = "name";
  @NonNls private static final String ARTIFACT_URLS = "artifactUrls";

  @Nullable
  private final GradleDslExpressionMap myDslElement;

  public MavenCentralRepositoryModel() {
    myDslElement = null;
  }

  public MavenCentralRepositoryModel(@NotNull GradleDslExpressionMap dslElement) {
    myDslElement = dslElement;
  }

  @NotNull
  @Override
  public String name() {
    if (myDslElement == null) {
      return DEFAULT_REPO_NAME;
    }

    String name = myDslElement.getProperty(NAME, String.class);
    return name != null ? name : DEFAULT_REPO_NAME;
  }

  @NotNull
  @Override
  public String url() {
    return DEFAULT_REPO_URL;
  }

  @NotNull
  public List<String> artifactUrls() {
    if (myDslElement == null) {
      return ImmutableList.of();
    }

    List<String> artifactUrls = myDslElement.getListProperty(ARTIFACT_URLS, String.class);
    if (artifactUrls != null) {
      return artifactUrls;
    }

    String artifactUrl = myDslElement.getProperty(ARTIFACT_URLS, String.class);
    if (artifactUrl != null) {
      return ImmutableList.of(artifactUrl);
    }

    return ImmutableList.of();
  }
}