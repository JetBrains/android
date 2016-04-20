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
package com.android.tools.idea.gradle.structure.model.android;

import com.android.builder.model.BaseArtifact;
import com.android.tools.idea.gradle.AndroidGradleModel;
import com.android.tools.idea.gradle.dsl.model.dependencies.DependencyModel;
import com.android.tools.idea.gradle.structure.model.PsChildModel;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.builder.model.AndroidProject.*;
import static com.android.tools.idea.gradle.dsl.model.dependencies.CommonConfigurationNames.*;
import static com.intellij.icons.AllIcons.Modules.TestRoot;
import static com.intellij.icons.AllIcons.Nodes.Artifact;
import static com.intellij.openapi.util.text.StringUtil.capitalize;
import static icons.AndroidIcons.AndroidTestRoot;

public class PsAndroidArtifact extends PsChildModel implements PsAndroidModel {
  @NonNls private static final String COMPILE_SUFFIX = "Compile";

  @NotNull private final String myName;
  @NotNull private final String myResolvedName;
  @NotNull private final Icon myIcon;

  @Nullable private final BaseArtifact myResolvedModel;

  public PsAndroidArtifact(@NotNull PsVariant parent, @NotNull String resolvedName, @Nullable BaseArtifact resolvedModel) {
    super(parent);
    myResolvedName = resolvedName;

    Icon icon = Artifact;
    String name = "";
    switch (resolvedName) {
      case ARTIFACT_MAIN:
        icon = AllIcons.Modules.SourceRoot;
        break;
      case ARTIFACT_ANDROID_TEST:
        name = "AndroidTest";
        icon = AndroidTestRoot;
        break;
      case ARTIFACT_UNIT_TEST:
        name = "Test";
        icon = TestRoot;
    }

    myName = name;
    myIcon = icon;
    myResolvedModel = resolvedModel;
  }

  @Override
  @NotNull
  public String getName() {
    return myName;
  }

  @NotNull
  public String getResolvedName() {
    return myResolvedName;
  }

  @Override
  @Nullable
  public BaseArtifact getResolvedModel() {
    return myResolvedModel;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return myIcon;
  }

  @Override
  @NotNull
  public AndroidGradleModel getGradleModel() {
    return getParent().getGradleModel();
  }

  @Override
  @NotNull
  public PsVariant getParent() {
    return (PsVariant)super.getParent();
  }

  @Override
  public boolean isDeclared() {
    return false;
  }

  public boolean contains(@NotNull DependencyModel parsedDependency) {
    String configurationName = parsedDependency.configurationName();
    return containsConfigurationName(configurationName);
  }

  public boolean containsConfigurationName(@NotNull String configurationName) {
    return getPossibleConfigurationNames().contains(configurationName);
  }

  @VisibleForTesting
  @NotNull
  List<String> getPossibleConfigurationNames() {
    List<String> configurationNames = Lists.newArrayList();
    switch (myResolvedName) {
      case ARTIFACT_MAIN:
        configurationNames.add(COMPILE);
        break;
      case ARTIFACT_UNIT_TEST:
        configurationNames.add(TEST_COMPILE);
        break;
      case ARTIFACT_ANDROID_TEST:
        configurationNames.add(ANDROID_TEST_COMPILE);
    }

    PsVariant variant = getParent();

    String buildTypeName = variant.getBuildType().getName();
    switch (myResolvedName) {
      case ARTIFACT_MAIN:
        configurationNames.add(buildTypeName + COMPILE_SUFFIX);
        break;
      case ARTIFACT_UNIT_TEST:
        configurationNames.add("test" + capitalize(buildTypeName) + COMPILE_SUFFIX);
    }

    variant.forEachProductFlavor(productFlavor -> {
      String productFlavorName = productFlavor.getName();
      switch (myResolvedName) {
        case ARTIFACT_MAIN:
          configurationNames.add(productFlavorName + COMPILE_SUFFIX);
          break;
        case ARTIFACT_UNIT_TEST:
          configurationNames.add("test" + capitalize(productFlavorName) + COMPILE_SUFFIX);
          break;
        case ARTIFACT_ANDROID_TEST:
          configurationNames.add("androidTest" + capitalize(productFlavorName) + COMPILE_SUFFIX);
      }
    });
    return configurationNames;
  }
}
