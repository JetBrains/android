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
package com.android.tools.idea.gradle.structure.prototype.model;

import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.Library;
import com.android.builder.model.Variant;
import com.google.common.collect.Lists;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.util.GradleUtil.findModuleByGradlePath;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

/**
 * A module dependency obtained from the Gradle model.
 */
public class GradleModuleDependency {
  @NotNull final String gradlePath;
  @NotNull final List<Variant> containers = Lists.newArrayList();

  @Nullable final Library dependency;
  @Nullable final Module module;

  @Nullable
  static GradleModuleDependency create(@NotNull AndroidLibrary dependency, @NotNull Project project) {
    String gradlePath = dependency.getProject();
    if (isNotEmpty(gradlePath)) {
      return new GradleModuleDependency(gradlePath, project, dependency);
    }
    return null;
  }

  GradleModuleDependency(@NotNull String gradlePath, @NotNull Project project, @Nullable AndroidLibrary dependency) {
    this.gradlePath = gradlePath;
    module = findModuleByGradlePath(project, gradlePath);
    this.dependency = dependency;
  }

  void addContainer(@NotNull Variant variant) {
    containers.add(variant);
  }

  @Override
  public String toString() {
    return gradlePath;
  }
}
