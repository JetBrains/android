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

import com.android.tools.idea.gradle.dsl.api.repositories.RepositoryModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleDefaultValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleDefaultValueImpl;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base class for all the repository models.
 */
public abstract class RepositoryModelImpl implements RepositoryModel {
  @NonNls private static final String NAME = "name";

  @NotNull private final String myDefaultRepoName;

  @Nullable protected final GradlePropertiesDslElement myDslElement;

  protected RepositoryModelImpl(@Nullable GradlePropertiesDslElement dslElement, @NotNull String defaultRepoName) {
    myDslElement = dslElement;
    myDefaultRepoName = defaultRepoName;
  }

  @NotNull
  @Override
  public GradleDefaultValue<String> name() {
    if (myDslElement == null) {
      return new GradleDefaultValueImpl<>(null, myDefaultRepoName);
    }

    GradleDslExpression nameExpression = myDslElement.getPropertyElement(NAME, GradleDslExpression.class);

    String name = null;
    if (nameExpression != null) {
      name = nameExpression.getValue(String.class);
    }
    if (name == null) {
      name = myDefaultRepoName;
    }

    return new GradleDefaultValueImpl<>(nameExpression, name);
  }
}
