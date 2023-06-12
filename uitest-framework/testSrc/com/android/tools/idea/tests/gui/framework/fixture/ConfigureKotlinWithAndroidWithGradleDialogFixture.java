/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture;

import static junit.framework.Assert.assertTrue;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.uibuilder.handlers.motion.editor.adapters.Annotations.NotNull;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.timing.Wait;

public class ConfigureKotlinWithAndroidWithGradleDialogFixture{
  @NotNull private final IdeFrameFixture myIdeFrame;
  @NotNull private final JDialog myDialog;
  @NotNull static final String TITLE = "Configure Kotlin with Android with Gradle";

  public static ConfigureKotlinWithAndroidWithGradleDialogFixture find(IdeFrameFixture ideFrame) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrame.robot(),
                                               Matchers.byTitle(JDialog.class, TITLE));
    assertTrue(dialog.isVisible());
    return new ConfigureKotlinWithAndroidWithGradleDialogFixture(ideFrame, dialog);
  }

  private ConfigureKotlinWithAndroidWithGradleDialogFixture(@NotNull IdeFrameFixture ideFrameFixture,
                                           @NotNull JDialog dialog) {
    myIdeFrame = ideFrameFixture;
    myDialog = dialog;
  }

  @NotNull
  public List<String> getKotlinVersions() {
    JLabel kotlinVersionLabel = myIdeFrame.robot().finder().find(myDialog, Matchers.byText(JLabel.class, "Kotlin compiler and runtime version:"));
    JComboBoxFixture comboBox = new JComboBoxFixture(myIdeFrame.robot(), myIdeFrame.robot().finder().findByType(kotlinVersionLabel.getParent(), JComboBox.class));
    List kotlinVersions = Arrays.asList(comboBox.contents());
    return kotlinVersions;
  }

  @NotNull
  public List<String> getSingleModuleComboBoxDetails() {
    String radioButtonName = "Single module:";
    Collection<JRadioButton> allRadioButtonsFound = myIdeFrame.robot().finder().findAll(myDialog, Matchers.byType(JRadioButton.class));
    for (JRadioButton radioButton: allRadioButtonsFound) {
      if (radioButtonName.equalsIgnoreCase(radioButton.getText())) {
        JComboBoxFixture comboBox = new JComboBoxFixture(myIdeFrame.robot(), myIdeFrame.robot().finder().findByType(radioButton.getParent(), JComboBox.class));
        List modules = Arrays.asList(comboBox.contents());
        return modules;
      }
    }
    throw new AssertionError("Radio button - 'Combo box associated with the radio button - 'Single Module:' not found / accessible.");
  }

  @NotNull
  public boolean clickRadioButtonWithName(String radioButtonName) {
    Collection<JRadioButton> allRadioButtonsFound = myIdeFrame.robot().finder().findAll(myDialog, Matchers.byType(JRadioButton.class));
    for (JRadioButton radioButton : allRadioButtonsFound) {
      if (radioButtonName.equalsIgnoreCase(radioButton.getText())) {
        myIdeFrame.robot().click(radioButton);
        return true;
      }
    }
    return false;
  }

  public void clickOkAndWaitDialogDisappear() {
    String buttonName = "OK";
    JButton okButton = myIdeFrame.robot().finder().find(myDialog, Matchers.byText(JButton.class, buttonName));
    okButton.isEnabled();
    myIdeFrame.robot().click(okButton);
    waitForDialogToDisappear();
  }

  private void waitForDialogToDisappear() {
    Wait.seconds(10).expecting(myDialog.getTitle() + " dialog to disappear")
      .until(() -> !myDialog.isShowing());
  }
}
