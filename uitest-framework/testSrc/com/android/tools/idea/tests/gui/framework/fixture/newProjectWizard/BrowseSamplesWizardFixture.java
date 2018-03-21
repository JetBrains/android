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
package com.android.tools.idea.tests.gui.framework.fixture.newProjectWizard;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JTreeFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;


public class BrowseSamplesWizardFixture extends AbstractWizardFixture<BrowseSamplesWizardFixture> {

  BrowseSamplesWizardFixture(@NotNull JDialog dialog, @NotNull Robot robot) {
    super(BrowseSamplesWizardFixture.class, robot, dialog);
  }

  @NotNull
  public static BrowseSamplesWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "Import Sample"));
    return new BrowseSamplesWizardFixture(dialog, robot);
  }

  @NotNull
  public BrowseSamplesWizardFixture selectSample(@NotNull String samplePath) {
    JTree tree = robot().finder().findByType(target(), com.intellij.ui.treeStructure.Tree.class, true);
    new JTreeFixture(robot(), tree).clickPath(samplePath);
    return this;
  }

  @NotNull
  public ConfigureAndroidProjectStepFixture<BrowseSamplesWizardFixture> getConfigureFormFactorStep() {
    JRootPane rootPane = findStepWithTitle("Configure Sample");
    return new ConfigureAndroidProjectStepFixture<>(this, rootPane);
  }

  @NotNull
  public BrowseSamplesWizardFixture clickFinish() {
    super.clickFinish(Wait.seconds(120));
    return this;
  }
}
