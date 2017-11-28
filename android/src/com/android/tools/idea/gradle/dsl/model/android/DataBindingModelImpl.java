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

import com.android.tools.idea.gradle.dsl.api.android.DataBindingModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import com.android.tools.idea.gradle.dsl.model.GradleDslBlockModel;
import com.android.tools.idea.gradle.dsl.parser.android.DataBindingDslElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class DataBindingModelImpl extends GradleDslBlockModel implements DataBindingModel {
  @NonNls private static final String ADD_DEFAULT_ADAPTERS = "addDefaultAdapters";
  @NonNls private static final String ENABLED = "enabled";
  @NonNls private static final String VERSION = "version";

  public DataBindingModelImpl(@NotNull DataBindingDslElement dslElement) {
    super(dslElement);
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> addDefaultAdapters() {
    return myDslElement.getLiteralProperty(ADD_DEFAULT_ADAPTERS, Boolean.class);
  }

  @Override
  @NotNull
  public DataBindingModel setAddDefaultAdapters(boolean addDefaultAdapters) {
    myDslElement.setNewLiteral(ADD_DEFAULT_ADAPTERS, addDefaultAdapters);
    return this;
  }

  @Override
  @NotNull
  public DataBindingModel removeAddDefaultAdapters() {
    myDslElement.removeProperty(ADD_DEFAULT_ADAPTERS);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<Boolean> enabled() {
    return myDslElement.getLiteralProperty(ENABLED, Boolean.class);
  }

  @Override
  @NotNull
  public DataBindingModel setEnabled(boolean enabled) {
    myDslElement.setNewLiteral(ENABLED, enabled);
    return this;
  }

  @Override
  @NotNull
  public DataBindingModel removeEnabled() {
    myDslElement.removeProperty(ENABLED);
    return this;
  }

  @Override
  @NotNull
  public GradleNullableValue<String> version() {
    return myDslElement.getLiteralProperty(VERSION, String.class);
  }

  @Override
  @NotNull
  public DataBindingModel setVersion(@NotNull String version) {
    myDslElement.setNewLiteral(VERSION, version);
    return this;
  }

  @Override
  @NotNull
  public DataBindingModel removeVersion() {
    myDslElement.removeProperty(VERSION);
    return this;
  }
}
