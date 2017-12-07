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

import com.android.tools.idea.gradle.dsl.api.repositories.UrlBasedRepositoryModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleDefaultValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleDefaultValueImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for all the url based repository models like Maven and JCenter.
 */
public abstract class UrlBasedRepositoryModelImpl extends RepositoryModelImpl implements UrlBasedRepositoryModel {
  @NonNls private static final String URL = "url";

  @NotNull private final String myDefaultRepoUrl;

  protected UrlBasedRepositoryModelImpl(@Nullable GradlePropertiesDslElement dslElement,
                                        @NotNull String defaultRepoName,
                                        @NotNull String defaultRepoUrl) {
    super(dslElement, defaultRepoName);
    myDefaultRepoUrl = defaultRepoUrl;
  }

  @Override
  @NotNull
  public GradleDefaultValue<String> url() {
    if (myDslElement == null) {
      return new GradleDefaultValueImpl<>(null, myDefaultRepoUrl);
    }

    GradleDslExpression nameExpression = myDslElement.getPropertyElement(URL, GradleDslExpression.class);

    String url = null;
    if (nameExpression != null) {
      url = nameExpression.getValue(String.class);
    }
    if (url == null) {
      url = myDefaultRepoUrl;
    }

    return new GradleDefaultValueImpl<>(nameExpression, url);
  }
}
