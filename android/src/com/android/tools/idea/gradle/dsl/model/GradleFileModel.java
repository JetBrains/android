/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model;

import com.android.tools.idea.gradle.dsl.parser.GradlePsiFile;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class GradleFileModel {
  @NotNull protected GradlePsiFile myGradlePsiFile;

  public GradleFileModel(@NotNull GradlePsiFile gradlePsiFile) {
    myGradlePsiFile = gradlePsiFile;
  }

  @NotNull
  public Project getProject() {
    return myGradlePsiFile.getProject();
  }

  public void reparse() {
    myGradlePsiFile.reparse();
  }

  public boolean isModified() {
    return myGradlePsiFile.isModified();
  }

  public void resetState() {
    myGradlePsiFile.resetState();
  }

  public void applyChanges() {
    myGradlePsiFile.applyChanges();
  }
}
