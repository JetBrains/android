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

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A simple wizard containing a single step, presented as a dialog box with OK/Cancel buttons.
 */
public abstract class SingleStepWizard extends DynamicWizard {
  private DynamicWizardStep myStep;

  public SingleStepWizard(DynamicWizardStep step) {
    this(null, null, step, new SingleStepDialogWrapperHost(null));
    myStep = step;
  }

  public SingleStepWizard(@Nullable Project project, @Nullable Module module,
                          @NotNull DynamicWizardStep step, @NotNull DynamicWizardHost host) {
    super(project, module, "WizardStep", host);
    myStep = step;
  }

  @Override
  public void init() {
    addPath(new SingleStepPath(myStep));
    super.init();
  }

  /**
   * By default no nothing; getState() can be called to retrieve state after the wizard is complete.
   */
  @Override
  public void performFinishingActions() {
  }
}
