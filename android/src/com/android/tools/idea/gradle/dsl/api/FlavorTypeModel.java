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

import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

public interface FlavorTypeModel {
  @NotNull
  String name();

  @Nullable
  List<GradleNotNullValue<String>> consumerProguardFiles();

  void addConsumerProguardFile(@NotNull String consumerProguardFile);

  void removeConsumerProguardFile(@NotNull String consumerProguardFile);

  void removeAllConsumerProguardFiles();

  void replaceConsumerProguardFile(@NotNull String oldConsumerProguardFile, @NotNull String newConsumerProguardFile);

  @Nullable
  Map<String, GradleNotNullValue<Object>> manifestPlaceholders();

  void setManifestPlaceholder(@NotNull String name, @NotNull String value);

  void setManifestPlaceholder(@NotNull String name, int value);

  void setManifestPlaceholder(@NotNull String name, boolean value);

  void removeManifestPlaceholder(@NotNull String name);

  void removeAllManifestPlaceholders();

  @NotNull
  ResolvedPropertyModel multiDexEnabled();

  void removeMultiDexEnabled();

  @Nullable
  List<GradleNotNullValue<String>> proguardFiles();

  void addProguardFile(@NotNull String proguardFile);

  void removeProguardFile(@NotNull String proguardFile);

  void removeAllProguardFiles();

  void replaceProguardFile(@NotNull String oldProguardFile, @NotNull String newProguardFile);

  @Nullable
  List<GradleNotNullValue<ResValue>> resValues();

  void addResValue(@NotNull ResValue resValue);

  void removeResValue(@NotNull ResValue resValue);

  void removeAllResValues();

  void replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue);

  @NotNull
  ResolvedPropertyModel useJack();

  void removeUseJack();

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
