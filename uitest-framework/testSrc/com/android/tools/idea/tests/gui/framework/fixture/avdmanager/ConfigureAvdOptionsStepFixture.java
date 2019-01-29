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

import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardStepFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.diagnostic.Logger;
import org.fest.swing.edt.GuiTask;
import org.fest.swing.exception.ComponentLookupException;
import org.fest.swing.fixture.JComboBoxFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import java.awt.Component;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.util.concurrent.atomic.AtomicReference;

import static com.google.common.truth.Truth.assertThat;

public class ConfigureAvdOptionsStepFixture<W extends AbstractWizardFixture>
  extends AbstractWizardStepFixture<ConfigureAvdOptionsStepFixture, W> {

  protected ConfigureAvdOptionsStepFixture(@NotNull W wizard, @NotNull JRootPane target) {
    super(ConfigureAvdOptionsStepFixture.class, wizard, target);
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> showAdvancedSettings() {
    try {
      JButton showAdvancedSettingsButton = robot().finder().find(target(), Matchers.byText(JButton.class, "Show Advanced Settings"));
      robot().click(showAdvancedSettingsButton);
    } catch (ComponentLookupException e) {
      throw new RuntimeException("Show Advanced Settings called when advanced settings are already shown.", e);
    }
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> requireAvdName(@NotNull String name) {
    String text = findTextFieldWithLabel("AVD Name").getText();
    assertThat(text).named("AVD name").isEqualTo(name);
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> setAvdName(@NotNull String name) {
    JTextComponent textFieldWithLabel = findTextFieldWithLabel("AVD Name");

    // TODO remove this focus listener. This focus listener is used to help diagnose http://b/77152845
    // See http://b/80101068 for work required to remove this focus listener
    // Use an AtomicReference, since the listener is invoked on the EDT while the test runs
    // in the test thread.
    AtomicReference<Exception> focusStolenException = new AtomicReference<>();
    FocusListener focusListener = new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        // This is expected. Focus most definitely _should_ be gained!
      }

      @Override
      public void focusLost(FocusEvent e) {
        // Focus should not be lost while this listener is attached to the text field!
        Logger logger = Logger.getInstance(ConfigureAvdOptionsStepFixture.class);
        Exception focusLostException = new Exception("Focus was stolen from AVD name field");
        Component opposite = e.getOppositeComponent();
        logger.error(
          "Focus was lost from the AVD name field to "
            + opposite.getName()
            + " which is a " + opposite.getClass().toString(),
          focusLostException
        );
        focusStolenException.set(focusLostException);
      }
    };

    try {
      GuiTask.execute(() -> textFieldWithLabel.addFocusListener(focusListener));
      replaceText(textFieldWithLabel, name);
    } finally {
      GuiTask.execute(() -> textFieldWithLabel.removeFocusListener(focusListener));
    }
    Exception focusLostException = focusStolenException.get();
    if (focusLostException != null) {
      throw new RuntimeException(focusLostException);
    }
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> setFrontCamera(@NotNull String selection) {
    JComboBoxFixture frontCameraFixture = findComboBoxWithLabel("Front:");
    frontCameraFixture.selectItem(selection);
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> selectGraphicsHardware() {
    findComboBoxWithLabel("Graphics:").selectItem("Hardware - GLES .*");
    return this;
  }

  @NotNull
  public ConfigureAvdOptionsStepFixture<W> selectGraphicsSoftware() {
    findComboBoxWithLabel("Graphics:").selectItem("Software - GLES .*");
    return this;
  }
}
