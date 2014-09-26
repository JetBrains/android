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

import com.android.tools.idea.avdmanager.ConfigureAvdOptionsStep;
import com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard.AbstractWizardStepFixture;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JCheckBoxFixture;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JScrollBarFixture;
import org.fest.swing.fixture.JScrollPaneFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ConfigureAvdOptionsStepFixture extends AbstractWizardStepFixture {
  private JScrollPaneFixture myScrollPaneFixture;

  protected ConfigureAvdOptionsStepFixture(@NotNull Robot robot, @NotNull JRootPane target) {
    super(robot, target);
  }

  public ConfigureAvdOptionsStepFixture showAdvancedSettings() {
    try {
      JButton showAdvancedSettingsButton = robot.finder().find(new GenericTypeMatcher<JButton>(JButton.class) {
        @Override
        protected boolean isMatching(JButton component) {
          return "Show Advanced Settings".equals(component.getText());
        }
      });
      robot.click(showAdvancedSettingsButton);
    } catch (ComponentLookupException e) {
      throw new RuntimeException("Show Advanced Settings called when advanced settings are already shown.", e);
    }
    return this;
  }

  public ConfigureAvdOptionsStepFixture hideAdvancedSettings() {
    try {
      JButton showAdvancedSettingsButton = robot.finder().find(new GenericTypeMatcher<JButton>(JButton.class) {
        @Override
        protected boolean isMatching(JButton component) {
          return "Hide Advanced Settings".equals(component.getText());
        }
      });
      robot.click(showAdvancedSettingsButton);
    } catch (ComponentLookupException e) {
      throw new RuntimeException("Hide Advanced Settings called when advanced settings are already hidden.", e);
    }
    return this;
  }

  public ConfigureAvdOptionsStepFixture setFrontCamera(@NotNull String selection) {
    JComboBoxFixture frontCameraFixture = getComboBoxFixtureByLabel("Front:");
    frontCameraFixture.selectItem(selection);
    return this;
  }

  public ConfigureAvdOptionsStepFixture setBackCamera(@NotNull String selection) {
    JComboBoxFixture backCameraFixture = getComboBoxFixtureByLabel("Back:");
    backCameraFixture.selectItem(selection);
    return this;
  }

  public ConfigureAvdOptionsStepFixture setNetworkSpeed(@NotNull String selection) {
    JComboBoxFixture networkSpeedComboFixture = getComboBoxFixtureByLabel("Speed:");
    networkSpeedComboFixture.selectItem(selection);
    return this;
  }

  public ConfigureAvdOptionsStepFixture setNetworkLatency(@NotNull String selection) {
    JComboBoxFixture networkLatencyComboFixture = getComboBoxFixtureByLabel("Latency:");
    networkLatencyComboFixture.selectItem(selection);
    return this;
  }

  public ConfigureAvdOptionsStepFixture setScaleFactor(@NotNull String selection) {
    JComboBoxFixture scaleFactorCombo = getComboBoxFixtureByLabel("Scale:");
    scaleFactorCombo.selectItem(selection);
    return this;
  }

  public ConfigureAvdOptionsStepFixture setUseHostGpu(boolean useHostGpu) {
    JCheckBoxFixture hostGpuCheckBox = getCheckBoxFixtureByLabel("Use Host GPU");
    if (useHostGpu) {
      hostGpuCheckBox.check();
    } else {
      hostGpuCheckBox.uncheck();
    }
    return this;
  }

  private JComboBoxFixture getComboBoxFixtureByLabel(@NotNull String label) {
    JComboBox combo = robot.finder().findByLabel(target, label, JComboBox.class, true);
    return new JComboBoxFixture(robot, combo);
  }

  private JCheckBoxFixture getCheckBoxFixtureByLabel(@NotNull final String label) {
    JCheckBox checkBox = robot.finder().find(target, new GenericTypeMatcher<JCheckBox>(JCheckBox.class) {
      @Override
      protected boolean isMatching(JCheckBox component) {
        return label.equals(component.getText());
      }
    });
    return new JCheckBoxFixture(robot, checkBox);
  }
}
