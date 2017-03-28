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
import com.android.tools.idea.gradle.dsl.parser.android.splits.AbiDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AbiModel extends GradleDslBlockModel {
  @NonNls private static final String ENABLE = "enable";
  @NonNls private static final String EXCLUDE = "exclude";
  @NonNls private static final String INCLUDE = "include";
  @NonNls private static final String RESET = "reset";
  @NonNls private static final String UNIVERSAL_APK = "universalApk";

  public AbiModel(@NotNull AbiDslElement dslElement) {
    super(dslElement);
  }

  @NotNull
  public GradleNullableValue<Boolean> enable() {
    return myDslElement.getLiteralProperty(ENABLE, Boolean.class);
  }

  @NotNull
  public AbiModel setEnable(boolean enable) {
    myDslElement.setNewLiteral(ENABLE, enable);
    return this;
  }

  @NotNull
  public AbiModel removeEnable() {
    myDslElement.removeProperty(ENABLE);
    return this;
  }

  @Nullable
  public List<GradleNotNullValue<String>> exclude() {
    return myDslElement.getListProperty(EXCLUDE, String.class);
  }

  @NotNull
  public AbiModel addExclude(@NotNull String exclude) {
    myDslElement.addToNewLiteralList(EXCLUDE, exclude);
    return this;
  }

  @NotNull
  public AbiModel removeExclude(@NotNull String exclude) {
    myDslElement.removeFromExpressionList(EXCLUDE, exclude);
    return this;
  }

  @NotNull
  public AbiModel removeAllExclude() {
    myDslElement.removeProperty(EXCLUDE);
    return this;
  }

  @NotNull
  public AbiModel replaceExclude(@NotNull String oldExclude, @NotNull String newExclude) {
    myDslElement.replaceInExpressionList(EXCLUDE, oldExclude, newExclude);
    return this;
  }

  @Nullable
  public List<GradleNotNullValue<String>> include() {
    return myDslElement.getListProperty(INCLUDE, String.class);
  }

  @NotNull
  public AbiModel addInclude(@NotNull String include) {
    myDslElement.addToNewLiteralList(INCLUDE, include);
    return this;
  }

  @NotNull
  public AbiModel removeInclude(@NotNull String include) {
    myDslElement.removeFromExpressionList(INCLUDE, include);
    return this;
  }

  @NotNull
  public AbiModel removeAllInclude() {
    myDslElement.removeProperty(INCLUDE);
    return this;
  }

  @NotNull
  public AbiModel replaceInclude(@NotNull String oldInclude, @NotNull String newInclude) {
    myDslElement.replaceInExpressionList(INCLUDE, oldInclude, newInclude);
    return this;
  }

  @NotNull
  public AbiModel addReset() {
    GradleDslMethodCall resetMethod = new GradleDslMethodCall(myDslElement, RESET, null);
    myDslElement.setNewElement(RESET, resetMethod); // TODO: reset include
    return this;
  }

  @NotNull
  public AbiModel removeReset() {
    myDslElement.removeProperty(RESET);
    return this;
  }

  @NotNull
  public GradleNullableValue<Boolean> universalApk() {
    return myDslElement.getLiteralProperty(UNIVERSAL_APK, Boolean.class);
  }

  @NotNull
  public AbiModel setUniversalApk(boolean universalApk) {
    myDslElement.setNewLiteral(UNIVERSAL_APK, universalApk);
    return this;
  }

  @NotNull
  public AbiModel removeUniversalApk() {
    myDslElement.removeProperty(UNIVERSAL_APK);
    return this;
  }
}
