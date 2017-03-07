/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConfigureCppStepFixture extends AbstractWizardStepFixture<ConfigureCppStepFixture> {
  protected ConfigureCppStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ConfigureCppStepFixture.class, robot, target);
  }

  @NotNull
  public ConfigureCppStepFixture setExceptionsSupport(boolean select) {
    selectCheckBoxWithText("Exceptions Support (-fexceptions)", select);
    return this;
  }

  @NotNull
  public ConfigureCppStepFixture setRuntimeInformationSupport(boolean select) {
    selectCheckBoxWithText("Runtime Type Information Support (-frtti)", select);
    return this;
  }
}
