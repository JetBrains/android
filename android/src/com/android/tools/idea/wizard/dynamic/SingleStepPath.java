/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.wizard.dynamic;

import org.jetbrains.annotations.NotNull;

/**
 * Helper class for wrapping a single {@link DynamicWizardStep} in a {@link DynamicWizardPath}
 */
public class SingleStepPath extends DynamicWizardPath {
  private final DynamicWizardStep myStep;

  public SingleStepPath(DynamicWizardStep step) {
    myStep = step;
  }

  @Override
  protected void init() {
    addStep(myStep);
  }

  @Override
  public boolean canGoNext() {
    return myStep.canGoNext();
  }

  @Override
  public boolean canGoPrevious() {
    return myStep.canGoPrevious();
  }

  @Override
  public boolean readyToLeavePath() {
    return super.readyToLeavePath() && canGoNext();
  }

  @Override
  public boolean isPathVisible() {
    return myStep.isStepVisible();
  }

  @NotNull
  @Override
  public String getPathName() {
    return myStep.getStepName();
  }

  @Override
  public boolean performFinishingActions() {
    return true;
  }

  @Override
  public boolean validate() {
    return myStep.validate();
  }

  @Override
  public boolean containsStep(@NotNull String stepName, boolean visibleOnly) {
    if (visibleOnly && !isPathVisible()) {
      return false;
    }
    return stepName.equals(myStep.getStepName());
  }
}
