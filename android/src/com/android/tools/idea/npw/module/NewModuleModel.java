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
 * limitations under the License.
 */
package com.android.tools.idea.npw.module;

import com.android.tools.idea.ui.properties.core.OptionalProperty;
import com.android.tools.idea.ui.properties.core.OptionalValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class NewModuleModel extends WizardModel {
  @NotNull private final OptionalProperty<Project> myProject;

  public NewModuleModel(@Nullable Project project) {
    myProject = OptionalValueProperty.fromNullable(project);
  }

  @NotNull
  public OptionalProperty<Project> getProject() {
    return myProject;
  }

  @Override
  protected void handleFinished() {
    // By the time we run handleFinished(), we must have a Project
    if (!myProject.get().isPresent()) {
      getLog().error("NewModuleModel did not collect expected information and will not complete. Please report this error.");
      return;
    }

    // TODO: Port over logic from NewFormFactorModulePath#performFinishingOperation
  }

  @NotNull
  private static Logger getLog() {
    return Logger.getInstance(NewModuleModel.class);
  }
}
