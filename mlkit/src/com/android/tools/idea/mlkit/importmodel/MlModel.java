/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.mlkit.importmodel;

import com.android.tools.idea.observable.core.StringValueProperty;
import com.android.tools.idea.wizard.model.WizardModel;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * {@link WizardModel} that contains model location to import.
 */
public class MlModel extends WizardModel {

  @NotNull
  private Project myProject;

  public StringValueProperty sourceLocation;

  public MlModel(@NotNull Project project) {
    myProject = project;
    sourceLocation = new StringValueProperty();
  }

  @Override
  protected void handleFinished() {
    //TODO(jackqdyulei): create ml folder and put model into this folder.
  }

  public Project getProject() {
    return myProject;
  }
}
