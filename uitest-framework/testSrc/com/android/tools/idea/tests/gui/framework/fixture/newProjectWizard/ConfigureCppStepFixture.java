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

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConfigureCppStepFixture<W extends AbstractWizardFixture> extends AbstractWizardStepFixture<ConfigureCppStepFixture, W> {
  protected ConfigureCppStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureCppStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureCppStepFixture<W> setExceptionsSupport(boolean select) {
    selectCheckBoxWithText("Exceptions Support (-fexceptions)", select);
    return this;
  }

  @NotNull
  public ConfigureCppStepFixture<W> setRuntimeInformationSupport(boolean select) {
    selectCheckBoxWithText("Runtime Type Information Support (-frtti)", select);
    return this;
  }
}
