/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.parser.files;

import com.android.tools.idea.gradle.dsl.model.BuildModelContext;
import com.android.tools.idea.gradle.dsl.parser.apply.ApplyDslElement;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Represents those files that are part of a Gradle build that are code which is executed to construct a Gradle Build Model.
 *
 * The common functionality captured in this class is the handling of apply(from...) which executes the applied file in the current
 * context.  This is principally used in build files, but is in principle available in settings files; note that apply from settings
 * files is currently untested.
 */
abstract public class GradleScriptFile extends GradleDslFile {
  @Nullable private ApplyDslElement myApplyDslElement;

  protected GradleScriptFile(@NotNull VirtualFile file,
                             @NotNull Project project,
                             @NotNull String moduleName,
                             @NotNull BuildModelContext context) {
    super(file, project, moduleName, context);
  }

  @NotNull
  public List<GradleScriptFile> getApplyDslElement() {
    return myApplyDslElement == null ? ImmutableList.of() : myApplyDslElement.getAppliedDslFiles();
  }

  @Override
  protected void apply() {
    // First make sure we update all our applied files.
    if (myApplyDslElement != null) {
      for (GradleScriptFile file : myApplyDslElement.getAppliedDslFiles()) {
        file.apply();
      }
    }

    // And update us.
    super.apply();
  }

  public void registerApplyElement(@NotNull ApplyDslElement applyElement) {
    myApplyDslElement = applyElement;
  }
}
