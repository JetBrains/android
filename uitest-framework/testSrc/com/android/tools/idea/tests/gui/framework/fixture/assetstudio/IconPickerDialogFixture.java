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
package com.android.tools.idea.tests.gui.framework.fixture.assetstudio;

import static com.android.tools.idea.tests.gui.framework.GuiTests.findAndClickOkButton;

import com.android.tools.idea.npw.assetstudio.ui.IconPickerDialog;
import com.android.tools.idea.tests.gui.framework.fixture.IdeaDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SearchTextFieldFixture;
import com.intellij.ui.SearchTextField;
import javax.swing.JComboBox;
import javax.swing.JTable;
import org.fest.swing.core.MouseButton;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.fixture.JTableFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;

public class IconPickerDialogFixture extends IdeaDialogFixture<IconPickerDialog> {

  private final AssetStudioWizardFixture myParentFixture;

  @NotNull
  public static IconPickerDialogFixture find(@NotNull AssetStudioWizardFixture parentFixture) {
    IconPickerDialogFixture dialogFixture = new IconPickerDialogFixture(
      parentFixture, IdeaDialogFixture.find(parentFixture.robot(), IconPickerDialog.class));
    JTableFixture tableFixture =
      new JTableFixture(dialogFixture.robot(), parentFixture.robot().finder().findByType(dialogFixture.target(), JTable.class));
    Wait.seconds(60L).expecting("Table should be populated.").until(() -> tableFixture.contents().length > 1);
    return dialogFixture;
  }

  private IconPickerDialogFixture(
    @NotNull AssetStudioWizardFixture assetStudioWizardFixture, @NotNull DialogAndWrapper<IconPickerDialog> dialogAndWrapper) {
    super(assetStudioWizardFixture.robot(), dialogAndWrapper);
    myParentFixture = assetStudioWizardFixture;
  }

  @SuppressWarnings("UnusedReturnValue")
  @NotNull
  public IconPickerDialogFixture waitForLoading() {
    JComboBoxFixture categoriesComboFixture = new JComboBoxFixture(robot(), robot().finder().findByName("Categories", JComboBox.class));
    Wait.seconds(60L).expecting("Waiting for icons to load and categories to become enabled").until(categoriesComboFixture::isEnabled);
    return this;
  }

  @NotNull
  public AssetStudioWizardFixture clickOk() {
    findAndClickOkButton(this);
    return myParentFixture;
  }

  public IconPickerDialogFixture filterByNameAndSelect(@NotNull String name) {
    waitForLoading();

    JTableFixture tableFixture =
      new JTableFixture(robot(), robot().finder().findByType(target(), JTable.class));
    Wait.seconds(60L).expecting("Table should be populated.").until(() -> tableFixture.contents().length > 10);
    new SearchTextFieldFixture(robot(), robot().finder().findByType(this.target(), SearchTextField.class))
      .enterText(name);
    Wait.seconds(60L).expecting("Table should be populated.").until(() -> tableFixture.contents().length > 0);
    new JTableFixture(robot(), robot().finder().findByType(target(), JTable.class))
      .click(TableCell.row(0).column(0), MouseButton.LEFT_BUTTON);
    return this;
  }
}
