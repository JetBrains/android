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
package com.android.tools.idea.model;

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;

/**
 * Creates a deep copy of {@link Variant}.
 *
 * @see IdeAndroidProject
 */
public class IdeVariant implements Variant, Serializable {
  @NotNull private final String myName;
  @NotNull private final String myDisplayName;
  @NotNull private final AndroidArtifact myMainArtifact;
  @NotNull private final Collection<AndroidArtifact> myExtraAndroidArtifacts;
  @NotNull private final Collection<JavaArtifact> myExtraJavaArtifacts;
  @NotNull private final String myBuildType;
  @NotNull private final List<String> myProductFlavors;
  @NotNull private final ProductFlavor myMergedFlavor;
  @NotNull private final Collection<TestedTargetVariant> myTestedTargetVariants;

  public IdeVariant(@NotNull Variant variant, Map<Library, Library> seen, GradleVersion gradleVersion) {
    myName = variant.getName();
    myDisplayName = variant.getDisplayName();
    myMainArtifact = new IdeAndroidArtifact(variant.getMainArtifact(), seen, gradleVersion);

    myExtraAndroidArtifacts = new ArrayList<>();
    for (AndroidArtifact artifact : variant.getExtraAndroidArtifacts()) {
      myExtraAndroidArtifacts.add(new IdeAndroidArtifact(artifact, seen, gradleVersion));
    }

    myExtraJavaArtifacts = new ArrayList<>();
    for (JavaArtifact artifact : variant.getExtraJavaArtifacts()) {
      myExtraJavaArtifacts.add(new IdeJavaArtifact(artifact, seen, gradleVersion));
    }

    myBuildType = variant.getBuildType();
    myProductFlavors = new ArrayList<>(variant.getProductFlavors());
    myMergedFlavor = new IdeProductFlavor(variant.getMergedFlavor());

    myTestedTargetVariants = new HashSet<>();
    for (TestedTargetVariant tested : variant.getTestedTargetVariants()) {
      myTestedTargetVariants.add(new IdeTestedTargetVariants(tested));
    }
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
}
