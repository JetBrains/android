/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.gradle.stubs.android;

import com.android.builder.model.ProductFlavor;
import com.android.builder.model.Variant;
import com.android.tools.idea.gradle.stubs.FileStructure;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class VariantStub implements Variant {
  @NotNull private final List<String> myProductFlavors = Lists.newArrayList();

  @NotNull private final String myName;
  @NotNull private final String myBuildType;
  @NotNull private final ArtifactInfoStub myMainArtifactInfo;
  @NotNull private final ArtifactInfoStub myTestArtifactInfo;

  /**
   * Creates a new {@link VariantStub}.
   *
   * @param name          the name of the variant.
   * @param buildType     the name of the build type.
   * @param fileStructure the file structure of the Gradle project this variant belongs to.
   */
  VariantStub(@NotNull String name, @NotNull String buildType, @NotNull FileStructure fileStructure) {
    myName = name;
    myBuildType = buildType;
    myMainArtifactInfo = new ArtifactInfoStub("assemble", buildType, fileStructure);
    myTestArtifactInfo = new ArtifactInfoStub("assembleTest", buildType, fileStructure);
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return myName;
  }

  @Override
  @NotNull
  public ArtifactInfoStub getMainArtifactInfo() {
    return myMainArtifactInfo;
  }

  @Override
  @Nullable
  public ArtifactInfoStub getTestArtifactInfo() {
    return myTestArtifactInfo;
  }

  @Override
  @NotNull
  public String getBuildType() {
    return myBuildType;
  }

  @Override
  @NotNull
  public List<String> getProductFlavors() {
    return myProductFlavors;
  }

  @Override
  @NotNull
  public ProductFlavor getMergedFlavor() {
    throw new UnsupportedOperationException();
  }

  @Override
  @NotNull
  public List<String> getResourceConfigurations() {
    throw new UnsupportedOperationException();
  }

  public void addProductFlavors(@NotNull String... flavorNames) {
    myProductFlavors.addAll(Arrays.asList(flavorNames));
  }
}
