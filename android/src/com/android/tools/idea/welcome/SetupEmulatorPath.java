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
package com.android.tools.idea.welcome;

import com.android.tools.idea.wizard.DynamicWizardPath;
import org.jetbrains.annotations.NotNull;

/**
 * Guides the user through setting up an emulator on first Android Studio run.
 */
public class SetupEmulatorPath extends DynamicWizardPath {
  @Override
  protected void init() {
    addStep(new MacEmulatorSettingsStep());
    addStep(new LinuxEmulatorSettingsStep());
  }

  @NotNull
  @Override
  public String getPathName() {
    return "Setup Android emulator";
  }

  @Override
  public boolean performFinishingActions() {
    return false;
  }
}
