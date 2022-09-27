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

import static junit.framework.TestCase.assertTrue;

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class NewFolderWizardFixture {

  private final IdeFrameFixture myIdeFrame;
  JDialog myDialog;
  static int count = 0;

  public static NewFolderWizardFixture find2(IdeFrameFixture ideFrame) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrame.robot(),
                                               Matchers.byTitle(JDialog.class, "New Android Component"));
    assertTrue(dialog.isVisible());
    return new NewFolderWizardFixture(ideFrame, dialog);
  }

  public static NewFolderWizardFixture open(IdeFrameFixture ideFrame) {
    // Opening new java folder creation panel till the combo box is seen. Due to a product bug b/240730295.
    while (count < 6) {
      ideFrame.getProjectView().selectAndroidPane();
      ideFrame.invokeMenuPath("File", "New", "Folder", "Java Folder");
      JDialog dialog = GuiTests.waitUntilShowing(ideFrame.robot(),
                                                 Matchers.byTitle(JDialog.class, "New Android Component"));
      assertTrue(dialog.isVisible());
      if (!ideFrame.robot().finder().findAll(dialog, Matchers.byType(JComboBox.class)).isEmpty()) {
        return new NewFolderWizardFixture(ideFrame, dialog);
      }
      JButton cancelButton = ideFrame.robot().finder().find(dialog, Matchers.byText(JButton.class, "Cancel"));
      ideFrame.robot().click(cancelButton);
      count++;
    }
    throw new AssertionError("The combo box is not showing up.");
  }

  private NewFolderWizardFixture(@NotNull IdeFrameFixture ideFrame, @NotNull JDialog target) {
    myIdeFrame = ideFrame;
    myDialog = target;
  }

  @NotNull
  public NewFolderWizardFixture selectResFolder(@NotNull String resFolder) {
    JComboBoxFixture comboBoxFixture = new JComboBoxFixture(myIdeFrame.robot(),
                                                            myIdeFrame.robot().finder().findByType(myDialog, JComboBox.class, true));
    assertTrue(comboBoxFixture.isEnabled());
    comboBoxFixture.click();
    comboBoxFixture.selectItem(resFolder);
    return this;
  }

  @NotNull
  public IdeFrameFixture clickFinishAndWaitForSyncToComplete() {
    return myIdeFrame.actAndWaitForGradleProjectSyncToFinish(Wait.seconds(120), this::clickFinishAndGetIdeFrame);
  }

  private IdeFrameFixture clickFinishAndGetIdeFrame() {
    clickFinish();
    IdeFrameFixture ideFrameFixture = IdeFrameFixture.find(myIdeFrame.robot());
    ideFrameFixture.requestFocusIfLost();
    return ideFrameFixture;
  }

  private void clickFinish() {
    String buttonName = "Finish";
    JButton finishButton = myIdeFrame.robot().finder().find(myDialog, Matchers.byText(JButton.class, buttonName));
    finishButton.isEnabled();
    myIdeFrame.robot().click(finishButton);
    waitForDialogToDisappear();
  }

  public void clickCancel() {
    String buttonName = "Cancel";
    JButton cancelButton = myIdeFrame.robot().finder().find(myDialog, Matchers.byText(JButton.class, buttonName));
    cancelButton.isEnabled();
    myIdeFrame.robot().click(cancelButton);
    waitForDialogToDisappear();
  }

  private void waitForDialogToDisappear() {
    Wait.seconds(10).expecting(myDialog.getTitle() + " dialog to disappear")
      .until(() -> !myDialog.isShowing());
  }
}