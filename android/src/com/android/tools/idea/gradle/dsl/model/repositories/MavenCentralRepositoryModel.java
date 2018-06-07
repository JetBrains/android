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

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValueImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionMap;
import com.google.common.collect.ImmutableList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents a repository defined with mavenCentral().
 */
public class MavenCentralRepositoryModel extends UrlBasedRepositoryModelImpl {
  @NonNls public static final String MAVEN_CENTRAL_METHOD_NAME = "mavenCentral";

  @NonNls private static final String ARTIFACT_URLS = "artifactUrls";

  public MavenCentralRepositoryModel(@Nullable GradleDslExpressionMap dslElement) {
    super(dslElement, "MavenRepo", "https://repo1.maven.org/maven2/");
  }

  @NotNull
  public List<GradleNotNullValue<String>> artifactUrls() {
    if (myDslElement == null) {
      return ImmutableList.of();
    }

    List<GradleNotNullValue<String>> artifactUrls = myDslElement.getListProperty(ARTIFACT_URLS, String.class);
    if (artifactUrls != null) {
      return artifactUrls;
    }

    GradleNullableValue<String> artifactUrl = myDslElement.getLiteralProperty(ARTIFACT_URLS, String.class);
    if (artifactUrl.value() != null) {
      assert artifactUrl instanceof GradleNotNullValueImpl;
      return ImmutableList.of((GradleNotNullValueImpl<String>)artifactUrl);
    }

    return ImmutableList.of();
  }

  @NotNull
  @Override
  public RepositoryType getType() {
    return RepositoryType.MAVEN_CENTRAL;
  }
}