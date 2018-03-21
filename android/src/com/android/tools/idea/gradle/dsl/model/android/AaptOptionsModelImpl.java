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
package com.android.tools.idea.gradle.dsl.model.android;

import com.android.tools.idea.gradle.dsl.api.android.AaptOptionsModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.AaptOptionsDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class AaptOptionsModelImpl extends GradleDslBlockModel implements AaptOptionsModel {
  @NonNls private static final String ADDITIONAL_PARAMETERS = "additionalParameters";
  @NonNls private static final String CRUNCHER_ENABLED = "cruncherEnabled";
  @NonNls private static final String CRUNCHER_PROCESSES = "cruncherProcesses";
  @NonNls private static final String FAIL_ON_MISSING_CONFIG_ENTRY = "failOnMissingConfigEntry";
  @NonNls private static final String IGNORE_ASSETS = "ignoreAssets";
  @NonNls private static final String NO_COMPRESS = "noCompress";

  public AaptOptionsModelImpl(@NotNull AaptOptionsDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> additionalParameters() {
    return myDslElement.getListProperty(ADDITIONAL_PARAMETERS, String.class);
  }

  @Override
  @NotNull
  public AaptOptionsModel addAdditionalParameter(@NotNull String additionalParameter) {
    myDslElement.addToNewLiteralList(ADDITIONAL_PARAMETERS, additionalParameter);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel removeAdditionalParameter(@NotNull String additionalParameter) {
    myDslElement.removeFromExpressionList(ADDITIONAL_PARAMETERS, additionalParameter);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel removeAllAdditionalParameters() {
    myDslElement.removeProperty(ADDITIONAL_PARAMETERS);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel replaceAdditionalParameter(@NotNull String oldAdditionalParameter, @NotNull String newAdditionalParameter) {
    myDslElement.replaceInExpressionList(ADDITIONAL_PARAMETERS, oldAdditionalParameter, newAdditionalParameter);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<String> ignoreAssets() {
    return myDslElement.getLiteralProperty(IGNORE_ASSETS, String.class);
  }

  @Override
  @NotNull
  public AaptOptionsModel setIgnoreAssets(@NotNull String ignoreAssets) {
    myDslElement.setNewLiteral(IGNORE_ASSETS, ignoreAssets);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel removeIgnoreAssets() {
    myDslElement.removeProperty(IGNORE_ASSETS);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> failOnMissingConfigEntry() {
    return myDslElement.getLiteralProperty(FAIL_ON_MISSING_CONFIG_ENTRY, Boolean.class);
  }

  @Override
  @NotNull
  public AaptOptionsModel setFailOnMissingConfigEntry(boolean failOnMissingConfigEntry) {
    myDslElement.setNewLiteral(FAIL_ON_MISSING_CONFIG_ENTRY, failOnMissingConfigEntry);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel removeFailOnMissingConfigEntry() {
    myDslElement.removeProperty(FAIL_ON_MISSING_CONFIG_ENTRY);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Integer> cruncherProcesses() {
    return myDslElement.getLiteralProperty(CRUNCHER_PROCESSES, Integer.class);
  }

  @Override
  @NotNull
  public AaptOptionsModel setCruncherProcesses(int cruncherProcesses) {
    myDslElement.setNewLiteral(CRUNCHER_PROCESSES, cruncherProcesses);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel removeCruncherProcesses() {
    myDslElement.removeProperty(CRUNCHER_PROCESSES);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> cruncherEnabled() {
    return myDslElement.getLiteralProperty(CRUNCHER_ENABLED, Boolean.class);
  }

  @Override
  @NotNull
  public AaptOptionsModel setCruncherEnabled(boolean cruncherEnabled) {
    myDslElement.setNewLiteral(CRUNCHER_ENABLED, cruncherEnabled);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel removeCruncherEnabled() {
    myDslElement.removeProperty(CRUNCHER_ENABLED);
    return this;
  }

  @Override
  @Nullable
  public List<GradleNotNullValue<String>> noCompress() {
    return myDslElement.getListProperty(NO_COMPRESS, String.class);
  }

  @Override
  @NotNull
  public AaptOptionsModel addNoCompress(@NotNull String noCompress) {
    myDslElement.addToNewLiteralList(NO_COMPRESS, noCompress);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel removeNoCompress(@NotNull String noCompress) {
    myDslElement.removeFromExpressionList(NO_COMPRESS, noCompress);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel removeAllNoCompress() {
    myDslElement.removeProperty(NO_COMPRESS);
    return this;
  }

  @Override
  @NotNull
  public AaptOptionsModel replaceNoCompress(@NotNull String oldNoCompress, @NotNull String newNoCompress) {
    myDslElement.replaceInExpressionList(NO_COMPRESS, oldNoCompress, newNoCompress);
    return this;
  }
}
