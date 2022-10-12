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

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndKeypressButton;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.google.common.collect.Lists;
import com.intellij.openapi.project.ProjectManager;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JDialog;
import javax.swing.JLabel;
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
  public String getActivityName(String activityName) {

    List<JLabel> allLabels = Lists.newArrayList(robot().finder().findAll(target(), Matchers.byType(JLabel.class)));
    for (JLabel label : allLabels) {
      if (label.getText() != null && label.getText().contains(activityName)) {
        return label.getText();
      }
    }
    return "No matching activity found";
  }


  @NotNull
  public ChooseAndroidProjectStepFixture<NewProjectWizardFixture> getChooseAndroidProjectStep() {
      return new ChooseAndroidProjectStepFixture<>(this, findStepWithTitle("Templates"));
  }

  @NotNull
  public ConfigureCppStepFixture<NewProjectWizardFixture> getConfigureCppStepFixture() {
    JRootPane rootPane = findStepWithTitle("Native C++", 30);
    return new ConfigureCppStepFixture<>(this, rootPane);
  }

  @NotNull
  public NewProjectWizardFixture clickFinish(@NotNull Wait projectOpen, @NotNull Wait indexing) {
    // Occasionally the root pane will not be able to resolve the screen coordinates properly.
    // This works around the clicking issue by using the keyboard instead.
    findAndKeypressButton(this, "Finish", KeyEvent.VK_ENTER);
    projectOpen
      .expecting("project opened")
      .until(() -> GuiQuery.getNonNull(() -> ProjectManager.getInstance().getOpenProjects().length == 1));
    GuiTests.waitForProjectIndexingToFinish(ProjectManager.getInstance().getOpenProjects()[0], indexing);
    Assume.assumeTrue(GuiQuery.getNonNull(() -> !target().isShowing()));
    return myself();
  }

  @NotNull
  public IdeFrameFixture clickFinishAndWaitForSyncToFinish() {
    return IdeFrameFixture.actAndWaitForGradleProjectSyncToFinish(Wait.seconds(120), this::clickFinishAndGetIdeFrame);
  }

  @NotNull
  public IdeFrameFixture clickFinishAndWaitForSyncToFinish(@NotNull Wait waitSync) {
    return IdeFrameFixture.actAndWaitForGradleProjectSyncToFinish(waitSync, this::clickFinishAndGetIdeFrame);
  }

  private IdeFrameFixture clickFinishAndGetIdeFrame() {
    clickFinish();
    IdeFrameFixture ideFrameFixture = IdeFrameFixture.find(robot());
    ideFrameFixture.requestFocusIfLost();
    return ideFrameFixture;
  }

  private void clickFinish() {
    clickFinish(Wait.seconds(15), Wait.seconds(240));
  }
}
