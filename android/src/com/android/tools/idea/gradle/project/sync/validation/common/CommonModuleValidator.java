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
package com.android.tools.idea.gradle.project.sync.validation.common;

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class CommonModuleValidator {
  public abstract void validate(@NotNull Module module);

  public abstract void fixAndReportFoundIssues();

  public static class Factory {
    @NotNull
    public CommonModuleValidator create(@NotNull Project project) {
      return new CommonModuleValidatorImpl(project);
    }
  }

  @VisibleForTesting
  static class CommonModuleValidatorImpl extends CommonModuleValidator {
    @NotNull private final CommonProjectValidationStrategy[] myStrategies;

    CommonModuleValidatorImpl(@NotNull Project project) {
      this(new UniquePathModuleValidatorStrategy(project));
    }

    @VisibleForTesting
    CommonModuleValidatorImpl(@NotNull CommonProjectValidationStrategy... strategies) {
      myStrategies = strategies;
    }

    @Override
    public void validate(@NotNull Module module) {
      for (CommonProjectValidationStrategy strategy : myStrategies) {
        strategy.validate(module);
      }
    }

    @Override
    public void fixAndReportFoundIssues() {
      for (CommonProjectValidationStrategy strategy : myStrategies) {
        strategy.fixAndReportFoundIssues();
      }
    }

    @VisibleForTesting
    @NotNull
    CommonProjectValidationStrategy[] getStrategies() {
      return myStrategies;
    }
  }
}
