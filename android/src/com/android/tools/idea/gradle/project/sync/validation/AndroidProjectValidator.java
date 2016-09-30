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
package com.android.tools.idea.gradle.project.sync.validation;

import com.android.tools.idea.gradle.AndroidGradleModel;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class AndroidProjectValidator {
  public abstract void validate(@NotNull Module module, @NotNull AndroidGradleModel androidModel);

  public abstract void fixAndReportFoundIssues();

  public static class Factory {
    @NotNull
    public AndroidProjectValidator create(@NotNull Project project) {
      return new AndroidProjectValidatorImpl(project);
    }
  }

  @VisibleForTesting
  static class AndroidProjectValidatorImpl extends AndroidProjectValidator {
    @NotNull private final AndroidProjectValidationStrategy[] myStrategies;

    AndroidProjectValidatorImpl(@NotNull Project project) {
      this(new EncodingValidationStrategy(project),
           new BuildTools23Rc1ValidationStrategy(project),
           new LayoutRenderingIssueValidationStrategy(project),
           new ExtraGeneratedFolderValidationStrategy(project)
      );
    }

    @VisibleForTesting
    AndroidProjectValidatorImpl(@NotNull AndroidProjectValidationStrategy... strategies) {
      myStrategies = strategies;
    }

    @Override
    public void validate(@NotNull Module module, @NotNull AndroidGradleModel androidModel) {
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
