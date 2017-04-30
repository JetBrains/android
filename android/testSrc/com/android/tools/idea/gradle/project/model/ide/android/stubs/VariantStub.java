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
package com.android.tools.idea.gradle.project.model.ide.android.stubs;

import com.android.builder.model.*;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class VariantStub extends BaseStub implements Variant {
  @NotNull private final String myName;
  @NotNull private final String myDisplayName;
  @NotNull private final AndroidArtifact myMainArtifact;
  @NotNull private final Collection<AndroidArtifact> myExtraAndroidArtifacts;
  @NotNull private final Collection<JavaArtifact> myExtraJavaArtifacts;
  @NotNull private final String myBuildType;
  @NotNull private final List<String> myProductFlavors;
  @NotNull private final ProductFlavor myMergedFlavor;
  @NotNull private final Collection<TestedTargetVariant> myTestedTargetVariants;

  public VariantStub() {
    this("name", "displayName", new AndroidArtifactStub(), Lists.newArrayList(new AndroidArtifactStub()),
         Lists.newArrayList(new JavaArtifactStub()), "buildType", Lists.newArrayList("flavor"), new ProductFlavorStub(),
         Lists.newArrayList(new TestedTargetVariantStub()));
  }

  public VariantStub(@NotNull String name,
                     @NotNull String displayName,
                     @NotNull AndroidArtifact mainArtifact,
                     @NotNull Collection<AndroidArtifact> extraAndroidArtifacts,
                     @NotNull Collection<JavaArtifact> extraJavaArtifacts,
                     @NotNull String buildType,
                     @NotNull List<String> productFlavors,
                     @NotNull ProductFlavor mergedFlavor,
                     @NotNull Collection<TestedTargetVariant> testedTargetVariants) {
    myName = name;
    myDisplayName = displayName;
    myMainArtifact = mainArtifact;
    myExtraAndroidArtifacts = extraAndroidArtifacts;
    myExtraJavaArtifacts = extraJavaArtifacts;
    myBuildType = buildType;
    myProductFlavors = productFlavors;
    myMergedFlavor = mergedFlavor;
    myTestedTargetVariants = testedTargetVariants;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return myDisplayName;
  }

  @Override
  @NotNull
  public AndroidArtifact getMainArtifact() {
    return myMainArtifact;
  }

  @Override
  @NotNull
  public Collection<AndroidArtifact> getExtraAndroidArtifacts() {
    return myExtraAndroidArtifacts;
  }

  @Override
  @NotNull
  public Collection<JavaArtifact> getExtraJavaArtifacts() {
    return myExtraJavaArtifacts;
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
    return myMergedFlavor;
  }

  @Override
  @NotNull
  public Collection<TestedTargetVariant> getTestedTargetVariants() {
    return myTestedTargetVariants;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Variant)) {
      return false;
    }
    Variant variant = (Variant)o;
    return Objects.equals(getName(), variant.getName()) &&
           Objects.equals(getDisplayName(), variant.getDisplayName()) &&
           Objects.equals(getMainArtifact(), variant.getMainArtifact()) &&
           Objects.equals(getExtraAndroidArtifacts(), variant.getExtraAndroidArtifacts()) &&
           Objects.equals(getExtraJavaArtifacts(), variant.getExtraJavaArtifacts()) &&
           Objects.equals(getBuildType(), variant.getBuildType()) &&
           Objects.equals(getProductFlavors(), variant.getProductFlavors()) &&
           Objects.equals(getMergedFlavor(), variant.getMergedFlavor()) &&
           Objects.equals(getTestedTargetVariants(), variant.getTestedTargetVariants());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), getDisplayName(), getMainArtifact(), getExtraAndroidArtifacts(), getExtraJavaArtifacts(), getBuildType(),
                        getProductFlavors(), getMergedFlavor(), getTestedTargetVariants());
  }

  @Override
  public String toString() {
    return "VariantStub{" +
           "myName='" + myName + '\'' +
           ", myDisplayName='" + myDisplayName + '\'' +
           ", myMainArtifact=" + myMainArtifact +
           ", myExtraAndroidArtifacts=" + myExtraAndroidArtifacts +
           ", myExtraJavaArtifacts=" + myExtraJavaArtifacts +
           ", myBuildType='" + myBuildType + '\'' +
           ", myProductFlavors=" + myProductFlavors +
           ", myMergedFlavor=" + myMergedFlavor +
           ", myTestedTargetVariants=" + myTestedTargetVariants +
           "}";
  }
}
