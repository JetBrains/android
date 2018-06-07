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
package com.android.tools.idea.tests.gui.framework.fixture.welcome;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.welcome.config.FirstRunWizardMode;
import com.android.tools.idea.welcome.wizard.StudioFirstRunWelcomeScreen;
import com.android.tools.idea.welcome.wizard.deprecated.FirstRunWizardHost;
import com.intellij.openapi.wm.WelcomeScreen;
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiTask;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Collection;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowing;

public class FirstRunWizardFixture extends AbstractWizardFixture<FirstRunWizardFixture> {

  public static void show() {
    GuiTask.execute(() -> {
      JFrame welcomeFrame = new WelcomeFrame();
      WelcomeScreen welcomeScreen = StudioFlags.NPW_FIRST_RUN_WIZARD.get()
                                    ? new StudioFirstRunWelcomeScreen(FirstRunWizardMode.NEW_INSTALL)
                                    : new FirstRunWizardHost(FirstRunWizardMode.NEW_INSTALL);
      welcomeFrame.setContentPane(welcomeScreen.getWelcomePanel());
      welcomeScreen.setupFrame(welcomeFrame);

      welcomeFrame.setVisible(true);
    });
  }

  public static void close(Robot robot) {
    // After running a test, we may end up with two "Welcome Wizards": The initial Android Studio Welcome Wizard + a new started because
    // the "finish" button was pressed.
    GuiTask.execute(() -> {
      Collection<JFrame> welcomeFrames = robot.finder().findAll(Matchers.byType(JFrame.class));
      if (welcomeFrames.size() > 1) {
        // Disposing the WelcomeFrame/FlatWelcomeFrame resets WelcomeFrame.ourInstance to null, so we close all, and re-launch
        for (JFrame welcomeFrame : welcomeFrames) {
          welcomeFrame.setVisible(false);
          welcomeFrame.dispose();
        }
        WelcomeFrame.showNow();
      }
    });
  }

  public static FirstRunWizardFixture find(@NotNull Robot robot) {
    JFrame welcomeFrame = waitUntilShowing(robot, new GenericTypeMatcher<JFrame>(JFrame.class) {
      @Override
      protected boolean isMatching(@NotNull JFrame frame) {
        return "Android Studio Setup Wizard".equals(frame.getTitle());
      }
    });

    return new FirstRunWizardFixture(welcomeFrame, robot);
  }

  private FirstRunWizardFixture(@NotNull JFrame frame, @NotNull Robot robot) {
    super(FirstRunWizardFixture.class, robot, frame);
  }

  public CancelFirstRunDialogFixture findCancelPopup() {
    JDialog dialog = waitUntilShowing(robot(), Matchers.byTitle(JDialog.class, ""));
    return new CancelFirstRunDialogFixture(robot(), dialog);
  }

  @NotNull
  @Override
  public FirstRunWizardFixture clickCancel() {
    // Super method waits for the dialog to be dismissed after cancel, but that is not true for First Run Wizard (shows cancel pop-up)
    GuiTests.findAndClickCancelButton(this);
    return myself();
  }
}
