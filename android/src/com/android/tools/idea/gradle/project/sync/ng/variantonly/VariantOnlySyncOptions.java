/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.gradle.project.sync.ng.variantonly;

import com.android.tools.layoutlib.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.Serializable;
import java.util.Objects;

public class VariantOnlySyncOptions implements Serializable {
  @NotNull public final File myBuildId;
  @NotNull public final String myGradlePath;
  @NotNull public final String myVariantName;
  @Nullable public final String myAbiName;

  public VariantOnlySyncOptions(@NotNull File buildId, @NotNull String gradlePath, @NotNull String variantName) {
    this(buildId, gradlePath, variantName, null);
  }

  public VariantOnlySyncOptions(@NotNull File buildId, @NotNull String gradlePath, @NotNull String variantName, @Nullable String abiName) {
    myBuildId = buildId;
    myGradlePath = gradlePath;
    myVariantName = variantName;
    myAbiName = abiName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    VariantOnlySyncOptions options = (VariantOnlySyncOptions)o;
    return Objects.equals(myBuildId, options.myBuildId) &&
           Objects.equals(myGradlePath, options.myGradlePath) &&
           Objects.equals(myVariantName, options.myVariantName) &&
           Objects.equals(myAbiName, options.myAbiName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myBuildId, myGradlePath, myVariantName, myAbiName);
  }

  @Override
  public String toString() {
    return "VariantOnlySyncOptions{" +
           "myBuildId=" + myBuildId +
           ", myGradlePath='" + myGradlePath + '\'' +
           ", myVariantName='" + myVariantName + '\'' +
           ", myAbiName='" + myAbiName + '\'' +
           '}';
  }
}
