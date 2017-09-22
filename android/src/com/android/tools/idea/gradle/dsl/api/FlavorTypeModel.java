/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.api;

import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import com.android.tools.idea.gradle.dsl.api.values.GradleNullableValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface FlavorTypeModel {
  @NotNull
  String name();

  @Nullable
  List<GradleNotNullValue<String>> consumerProguardFiles();

  @NotNull
  FlavorTypeModel addConsumerProguardFile(@NotNull String consumerProguardFile);

  @NotNull
  FlavorTypeModel removeConsumerProguardFile(@NotNull String consumerProguardFile);

  @NotNull
  FlavorTypeModel removeAllConsumerProguardFiles();

  @NotNull
  FlavorTypeModel replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile, @NotNull String newConsumerProguardFile);

  @Nullable
  Map<String, GradleNotNullValue<Object>> manifestPlaceholders();

  @NotNull
  FlavorTypeModel setManifestPlaceholder(@NotNull String name, @NotNull String value);

  @NotNull
  FlavorTypeModel setManifestPlaceholder(@NotNull String name, int value);

  @NotNull
  FlavorTypeModel setManifestPlaceholder(@NotNull String name, boolean value);

  @NotNull
  FlavorTypeModel removeManifestPlaceholder(@NotNull String name);

  @NotNull
  FlavorTypeModel removeAllManifestPlaceholders();

  @NotNull
  GradleNullableValue<Boolean> multiDexEnabled();

  @NotNull
  FlavorTypeModel setMultiDexEnabled(boolean multiDexEnabled);

  @NotNull
  FlavorTypeModel removeMultiDexEnabled();

  @Nullable
  List<GradleNotNullValue<String>> proguardFiles();

  @NotNull
  FlavorTypeModel addProguardFile(@NotNull String proguardFile);

  @NotNull
  FlavorTypeModel removeProguardFile(@NotNull String proguardFile);

  @NotNull
  FlavorTypeModel removeAllProguardFiles();

  @NotNull
  FlavorTypeModel replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile);

  @Nullable
  List<GradleNotNullValue<ResValue>> resValues();

  @NotNull
  FlavorTypeModel addResValue(@NotNull ResValue resValue);

  @NotNull
  FlavorTypeModel removeResValue(@NotNull ResValue resValue);

  @NotNull
  FlavorTypeModel removeAllResValues();

  @NotNull
  FlavorTypeModel replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue);

  @NotNull
  GradleNullableValue<Boolean> useJack();

  @NotNull
  FlavorTypeModel setUseJack(boolean useJack);

  @NotNull
  FlavorTypeModel removeUseJack();

  /**
   * Represents a statement like {@code resValue} or {@code buildConfigField} which contains type, name and value parameters.
   */
  interface TypeNameValueElement {
    @NotNull
    String name();

    @NotNull
    String value();

    @NotNull
    String type();

    @NotNull
    String elementName();
  }

  /**
   * Represents a {@code resValue} statement defined in the product flavor block of the Gradle file.
   */
  interface ResValue extends TypeNameValueElement {
  }
}
