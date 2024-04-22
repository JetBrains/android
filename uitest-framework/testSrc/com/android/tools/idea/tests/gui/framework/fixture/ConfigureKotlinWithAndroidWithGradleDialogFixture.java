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

import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class ConfigureKotlinWithAndroidWithGradleDialogFixture{
  @NotNull private final IdeFrameFixture myIdeFrame;
  @NotNull private final JDialog myDialog;
  @NotNull static final String TITLE = ""; //Title is not appearing after IDEA 2023.3 merge. b/316416680
  @NotNull static final String DIALOG_CONTAINS = "Kotlin compiler and runtime version:";

  public static ConfigureKotlinWithAndroidWithGradleDialogFixture find(IdeFrameFixture ideFrame) {
    JLabel jLabel = ideFrame.robot().finder().find(Matchers.byText(JLabel.class, DIALOG_CONTAINS));

    //Finding the dialog based on the content
    Collection<JDialog> allDialogFound = ideFrame.robot().finder().findAll(
      jLabel.getParent().getParent().getParent().getParent().getParent().getParent().getParent(),
      Matchers.byTitle(JDialog.class, TITLE));
    JDialog dialog = allDialogFound.iterator().next();

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
    // OK button can take a while to be enabled. We just wait for the button to be available, and
    // then we explicitly wait a long time for it to be enabled. This lets us have a less
    // strict wait for the button to be clickable.
    Wait.seconds(TimeUnit.MINUTES.toSeconds(5))
      .expecting("OK button to be enabled")
      .until(() -> okButton.isEnabled());
    myIdeFrame.robot().click(okButton);
    waitForDialogToDisappear();
  }

  private void waitForDialogToDisappear() {
    Wait.seconds(10).expecting(myDialog.getTitle() + " dialog to disappear")
      .until(() -> !myDialog.isShowing());
  }
}
