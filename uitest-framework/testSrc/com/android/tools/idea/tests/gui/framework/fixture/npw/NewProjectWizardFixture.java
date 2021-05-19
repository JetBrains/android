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
package com.android.tools.idea.tests.gui.framework.fixture.npw;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickButton;
import static org.jetbrains.android.util.AndroidBundle.message;

import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.project.ProjectManager;
import javax.swing.JDialog;
import javax.swing.JRootPane;
import org.fest.swing.core.Robot;
import org.fest.swing.edt.GuiQuery;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.junit.Assume;

public class NewProjectWizardFixture extends AbstractWizardFixture<NewProjectWizardFixture> {
  @NotNull
  public static NewProjectWizardFixture find(@NotNull Robot robot) {
    JDialog dialog = GuiTests.waitUntilShowing(robot, Matchers.byTitle(JDialog.class, "New Project"));
    return new NewProjectWizardFixture(robot, dialog);
  }

  private NewProjectWizardFixture(@NotNull Robot robot, @NotNull JDialog target) {
    super(NewProjectWizardFixture.class, robot, target);
  }

  @NotNull
  public ConfigureNewAndroidProjectStepFixture<NewProjectWizardFixture> getConfigureNewAndroidProjectStep() {
    JRootPane rootPane = findStepWithTitle("Minimum SDK");
    return new ConfigureNewAndroidProjectStepFixture<>(this, rootPane);
  }

  @NotNull
  public ChooseAndroidProjectStepFixture<NewProjectWizardFixture> getChooseAndroidProjectStep() {
    if (StudioFlags.NPW_NEW_MODULE_WITH_SIDE_BAR.get()) {
      return new ChooseAndroidProjectStepFixture<>(this, findStepWithTitle("Templates"));
    }
    JRootPane rootPane = findStepWithTitle(message("android.wizard.project.new.choose"));
    return new ChooseAndroidProjectStepFixture<>(this, rootPane);
  }

  @NotNull
  public ConfigureCppStepFixture<NewProjectWizardFixture> getConfigureCppStepFixture() {
    JRootPane rootPane = findStepWithTitle("Native C++", 30);
    return new ConfigureCppStepFixture<>(this, rootPane);
  }

  @NotNull
  public NewProjectWizardFixture clickFinish(@NotNull Wait projectOpen, @NotNull Wait indexing) {
    findAndClickButton(this, "Finish");
    projectOpen
      .expecting("project opened")
      .until(() -> GuiQuery.getNonNull(() -> ProjectManager.getInstance().getOpenProjects().length == 1));
    GuiTests.waitForProjectIndexingToFinish(ProjectManager.getInstance().getOpenProjects()[0], indexing);
    Assume.assumeTrue(GuiQuery.getNonNull(() -> !target().isShowing()));
    return myself();
  }

  @NotNull
  public IdeFrameFixture clickFinishAndWaitForSyncToFinish() {
    return IdeFrameFixture.actAndWaitForGradleProjectSyncToFinish(Wait.seconds(120), () -> clickFinishAndGetIdeFrame());
  }

  @NotNull
  public IdeFrameFixture clickFinishAndWaitForSyncToFinish(@NotNull Wait waitSync) {
    return IdeFrameFixture.actAndWaitForGradleProjectSyncToFinish(waitSync, () -> clickFinishAndGetIdeFrame());
  }

  private IdeFrameFixture clickFinishAndGetIdeFrame() {
    clickFinish();
    IdeFrameFixture ideFrameFixture = IdeFrameFixture.find(robot());
    ideFrameFixture.requestFocusIfLost();
    return ideFrameFixture;
  }

  @NotNull
  private NewProjectWizardFixture clickFinish() {
    return clickFinish(Wait.seconds(15), Wait.seconds(240));
  }
}
