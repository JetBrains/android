/*
 * Copyright (C) 2013 The Android Open Source Project
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

import com.intellij.ide.util.projectWizard.ModuleWizardStep;
import com.intellij.ide.wizard.AbstractWizard;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;

/**
 * TemplateWizard is a base class for Freemarker template-based wizards.
 */
public class TemplateWizard extends AbstractWizard<ModuleWizardStep> {
  public TemplateWizard(String title, Project project) {
    super(title, project);
  }

  /**
   * Subclasses and step classes can call this to update next/previous button state; this is
   * generally called after parameter validation has finished.
   */
  void update() {
    updateButtons();
  }

  @Override
  protected boolean canGoNext() {
    return !mySteps.isEmpty() && ((TemplateWizardStep)mySteps.get(getCurrentStep())).isValid();
  }

  @Nullable
  @Override
  protected String getHelpID() {
    return null;
  }

  @Override
  protected final int getNextStep(final int step) {
    for (int i = step + 1; i < mySteps.size(); i++) {
      if (mySteps.get(i).isStepVisible()) {
        return i;
      }
    }
    return step;
  }

  @Override
  protected final int getPreviousStep(final int step) {
    for (int i = step - 1; i >= 0; i--) {
      if (mySteps.get(i).isStepVisible()) {
        return i;
      }
    }
    return step;
  }
}
