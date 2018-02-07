/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.adtui.ASGallery;
import com.android.tools.idea.npw.module.ModuleGalleryEntry;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import org.fest.swing.core.matcher.JLabelMatcher;
import org.fest.swing.fixture.JListFixture;
import org.fest.swing.fixture.JTextComponentFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class NewModuleWizardFixture extends AbstractWizardFixture<NewModuleWizardFixture> {

  public static NewModuleWizardFixture find(IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "Create New Module"));
    return new NewModuleWizardFixture(ideFrameFixture, dialog);
  }

  private final IdeFrameFixture myIdeFrameFixture;

  private NewModuleWizardFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog dialog) {
    super(NewModuleWizardFixture.class, ideFrameFixture.robot(), dialog);
    myIdeFrameFixture = ideFrameFixture;
  }

  @NotNull
  public NewModuleWizardFixture chooseModuleType(String name) {
    JListFixture listFixture = new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class));
    listFixture.replaceCellReader((list, index) -> ((ModuleGalleryEntry)list.getModel().getElementAt(index)).getName());
    listFixture.clickItem(name);
    return this;
  }

  @NotNull
  public NewModuleWizardFixture setModuleName(String name) {
    new JTextComponentFixture(robot(), robot().finder().findByName(target(), "ModuleName", JTextField.class)).deleteText().enterText(name);
    return this;
  }

  @NotNull
  public NewModuleWizardFixture setFileName(String name) {
    TextFieldWithBrowseButton panel = robot().finder().findByLabel(target(), "File name:", TextFieldWithBrowseButton.class);
    new JTextComponentFixture(robot(), robot().finder().findByType(panel, JTextField.class)).deleteText().enterText(name);
    return this;
  }

  @NotNull
  public NewModuleWizardFixture setSubprojectName(String name) {
    new JTextComponentFixture(robot(), robot().finder().findByLabel(target(), "Subproject name:", JTextField.class))
      .deleteText().enterText(name);
    return this;
  }

  @NotNull
  public NewModuleWizardFixture chooseActivity(String activity) {
    new JListFixture(robot(), robot().finder().findByType(target(), ASGallery.class)).clickItem(activity);
    return this;
  }

  @NotNull
  public ConfigureAndroidModuleStepFixture<NewModuleWizardFixture> getConfigureAndroidModuleStep() {
    JRootPane rootPane = findStepWithTitle("Configure the new module");
    return new ConfigureAndroidModuleStepFixture<>(this, rootPane);
  }

  @NotNull
  public ConfigureBasicActivityStepFixture<NewModuleWizardFixture> getConfigureActivityStep() {
    JRootPane rootPane = findStepWithTitle("Configure Activity");
    return new ConfigureBasicActivityStepFixture<>(this, rootPane);
  }

  @NotNull
  public NewModuleWizardFixture clickNextToStep(String name) {
    GuiTests.findAndClickButton(this, "Next");
    Wait.seconds(5).expecting("next step to appear").until(
      () -> robot().finder().findAll(target(), JLabelMatcher.withText(name).andShowing()).size() == 1);
    return this;
  }

  @NotNull
  public ConfigureJavaLibraryStepFixture<NewModuleWizardFixture> getConfigureJavaLibaryStepFixture() {
    return new ConfigureJavaLibraryStepFixture<>(this, target().getRootPane());
  }

  @NotNull
  public IdeFrameFixture clickFinish() {
    super.clickFinish(Wait.seconds(5));

    // Wait for gradle project importing to finish
    Wait.seconds(30).expecting("Modal Progress Indicator to finish")
      .until(() -> {
        robot().waitForIdle();
        return !ProgressManager.getInstance().hasModalProgressIndicator();
      });

    return myIdeFrameFixture;
  }
}
