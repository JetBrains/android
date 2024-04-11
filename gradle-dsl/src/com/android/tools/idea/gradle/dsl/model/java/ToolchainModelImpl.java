/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.java;

import com.android.tools.idea.gradle.dsl.api.java.JavaLanguageVersionPropertyModel;
import com.android.tools.idea.gradle.dsl.api.java.ToolchainModel;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class ToolchainModelImpl extends GradleDslBlockModel implements ToolchainModel {

  @NonNls public static final String LANGUAGE_VERSION = "mLanguageVersion";

  public ToolchainModelImpl(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @Override
  public @NotNull JavaLanguageVersionPropertyModel languageVersion() {
    return new JavaLanguageVersionPropertyModelImpl(GradlePropertyModelBuilder.create(myDslElement, LANGUAGE_VERSION)
      .build());
  }
}
