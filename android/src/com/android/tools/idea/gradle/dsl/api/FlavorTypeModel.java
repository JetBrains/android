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
import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import com.android.tools.idea.gradle.dsl.api.values.GradleNotNullValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface FlavorTypeModel extends GradleDslModel {
  @NotNull
  String name();

  @Nullable
  ResolvedPropertyModel consumerProguardFiles();

  @Nullable
  ResolvedPropertyModel manifestPlaceholders();

  @NotNull
  ResolvedPropertyModel multiDexEnabled();

  @Nullable
  ResolvedPropertyModel proguardFiles();

  @Nullable
  List<GradleNotNullValue<ResValue>> resValues();

  void addResValue(@NotNull ResValue resValue);

  void removeResValue(@NotNull ResValue resValue);

  void removeAllResValues();

  void replaceResValue(@NotNull ResValue oldResValue, @NotNull ResValue newResValue);

  @NotNull
  ResolvedPropertyModel useJack();

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
