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
package com.android.tools.idea.gradle.structure.configurables.model;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Library;
import com.android.tools.idea.gradle.dsl.model.dependencies.ModuleDependencyModel;
import com.google.common.collect.Lists;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.Module;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class ModuleDependencyMergedModel extends DependencyMergedModel {
  @NotNull private final List<GradleModuleDependency> myGradleModuleDependencies;
  @NotNull private final String myGradlePath;

  @Nullable private final ModuleDependencyModel myParsedModel;

  @Nullable
  static ModuleDependencyMergedModel create(@NotNull ModuleMergedModel parent,
                                            @NotNull List<GradleModuleDependency> moduleDependencies,
                                            @Nullable ModuleDependencyModel parsedModel) {
    String gradlePath;
    if (parsedModel != null) {
      gradlePath = parsedModel.path();
    }
    else {
      if (moduleDependencies.isEmpty()) {
        return null;
      }
      GradleModuleDependency dependency = moduleDependencies.get(0);
      gradlePath = dependency.gradlePath;
    }
    return new ModuleDependencyMergedModel(parent, moduleDependencies, gradlePath, parsedModel);
  }

  private ModuleDependencyMergedModel(@NotNull ModuleMergedModel parent,
                                      @NotNull List<GradleModuleDependency> moduleDependencies,
                                      @NotNull String gradlePath,
                                      @Nullable ModuleDependencyModel parsedModel) {
    super(parent, parsedModel != null ? parsedModel.configurationName() : null);
    myGradleModuleDependencies = Lists.newArrayList(moduleDependencies);
    myGradlePath = gradlePath;
    myParsedModel = parsedModel;
  }

  @NotNull
  public String getGradlePath() {
    return myGradlePath;
  }

  @Override
  @NotNull
  public Icon getIcon() {
    return AllIcons.Nodes.Module;
  }

  @Override
  public boolean isInBuildFile() {
    return myParsedModel != null;
  }

  @Override
  public boolean matches(@NotNull Library library) {
    for (GradleModuleDependency dependency : myGradleModuleDependencies) {
      if (dependency.dependency == library) {
        return true;
      }
      if (library instanceof AndroidLibrary) {
        AndroidLibrary androidLibrary = (AndroidLibrary)library;
        String projectPath = androidLibrary.getProject();
        if (myGradlePath.equals(projectPath)) {
          return true;
        }
      }
    }
    return false;
  }

  @Override
  public boolean isInAndroidProject() {
    return false;
  }

  @Override
  public String toString() {
    Module module = null;
    for (GradleModuleDependency dependency : myGradleModuleDependencies) {
      module = dependency.module;
      if (module != null) {
        break;
      }
    }
    if (module != null) {
      return module.getName();
    }

    if (myParsedModel != null) {
      return myParsedModel.name();
    }

    return myGradlePath;
  }
}
