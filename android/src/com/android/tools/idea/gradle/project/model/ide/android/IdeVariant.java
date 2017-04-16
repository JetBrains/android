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
package com.android.tools.idea.gradle.project.model.ide.android;

import com.android.builder.model.*;
import com.android.ide.common.repository.GradleVersion;
import org.gradle.tooling.model.UnsupportedMethodException;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * Creates a deep copy of {@link Variant}.
 *
 * @see IdeAndroidProject
 */
public class IdeVariant extends IdeModel implements Variant {
  @NotNull private final String myName;
  @NotNull private final String myDisplayName;
  @NotNull private final AndroidArtifact myMainArtifact;
  @NotNull private final Collection<AndroidArtifact> myExtraAndroidArtifacts;
  @NotNull private final Collection<JavaArtifact> myExtraJavaArtifacts;
  @NotNull private final String myBuildType;
  @NotNull private final List<String> myProductFlavors;
  @NotNull private final ProductFlavor myMergedFlavor;
  @NotNull private final Collection<TestedTargetVariant> myTestedTargetVariants;

  public IdeVariant(@NotNull Variant variant, @NotNull ModelCache modelCache, @NotNull GradleVersion modelVersion) {
    super(variant, modelCache);
    myName = variant.getName();
    myDisplayName = variant.getDisplayName();
    myMainArtifact = modelCache.computeIfAbsent(variant.getMainArtifact(),
                                                artifact -> new IdeAndroidArtifact(artifact, modelCache, modelVersion));
    myExtraAndroidArtifacts = copy(variant.getExtraAndroidArtifacts(), modelCache,
                                   artifact -> new IdeAndroidArtifact(artifact, modelCache, modelVersion));
    myExtraJavaArtifacts = copy(variant.getExtraJavaArtifacts(), modelCache,
                                (Function<JavaArtifact, JavaArtifact>)artifact -> new IdeJavaArtifact(artifact, modelCache, modelVersion));
    myBuildType = variant.getBuildType();
    myProductFlavors = new ArrayList<>(variant.getProductFlavors());
    myMergedFlavor = modelCache.computeIfAbsent(variant.getMergedFlavor(), flavor -> new IdeProductFlavor(flavor, modelCache));
    myTestedTargetVariants = getTestedTargetVariants(variant, modelCache);
  }

  @NotNull
  private static Collection<TestedTargetVariant> getTestedTargetVariants(@NotNull Variant variant, @NotNull ModelCache modelCache) {
    try {
      return copy(variant.getTestedTargetVariants(), modelCache,
                  targetVariant -> new IdeTestedTargetVariants(targetVariant, modelCache));
    }
    catch (UnsupportedMethodException e) {
      return Collections.emptyList();
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
