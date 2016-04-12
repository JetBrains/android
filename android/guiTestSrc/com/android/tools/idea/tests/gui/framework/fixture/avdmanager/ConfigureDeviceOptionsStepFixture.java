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
package com.android.tools.idea.tests.gui.framework.fixture.avdmanager;

import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import org.fest.swing.core.Robot;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConfigureDeviceOptionsStepFixture extends AbstractWizardStepFixture<ConfigureDeviceOptionsStepFixture> {
  protected ConfigureDeviceOptionsStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(ConfigureDeviceOptionsStepFixture.class, robot, target);
  }

  @NotNull
  public ConfigureDeviceOptionsStepFixture setDeviceName(@NotNull String deviceName) {
    replaceText(findTextFieldWithLabel("Device Name"), deviceName);
    return this;
  }

  @NotNull
  public ConfigureDeviceOptionsStepFixture setScreenSize(String screenSize) {
    replaceText(findTextFieldWithLabel("Screen size:"), screenSize);
    return this;
  }

  @NotNull
  public ConfigureDeviceOptionsStepFixture setScreenResolutionX(String width) {
    replaceText(findTextFieldWithLabel("Resolution:"), width);
    return this;
  }

  @NotNull
  public ConfigureDeviceOptionsStepFixture setScreenResolutionY(String height) {
    replaceText(findTextFieldWithLabel("x"), height);
    return this;
  }
}
