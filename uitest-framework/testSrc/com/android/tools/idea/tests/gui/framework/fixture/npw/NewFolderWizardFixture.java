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

import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.IdeFrameFixture;
import com.android.tools.idea.tests.gui.framework.fixture.wizard.AbstractWizardFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class NewFolderWizardFixture extends AbstractWizardFixture<NewFolderWizardFixture> {
  private final IdeFrameFixture myIdeFrame;

  private NewFolderWizardFixture(@NotNull IdeFrameFixture ideFrameFixture, @NotNull JDialog target) {
    super(NewFolderWizardFixture.class, ideFrameFixture.robot(), target);
    this.myIdeFrame = ideFrameFixture;
  }

  @NotNull
  public static NewFolderWizardFixture find(@NotNull IdeFrameFixture ideFrameFixture) {
    JDialog dialog = GuiTests.waitUntilShowing(ideFrameFixture.robot(), Matchers.byTitle(JDialog.class, "New Android Component"));
    return new NewFolderWizardFixture(ideFrameFixture, dialog);
  }

  @NotNull
  public NewFolderWizardFixture selectResFolder(@NotNull String resFolder) {
    JComboBoxFixture comboBoxFixture =
      new JComboBoxFixture(robot(), GuiTests.waitUntilShowing(robot(), target(), Matchers.byType(JComboBox.class)));
    //comboBoxFixture.replaceCellReader((comboBox, index) -> ((SourceSetItem)comboBox.getItemAt(index)).getSourceSetName());
    comboBoxFixture.click();
    comboBoxFixture.selectItem(resFolder);
    return this;
  }

  @NotNull
  public IdeFrameFixture clickFinishAndWaitForSyncToComplete() {
    return IdeFrameFixture.actAndWaitForGradleProjectSyncToFinish(Wait.seconds(120), this::clickFinishAndGetIdeFrame);
  }

  private IdeFrameFixture clickFinishAndGetIdeFrame() {
    clickFinish();
    IdeFrameFixture ideFrameFixture = IdeFrameFixture.find(robot());
    ideFrameFixture.requestFocusIfLost();
    return ideFrameFixture;
  }

  private void clickFinish() {
    super.clickFinish(Wait.seconds(30));
  }


}