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
package com.android.tools.idea.wizard;

import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.ide.wizard.StepAdapter;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/**
 * Wizard that switches between several wizards depending on user input.
 */
public class BranchingWizard extends AbstractWizard<StepAdapter> {
  public BranchingWizard(String title, @Nullable Project project) {
    super(title, project);
  }

  @Override
  protected int getNextStep(int step) {
    return super.getNextStep(step);
  }

  @Override
  protected int getNumberOfSteps() {
    return super.getNumberOfSteps();
  }

  @Override
  protected int getPreviousStep(int step) {
    return super.getPreviousStep(step);
  }

  @Override
  public int getCurrentStep() {
    return super.getCurrentStep();
  }

  @Override
  public int getStepCount() {
    return super.getStepCount();
  }

  @Override
  protected StepAdapter getNextStepObject() {
    return super.getNextStepObject();
  }

  @Override
  public Component getCurrentStepComponent() {
    return super.getCurrentStepComponent();
  }

  @Nullable
  @Override
  protected String getHelpID() {
    return null;
  }
}
