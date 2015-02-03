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


import com.android.resources.Navigation;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConfigureDeviceOptionsStepFixture extends AbstractWizardStepFixture {
  protected ConfigureDeviceOptionsStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(robot, target);
  }


  public ConfigureDeviceOptionsStepFixture setDeviceName(@NotNull String deviceName) {
    replaceText(findTextFieldWithLabel("Device Name"), deviceName);
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setScreenSize(double screenSize) {
    replaceText(findTextFieldWithLabel("Screensize:"), Double.toString(screenSize));
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setScreenResolutionX(int width) {
    replaceText(findTextFieldWithLabel("Resolution:"), Integer.toString(width));
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setScreenResolutionY(int height) {
    replaceText(findTextFieldWithLabel("x"), Integer.toString(height));
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setHasHardwareButtons(boolean hasHardwareButtons) {
    JCheckBoxFixture checkBoxFixture = getCheckBoxFixtureByLabel(robot, target, "Has Hardware Buttons");
    if (hasHardwareButtons) {
      checkBoxFixture.check();
    } else {
      checkBoxFixture.uncheck();
    }
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setHasHardwareKeyboard(boolean hasHardwareKeyboard) {
    JCheckBoxFixture checkBoxFixture = getCheckBoxFixtureByLabel(robot, target, "Has Hardware Keyboard");
    if (hasHardwareKeyboard) {
      checkBoxFixture.check();
    } else {
      checkBoxFixture.uncheck();
    }
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setSupportsPortrait(boolean supportsPortrait) {
    JCheckBoxFixture checkBoxFixture = getCheckBoxFixtureByLabel(robot, target, "Portrait");
    if (supportsPortrait) {
      checkBoxFixture.check();
    } else {
      checkBoxFixture.uncheck();
    }
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setSupportsLandscape(boolean supportsLandscape) {
    JCheckBoxFixture checkBoxFixture = getCheckBoxFixtureByLabel(robot, target, "Landscape");
    if (supportsLandscape) {
      checkBoxFixture.check();
    } else {
      checkBoxFixture.uncheck();
    }
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setHasFrontCamera(boolean hasFrontCamera) {
    JCheckBoxFixture checkBoxFixture = getCheckBoxFixtureByLabel(robot, target, "Front-facing camera");
    if (hasFrontCamera) {
      checkBoxFixture.check();
    } else {
      checkBoxFixture.uncheck();
    }
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setHasBackCamera(boolean hasBackCamera) {
    JCheckBoxFixture checkBoxFixture = getCheckBoxFixtureByLabel(robot, target, "Back-facing camera");
    if (hasBackCamera) {
      checkBoxFixture.check();
    } else {
      checkBoxFixture.uncheck();
    }
    return this;
  }

  public ConfigureDeviceOptionsStepFixture setNavigation(@NotNull Navigation navigation) {
    JComboBoxFixture fixture = getComboBoxFixtureByLabel(robot, target, "Navigation Style");
    fixture.selectItem(navigation.getShortDisplayValue());
    return this;
  }
}
