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
package com.android.tools.idea.gradle.structure.model;

import org.jetbrains.annotations.NotNull;

import static com.android.tools.idea.gradle.structure.model.PsPath.TexType.FOR_COMPARE_TO;

public class TestPath extends PsPath {
  @NotNull private final String myComparisonValue;
  @NotNull private final String myOtherValue;

  public TestPath(@NotNull String path) {
    this(path, path);
  }

  public TestPath(@NotNull String comparisonValue, @NotNull String otherValue) {
    myComparisonValue = comparisonValue;
    myOtherValue = otherValue;
  }

  @NotNull
  @Override
  public String toText(@NotNull TexType type) {
      return type == FOR_COMPARE_TO ? myComparisonValue : myOtherValue;
    }
}
