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

import com.android.tools.idea.gradle.project.model.AndroidModuleModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class AndroidModuleValidator {
  public abstract void validate(@NotNull Module module, @NotNull AndroidModuleModel androidModel);

  public abstract void fixAndReportFoundIssues();

  public static class Factory {
    @NotNull
    public AndroidModuleValidator create(@NotNull Project project) {
      return new AndroidModuleValidatorImpl(project);
    }
  }

  @VisibleForTesting
  static class AndroidModuleValidatorImpl extends AndroidModuleValidator {
    @NotNull private final AndroidProjectValidationStrategy[] myStrategies;

    AndroidModuleValidatorImpl(@NotNull Project project) {
      this(new EncodingValidationStrategy(project),
           new BuildTools23Rc1ValidationStrategy(project)
      );
    }

    @VisibleForTesting
    AndroidModuleValidatorImpl(@NotNull AndroidProjectValidationStrategy... strategies) {
      myStrategies = strategies;
    }

    @Override
    public void validate(@NotNull Module module, @NotNull AndroidModuleModel androidModel) {
      for (AndroidProjectValidationStrategy strategy : myStrategies) {
        strategy.validate(module, androidModel);
      }
    }

    @Override
    public void fixAndReportFoundIssues() {
      for (AndroidProjectValidationStrategy strategy : myStrategies) {
        strategy.fixAndReportFoundIssues();
      }
    }

    @VisibleForTesting
    @NotNull
    AndroidProjectValidationStrategy[] getStrategies() {
      return myStrategies;
    }
  }
}
