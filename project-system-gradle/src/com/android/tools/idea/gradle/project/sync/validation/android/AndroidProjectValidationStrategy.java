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
package com.android.tools.idea.gradle.project.sync.validation.android;

import com.android.tools.idea.gradle.project.model.GradleAndroidModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class AndroidProjectValidationStrategy {
  @NotNull private final Project myProject;

  AndroidProjectValidationStrategy(@NotNull Project project) {
    myProject = project;
  }

  abstract void validate(@NotNull Module module, @NotNull GradleAndroidModel androidModel);

  abstract void fixAndReportFoundIssues();

  @NotNull
  Project getProject() {
    return myProject;
  }
}
