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
package com.android.tools.idea.tests.gui.framework.fixture.wizard;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickCancelButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndKeypressButton;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilGone;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.diagnostic.PerformanceWatcher;
import com.intellij.openapi.util.text.StringUtil;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.util.function.Predicate;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JRootPane;
import javax.swing.text.JTextComponent;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.core.matcher.JTextComponentMatcher;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.exception.WaitTimedOutError;
import org.fest.swing.fixture.ContainerFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

/**
 * Base class for fixtures which control wizards that extend {@link com.android.tools.idea.wizard.model.WizardModel}
 */
public abstract class AbstractWizardFixture<S> extends ComponentFixture<S, JRootPane> implements ContainerFixture<JRootPane> {

  public AbstractWizardFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull JDialog dialog) {
    super(selfType, robot, dialog.getRootPane());
  }

  public AbstractWizardFixture(@NotNull Class<S> selfType, @NotNull Robot robot, @NotNull JFrame frame) {
    super(selfType, robot, frame.getRootPane());
  }

  @NotNull
  protected JRootPane findStepWithTitle(@NotNull final String title) {
    waitUntilShowing(robot(), target(), JLabelMatcher.withText(title));
    return target();
  }

  @NotNull
  protected JRootPane findStepWithTitle(@NotNull final String title, long secondsToWait) {
    waitUntilShowing(robot(), target(), JLabelMatcher.withText(title), secondsToWait);
    return target();
  }

  @NotNull
  public S clickNext() {
    GuiTests.waitUntilShowingAndEnabled(robot(), target(), Matchers.byText(JButton.class, "Next"));
    // Occasionally the root pane will not be able to resolve the screen coordinates properly.
    // This works around the clicking issue by using the keyboard instead.
    findAndKeypressButton(this, "Next", KeyEvent.VK_ENTER);
    return myself();
  }

  protected void clickFinish(@NotNull Wait waitForDialogDisappear) {
    // TODO: if clicking is flakey, change this to key press instead
    findAndClickButton(this, "Finish");
    try {
      waitForDialogDisappear.expecting("dialog to disappear").until(
        () -> GuiQuery.getNonNull(() -> !target().isShowing())
      );
    }
    catch (WaitTimedOutError ex) {
      PerformanceWatcher.getInstance().dumpThreads("finish-timeout", true, false);
      throw ex;
    }
  }

  @NotNull
  public S clickCancel() {
    findAndClickCancelButton(this);
    Wait.seconds(5).expecting("dialog to disappear").until(
      () -> GuiQuery.getNonNull(() ->!target().isShowing())
    );
    return myself();
  }

  @NotNull
  public S waitUntilStepErrorMessageIsGone() {
    waitUntilGone(robot(), target(), new GenericTypeMatcher<JTextComponent>(JTextComponent.class) {
      @Override
      protected boolean isMatching(@NotNull JTextComponent component) {
        // Note: When there are no errors, the ValidationText component is kept visible (for layout reasons).
        return "ValidationText".equals(component.getName()) && component.isShowing() && !getPlainText(component).trim().isEmpty();
      }
    });

    return myself();
  }

  @NotNull
  public S assertStepErrorMessage(@NotNull Predicate<String> predicate) {
    JTextComponent errorText = waitUntilShowing(robot(), target(), JTextComponentMatcher.withName("ValidationText"));
    assertThat(predicate.test(getPlainText(errorText))).isTrue();
    return myself();
  }

  private static String getPlainText(@NotNull JTextComponent textComponent) {
    return StringUtil.removeHtmlTags(textComponent.getText());
  }
}
