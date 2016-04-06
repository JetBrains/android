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
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.ui.validation.Validator;
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import org.fest.assertions.Fail;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.fixture.JButtonFixture;
import org.fest.swing.fixture.JLabelFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.text.JTextComponent;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;

/**
 * Base class for fixtures which control wizards that extend {@link DynamicWizard}
 */
public abstract class AbstractWizardFixture<S> extends ComponentFixture<S, JDialog> implements ContainerFixture<JDialog> {

  public AbstractWizardFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull JDialog target) {
    super(selfType, robot, target);
  }

  @NotNull
  protected JRootPane findStepWithTitle(@NotNull final String title) {
    JRootPane rootPane = target().getRootPane();
    waitUntilShowing(robot(), rootPane, new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel label) {
        return title.equals(label.getText());
      }
    });
    return rootPane;
  }

  @NotNull
  public S clickNext() {
    findAndClickButton(this, "Next");
    return myself();
  }

  @NotNull
  public S clickFinish() {
    findAndClickButton(this, "Finish");
    return myself();
  }

  @NotNull
  public S clickCancel() {
    findAndClickCancelButton(this);
    return myself();
  }

  @NotNull
  public JTextComponentFixture findTextField(@NotNull final String labelText) {
    return new JTextComponentFixture(robot(), robot().finder().findByLabel(labelText, JTextComponent.class));
  }

  @NotNull
  public JButtonFixture findWizardButton(@NotNull final String text) {
    JButton button = robot().finder().find(target(), new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        String buttonText = button.getText();
        if (buttonText != null) {
          return buttonText.trim().equals(text) && button.isShowing();
        }
        return false;
      }
    });
    return new JButtonFixture(robot(), button);
  }

  @NotNull
  public JLabelFixture findLabel(@NotNull final String text) {
    JLabel label = waitUntilFound(robot(), target(), new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel label) {
        return text.equals(label.getText().replaceAll("(?i)<.?html>", ""));
      }
    });

    return new JLabelFixture(robot(), label);
  }

  @NotNull
  public String getValidationText(Validator.Severity severity) {
    robot().waitForIdle();

    final Icon severityIcon;
    switch (severity) {
      case ERROR:
        severityIcon = AllIcons.General.BalloonError;
        break;
      case WARNING:
        severityIcon = AllIcons.General.BalloonWarning;
        break;
      case INFO:
        severityIcon = AllIcons.General.BalloonInformation;
        break;
      default:
        severityIcon = null;
        Fail.failure("Invalid severity");
    }

    final JLabel error =
      waitUntilFound(robot(), new GenericTypeMatcher<JLabel>(JLabel.class) {
        @Override
        protected boolean isMatching(@NotNull JLabel component) {
          return component.isShowing() && StringUtil.isNotEmpty(component.getText()) && component.getIcon() == severityIcon;
        }
      });
    return error.getText();
  }
}
