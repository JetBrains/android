/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework;

import com.android.tools.idea.tests.gui.framework.fixture.WelcomeFrameFixture;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.ui.UIUtil;
import org.fest.swing.core.GenericTypeMatcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Timeout;
import org.junit.rules.Verifier;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

@RunWith(GuiTestRunner.class)
public class GuiTestRuleTest {

  private final Verifier guiTestVerifier = new Verifier() {
    @Override
    protected void verify() throws Throwable {
      assertThat(GuiTests.windowsShowing()).containsExactly(WelcomeFrame.getInstance());
    }
  };

  private final ExpectedException exception = ExpectedException.none();

  private final GuiTestRule guiTest = new GuiTestRule(new Timeout(5, TimeUnit.SECONDS)).withLeakCheck();

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(guiTestVerifier).around(exception).around(guiTest);

  @Test
  public void createNewProjectDialogLeftShowing() throws Exception {
    exception.expectMessage("Create New Project");
    WelcomeFrameFixture.find(guiTest.robot()).createNewProject();
  }

  @Test
  public void testModalDialogsLeftOpen() throws IOException {
    exception.expectMessage(allOf(
      containsString("Modal dialog showing"),
      containsString("javax.swing.JDialog with title 'Surprise!'"),
      containsString("javax.swing.JDialog with title 'Click a button'")));

    UIUtil.invokeLaterIfNeeded(
      () -> {
        final JOptionPane optionPane =
          new JOptionPane("Do you want another modal dialog?", JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION);

        final JDialog dialog = new JDialog((Frame)null, "Click a button", true);
        dialog.setContentPane(optionPane);
        optionPane.addPropertyChangeListener(
          e -> {
            String prop = e.getPropertyName();
            if (dialog.isVisible() && (e.getSource() == optionPane) && (prop.equals(JOptionPane.VALUE_PROPERTY))) {
              JOptionPane.showMessageDialog(optionPane, "Here's another modal dialog", "Surprise!", JOptionPane.INFORMATION_MESSAGE);
            }
          });
        dialog.pack();
        dialog.setVisible(true);
      });

    JDialog dialog = waitUntilShowing(guiTest.robot(), new GenericTypeMatcher<JDialog>(JDialog.class) {
      @Override
      protected boolean isMatching(@NotNull JDialog dialog) {
        return "Click a button".equals(dialog.getTitle());
      }
    });
    JButton yesButton = waitUntilShowing(guiTest.robot(), dialog, new GenericTypeMatcher<JButton>(JButton.class) {
      @Override
      protected boolean isMatching(@NotNull JButton button) {
        String buttonText = button.getText();
        return buttonText != null && buttonText.trim().equals("Yes");
      }
    });
    guiTest.robot().click(yesButton);
  }

  @Test
  public void testTimeout() throws InterruptedException {
    exception.expectMessage("test timed out");
    Thread.sleep(6000);
  }
}
