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

import com.android.tools.idea.gradle.dsl.api.android.splits.DensityModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DensityModelImpl extends GradleDslBlockModel implements DensityModel {
  @NonNls private static final String AUTO = "auto";
  @NonNls private static final String COMPATIBLE_SCREENS = "compatibleScreens";
  @NonNls private static final String ENABLE = "enable";
  @NonNls private static final String EXCLUDE = "exclude";
  @NonNls private static final String INCLUDE = "include";
  @NonNls private static final String RESET = "reset";

  public DensityModelImpl(@NotNull GradlePropertiesDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> auto() {
    return myDslElement.getLiteralProperty(AUTO, Boolean.class);
  }

  @Override
  @NotNull
  public DensityModel setAuto(boolean auto) {
    myDslElement.setNewLiteral(AUTO, auto);
    return this;
  }

  @Override
  @NotNull
  public DensityModel removeAuto() {
    myDslElement.removeProperty(AUTO);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> compatibleScreens() {
    return myDslElement.getListProperty(COMPATIBLE_SCREENS, String.class);
  }

  @Override
  @NotNull
  public DensityModel addCompatibleScreen(@NotNull String compatibleScreen) {
    myDslElement.addToNewLiteralList(COMPATIBLE_SCREENS, compatibleScreen);
    return this;
  }

  @Override
  @NotNull
  public DensityModel removeCompatibleScreen(@NotNull String compatibleScreen) {
    myDslElement.removeFromExpressionList(COMPATIBLE_SCREENS, compatibleScreen);
    return this;
  }

  @Override
  @NotNull
  public DensityModel removeAllCompatibleScreens() {
    myDslElement.removeProperty(COMPATIBLE_SCREENS);
    return this;
  }

  @Override
  @NotNull
  public DensityModel replaceCompatibleScreen(@NotNull String oldCompatibleScreen, @NotNull String newCompatibleScreen) {
    myDslElement.replaceInExpressionList(COMPATIBLE_SCREENS, oldCompatibleScreen, newCompatibleScreen);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> enable() {
    return myDslElement.getLiteralProperty(ENABLE, Boolean.class);
  }

  @Override
  @NotNull
  public DensityModel setEnable(boolean enable) {
    myDslElement.setNewLiteral(ENABLE, enable);
    return this;
  }

  @Override
  @NotNull
  public DensityModel removeEnable() {
    myDslElement.removeProperty(ENABLE);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> exclude() {
    return myDslElement.getListProperty(EXCLUDE, String.class);
  }

  @Override
  @NotNull
  public DensityModel addExclude(@NotNull String exclude) {
    myDslElement.addToNewLiteralList(EXCLUDE, exclude);
    return this;
  }

  @Override
  @NotNull
  public DensityModel removeExclude(@NotNull String exclude) {
    myDslElement.removeFromExpressionList(EXCLUDE, exclude);
    return this;
  }

  @Override
  @NotNull
  public DensityModel removeAllExclude() {
    myDslElement.removeProperty(EXCLUDE);
    return this;
  }

  @Override
  @NotNull
  public DensityModel replaceExclude(@NotNull String oldExclude, @NotNull String newExclude) {
    myDslElement.replaceInExpressionList(EXCLUDE, oldExclude, newExclude);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> include() {
    return myDslElement.getListProperty(INCLUDE, String.class);
  }

  @Override
  @NotNull
  public DensityModel addInclude(@NotNull String include) {
    myDslElement.addToNewLiteralList(INCLUDE, include);
    return this;
  }

  @Override
  @NotNull
  public DensityModel removeInclude(@NotNull String include) {
    myDslElement.removeFromExpressionList(INCLUDE, include);
    return this;
  }

  @Override
  @NotNull
  public DensityModel removeAllInclude() {
    myDslElement.removeProperty(INCLUDE);
    return this;
  }

  @Override
  @NotNull
  public DensityModel replaceInclude(@NotNull String oldInclude, @NotNull String newInclude) {
    myDslElement.replaceInExpressionList(INCLUDE, oldInclude, newInclude);
    return this;
  }

  @Override
  @NotNull
  public DensityModel addReset() {
    GradleDslMethodCall resetMethod = new GradleDslMethodCall(myDslElement, GradleNameElement.create(RESET), null);
    myDslElement.setNewElement(RESET, resetMethod); // TODO: reset include
    return this;
  }

  @Override
  @NotNull
  public DensityModel removeReset() {
    myDslElement.removeProperty(RESET);
    return this;
  }
}
