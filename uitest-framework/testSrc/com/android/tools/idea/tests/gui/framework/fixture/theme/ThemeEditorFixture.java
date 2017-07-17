/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.tests.gui.framework.fixture.theme;

import com.android.tools.idea.editors.theme.AttributesPanel;
import com.android.tools.idea.editors.theme.ThemeEditorComponent;
import com.android.tools.idea.editors.theme.preview.ThemePreviewComponent;
import com.android.tools.idea.tests.gui.framework.GuiTests;
import com.android.tools.idea.tests.gui.framework.fixture.ActionButtonFixture;
import com.android.tools.idea.tests.gui.framework.fixture.ComponentFixture;
import com.android.tools.idea.tests.gui.framework.fixture.HyperlinkLabelFixture;
import com.android.tools.idea.tests.gui.framework.fixture.SearchTextFieldFixture;
import com.android.tools.idea.tests.gui.framework.matcher.Matchers;
import com.android.tools.idea.ui.TextAccessors;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.actionSystem.impl.ActionButton;
import com.intellij.ui.HyperlinkLabel;
import com.intellij.ui.SearchTextField;
import org.fest.swing.core.Robot;
import org.fest.swing.fixture.JComboBoxFixture;
import org.fest.swing.timing.Wait;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilFound;
import static com.android.tools.idea.tests.gui.framework.GuiTests.waitUntilShowingAndEnabled;

public class ThemeEditorFixture extends ComponentFixture<ThemeEditorFixture, ThemeEditorComponent> {
  private final JComboBoxFixture myThemesComboBox;
  private @Nullable JComboBoxFixture myModulesComboBox;

  public ThemeEditorFixture(@NotNull Robot robot, @NotNull ThemeEditorComponent themeEditorComponent) {
    super(ThemeEditorFixture.class, robot, themeEditorComponent);
    myThemesComboBox = new JComboBoxFixture(robot(), robot().finder()
      .findByName(this.target().getSecondComponent(), AttributesPanel.THEME_SELECTOR_NAME, JComboBox.class));
  }

  @NotNull
  public JComboBoxFixture getThemesComboBox() {
    return myThemesComboBox;
  }

  @NotNull
  public JComboBoxFixture getModulesComboBox() {
    myModulesComboBox = new JComboBoxFixture(robot(), robot().finder()
      .findByName(this.target().getSecondComponent(), AttributesPanel.MODULE_SELECTOR_NAME, JComboBox.class));
    return myModulesComboBox;
  }

  @NotNull
  public ThemeEditorTableFixture getPropertiesTable() {
    return ThemeEditorTableFixture.find(robot());
  }

  @NotNull
  public SearchTextFieldFixture getSearchTextField() {
    return new SearchTextFieldFixture(robot(), robot().finder().findByType(this.target().getFirstComponent(), SearchTextField.class));
  }

  @NotNull
  public List<String> getThemesList() {
    return ImmutableList.copyOf(myThemesComboBox.contents());
  }

  @NotNull
  public List<String> getModulesList() {
    if (myModulesComboBox == null) {
      getModulesComboBox();
    }
    //noinspection ConstantConditions: getModulesComboBox either sets myModulesComboBox to a non-null value or throws an exception
    return ImmutableList.copyOf(myModulesComboBox.contents());
  }

  public void waitForThemeSelection(@NotNull final String themeName) {
    Wait.seconds(5).expecting(themeName + " to be selected").until(() -> themeName.equals(myThemesComboBox.selectedItem()));
  }

  @NotNull
  public ActionButtonFixture findToolbarButton(@NotNull final String tooltip) {
    return new ActionButtonFixture(robot(), waitUntilFound(robot(), Matchers.byTooltip(ActionButton.class, tooltip)));
  }

  @NotNull
  public ThemePreviewComponentFixture getPreviewComponent() {
    return new ThemePreviewComponentFixture(robot(), robot().finder()
      .findByType(target(), ThemePreviewComponent.class));
  }

  public static void clickPopupMenuItem(@NotNull String labelPrefix,
                                        @NotNull final String expectedLabel,
                                        @NotNull final ActionButton button,
                                        @NotNull org.fest.swing.core.Robot robot) {
    GuiTests.clickPopupMenuItem(labelPrefix, button, robot);
    Wait.seconds(1)
      .expecting("UI update")
      .until(() -> expectedLabel.equals(TextAccessors.getTextAccessor(button).getText()));
  }

  public HyperlinkLabelFixture getThemeWarningLabel() {
    // we wait here as the label only shows up when the IDE goes out of dumb mode.
    HyperlinkLabel label = waitUntilShowingAndEnabled(robot(), target(), Matchers.byType(HyperlinkLabel.class));
    return new HyperlinkLabelFixture(robot(), label);
  }
}
