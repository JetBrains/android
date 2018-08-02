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
package com.android.tools.idea.tests.gui.uibuilder;

import com.android.tools.adtui.TextAccessors;
import com.android.tools.idea.editors.theme.ui.ResourceComponent;
import com.android.tools.idea.tests.gui.framework.GuiTestRule;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.RunIn;
import com.android.tools.idea.tests.gui.framework.TestGroup;
import com.android.tools.idea.tests.gui.framework.fixture.ChooseResourceDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.NewThemeDialogFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ResourceComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorFixture;
import com.android.tools.idea.tests.gui.framework.fixture.theme.ThemeEditorTableFixture;
import com.android.tools.idea.tests.gui.theme.ThemeEditorGuiTestUtils;
import com.intellij.BundleBase;
import com.intellij.testGuiFramework.framework.GuiTestRemoteRunner;
import org.fest.swing.core.GenericTypeMatcher;
import org.fest.swing.data.TableCell;
import org.fest.swing.fixture.JTableCellFixture;
import org.jetbrains.annotations.NotNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.TimeUnit;

import static org.fest.swing.data.TableCell.row;

@RunWith(GuiTestRemoteRunner.class)
public class ThemeEditorTest {

  @Rule public final GuiTestRule guiTest = new GuiTestRule().withTimeout(5, TimeUnit.MINUTES);

  /**
   * To verify that the layout preview renders appropriately with different themes and API selections
   * <p>
   * This is run to qualify releases. Please involve the test team in substantial changes.
   * <p>
   * TT ID: 7ba5466f-78b0-43fa-8739-602f35c444d8
   * <p>
   *   <pre>
   *   Test Steps:
   *   1. Open Android Studio and import a simple application
   *   2. Open Theme Editor (Verify 1) (Tools > Theme Editor)
   *   3. From the right pane, select a different theme from the list, say a dark theme like Material Dark (Verify 2)
   *   4. Choose a different API from the list, say Android 5.0 or Android 4.0.3 (Verify 3)
   *   5. Choose a different device from the list, say N5 or N6 (Verify 4)
   *   6. Switch between portrait and landscape modes (Verify 5)
   *   7. Select Theme and then click on New Theme and enter a name and choose a parent theme from the list and click OK (Verify 6)
   *   8. On the newly created theme, click on a resource , say android:colorBackground and select a different color from the color picker (Verify 7)
   *   9. Repeat step 8 with a default system theme, like Material Light (Verify 8)
   *   Verify:
   *   1. Preview is displayed with the default theme selection (App Theme)
   *   2. Preview is updated with the newly selected theme
   *   3. Preview is updated for the other API's and there are no rendering errors
   *   4. Preview is updated for the other devices and there are no rendering errors
   *   5. Preview is updated appropriately without any errors
   *   6. New theme is created and is displayed as the selected theme for the module.
   *   7. Preview is updated with the newly selected color for the background
   *   8. A prompt will be displayed to the user mentioning that that the selected system theme is ready-only
   *   and that they need to create a new theme with the selected background color.
   *   </pre>
   */
  @RunIn(TestGroup.FAST_BAZEL)
  @Test
  public void themeEditor() throws Exception {
    guiTest.importSimpleLocalApplication();
    ThemeEditorFixture themeEditor = ThemeEditorGuiTestUtils.openThemeEditor(guiTest.ideFrame());
    ThemeEditorTableFixture themeEditorTable = themeEditor.getPropertiesTable();

    themeEditor.chooseTheme("Theme.AppCompat.NoActionBar")
      .chooseApiLevel("API 25", "25")
      .chooseDevice("Nexus 5", "Nexus 5")
      .switchOrientation("Landscape")
      .switchOrientation("Portrait")
      .createNewTheme("NewTheme", "Theme.AppCompat.NoActionBar");

    TableCell cell = row(1).column(0);
    JTableCellFixture colorCell = themeEditorTable.cell(cell);

    ResourceComponentFixture resourceComponent = new ResourceComponentFixture(guiTest.robot(), (ResourceComponent)colorCell.editor());
    colorCell.startEditing();
    Thread.sleep(3000);
    resourceComponent.getSwatchButton().click();
    ChooseResourceDialogFixture dialog = ChooseResourceDialogFixture.find(guiTest.robot());
    @SuppressWarnings("UseJBColor")
    Color color = new Color(255, 235, 59, 255);
    dialog.getColorPicker().setColorWithIntegers(color);
    dialog.clickOK();
    colorCell.stopEditing();

    themeEditor.chooseTheme("Theme.AppCompat.NoActionBar");
    colorCell.startEditing();
    resourceComponent.getSwatchButton().click();
    dialog = ChooseResourceDialogFixture.find(guiTest.robot());
    dialog.clickOK();
    NewThemeDialogFixture newThemeDialog = NewThemeDialogFixture.findDialog(guiTest.robot());
    GuiTests.waitUntilShowing(guiTest.robot(), newThemeDialog.target(), new GenericTypeMatcher<JLabel>(JLabel.class) {
      @Override
      protected boolean isMatching(@NotNull JLabel component) {
        String componentText = TextAccessors.getTextAccessor(component).getText();
        componentText = componentText == null ? "" : componentText.replaceAll(Character.toString(BundleBase.MNEMONIC), "");
        return componentText.contains("Read-Only");
      }
    });
    newThemeDialog.clickCancel();
    colorCell.stopEditing();
  }
}
