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
import com.android.tools.idea.wizard.dynamic.DynamicWizard;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.*;
import static com.google.common.truth.Truth.assertThat;

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
    waitUntilShowing(robot(), rootPane, JLabelMatcher.withText(title));
    return rootPane;
  }

  @NotNull
  protected JRootPane findStepWithTitle(@NotNull final String title,
                                        long secondsToWait) {
    JRootPane rootPane = target().getRootPane();
    waitUntilShowing(robot(), rootPane, JLabelMatcher.withText(title), secondsToWait);
    return rootPane;
  }

  @NotNull
  public S clickNext() {
    findAndClickButtonWhenEnabled(this, "Next");
    return myself();
  }

  @NotNull
  public S clickFinish() {
    findAndClickButtonWhenEnabled(this, "Finish");
    Wait.seconds(10).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myself();
  }

  @NotNull
  public S clickFinish(@NotNull Wait waitForDialogDisappear) {
    findAndClickButtonWhenEnabled(this, "Finish");
    waitForDialogDisappear.expecting("dialog to disappear").until(() -> !target().isShowing());
    return myself();
  }

  @NotNull
  public S clickCancel() {
    findAndClickCancelButton(this);
    Wait.seconds(5).expecting("dialog to disappear").until(() -> !target().isShowing());
    return myself();
  }

  @NotNull
  public S clickPrevious() {
    findAndClickButtonWhenEnabled(this, "Previous");
    return myself();
  }

  public S assertStepIcon(Icon expectedIcon) {
    assertThat(robot().finder().findByName("right_icon", JLabel.class).getIcon()).isEqualTo(expectedIcon);
    return myself();
  }
}
