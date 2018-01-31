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

import com.android.tools.idea.gradle.project.build.invoker.GradleInvocationResult;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import com.intellij.util.ui.UIUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.RuleChain;
import org.junit.rules.Verifier;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.awt.*;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;
import static com.google.common.truth.Truth.assertThat;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

@RunWith(GuiTestRunner.class)
public class GuiTestRuleTest {

  private final Verifier guiTestVerifier = new Verifier() {
    @Override
    protected void verify() {
      assertThat(GuiTests.windowsShowing()).containsExactly(WelcomeFrame.getInstance());
    }
  };

  private final ExpectedException exception = ExpectedException.none();

  private final GuiTestRule guiTest = new GuiTestRule();

  @Rule public final RuleChain ruleChain = RuleChain.outerRule(guiTestVerifier).around(exception).around(guiTest);

  @Test
  public void makeSimpleApplication() throws Exception {
    GradleInvocationResult result = guiTest.importSimpleLocalApplication().invokeProjectMake();
    assertThat(result.isBuildSuccessful()).named("Gradle build successful").isTrue();
  }

  @Test
  public void createNewProjectDialogLeftShowing() {
    exception.expectMessage("Create New Project");
    guiTest.welcomeFrame().createNewProject();
  }

  @Test
  public void testModalDialogsLeftOpen() {
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

    JDialog dialog = waitUntilShowing(guiTest.robot(), Matchers.byTitle(JDialog.class, "Click a button"));
    JButton yesButton = guiTest.robot().finder().find(dialog, Matchers.byText(JButton.class, "Yes"));
    guiTest.robot().click(yesButton);
  }
}
