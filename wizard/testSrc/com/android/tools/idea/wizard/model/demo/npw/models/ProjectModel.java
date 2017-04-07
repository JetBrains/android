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
package com.android.tools.idea.wizard.model.demo.npw.models;

import com.android.annotations.Nullable;
import com.android.tools.idea.wizard.model.WizardModel;
import org.jetbrains.annotations.NotNull;

/**
 * A model which represents the configuration needed for generating a project.
 */
public final class ProjectModel extends WizardModel {

  @Nullable private String myApplicationName;
  @Nullable private String myPackageName;
  @Nullable private String myProjectLocation;

  public void setApplicationName(@NotNull String applicationName) {
    myApplicationName = applicationName;
  }
  public void setPackageName(@NotNull String packageName) {
    myPackageName = packageName;
  }
  public void setProjectLocation(@NotNull String projectLocation) {
    myProjectLocation = projectLocation;
  }

  @Override
  public void handleFinished() {
    System.out.println("Creating project");
    System.out.println("Application Name: " + myApplicationName);
    System.out.println("Package Name: " + myPackageName);
    System.out.println("Project Location: " + myProjectLocation);
  }
}
