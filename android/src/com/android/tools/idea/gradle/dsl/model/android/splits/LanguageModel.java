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
package com.android.tools.idea.gradle.dsl.model.android.splits;

import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.model.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.model.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.parser.android.splits.LanguageDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class LanguageModel extends GradleDslBlockModel {
  @NonNls private static final String ENABLE = "enable";
  @NonNls private static final String INCLUDE = "include";

  public LanguageModel(@NotNull LanguageDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public GradleNullableValue<Boolean> enable() {
    return myDslElement.getLiteralProperty(ENABLE, Boolean.class);
  }

  @NotNull
  public LanguageModel setEnable(boolean enable) {
    myDslElement.setNewLiteral(ENABLE, enable);
    return this;
  }

  @NotNull
  public LanguageModel removeEnable() {
    myDslElement.removeProperty(ENABLE);
    return this;
  }

  @Nullable
  public List<GradleNotNullValue<String>> include() {
    return myDslElement.getListProperty(INCLUDE, String.class);
  }

  @NotNull
  public LanguageModel addInclude(@NotNull String include) {
    myDslElement.addToNewLiteralList(INCLUDE, include);
    return this;
  }

  @NotNull
  public LanguageModel removeInclude(@NotNull String include) {
    myDslElement.removeFromExpressionList(INCLUDE, include);
    return this;
  }

  @NotNull
  public LanguageModel removeAllInclude() {
    myDslElement.removeProperty(INCLUDE);
    return this;
  }

  @NotNull
  public LanguageModel replaceInclude(@NotNull String oldInclude, @NotNull String newInclude) {
    myDslElement.replaceInExpressionList(INCLUDE, oldInclude, newInclude);
    return this;
  }
}
