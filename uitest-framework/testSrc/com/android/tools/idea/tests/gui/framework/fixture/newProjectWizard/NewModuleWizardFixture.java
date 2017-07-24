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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.adtui.ASGallery;
import com.intellij.openapi.progress.ProgressManager;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NewModuleWizardFixture extends AbstractWizardFixture<NewModuleWizardFixture> {
  @NotNull
  public static NewModuleWizardFixture find(@NotNull IdeFrameFixture fixture) {
    Robot robot = fixture.robot();
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Create New Module"));
    return new NewModuleWizardFixture(robot, dialog);
  }

  private NewModuleWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(NewModuleWizardFixture.class, robot, target);
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture getConfigureAndroidModuleStep() {
    JRootPane rootPane = findStepWithTitle("Configure the new module");
    return new ConfigureAndroidModuleStepFixture(robot(), rootPane);
  }

  public NewModuleWizardFixture chooseModuleType(@NotNull String activity) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class));
    listFixture.clickItem(activity);
    return this;
  }

  public NewModuleWizardFixture chooseActivity(@NotNull String activity) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class));
    listFixture.clickItem(activity);
    return this;
  }

  @NotNull
  @Override
  public NewModuleWizardFixture clickFinish() {
    super.clickFinish();

    // Wait for gradle project importing to finish
    Wait.seconds(30).expecting("Modal Progress Indicator to finish")
      .until(() -> {
        robot().waitForIdle();
        return !ProgressManager.getInstance().hasModalProgressIndicator();
      });
    return myself();
  }
}
