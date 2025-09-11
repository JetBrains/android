/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.compose.debug;

import com.android.tools.compose.ComposeBundle;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.util.text.TextWithMnemonic;
import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.intellij.uiDesigner.core.Spacer;
import java.awt.Insets;
import javax.swing.AbstractButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

public class ComposeDebuggerSettingsUi implements ConfigurableUi<ComposeDebuggerSettings> {
  private JPanel myPanel;
  private JCheckBox filterComposeInternalClasses;

  public ComposeDebuggerSettingsUi() {
    setupUI();
  }

  @Override
  public void reset(@NotNull ComposeDebuggerSettings settings) {
    filterComposeInternalClasses.setSelected(settings.getFilterComposeRuntimeClasses());
  }

  @Override
  public boolean isModified(@NotNull ComposeDebuggerSettings settings) {
    return filterComposeInternalClasses.isSelected() != settings.getFilterComposeRuntimeClasses();
  }

  @Override
  public void apply(@NotNull ComposeDebuggerSettings settings) {
    settings.setFilterComposeRuntimeClasses(filterComposeInternalClasses.isSelected());
  }

  @Override
  public @NotNull JComponent getComponent() {
    return myPanel;
  }

  private void setupUI() {
    myPanel = new JPanel();
    myPanel.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
    filterComposeInternalClasses = new JCheckBox();
    loadButtonText(filterComposeInternalClasses, ComposeBundle.message("filter.ignore.compose.runtime.classes"));
    myPanel.add(filterComposeInternalClasses, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE,
                                                                  GridConstraints.SIZEPOLICY_CAN_SHRINK |
                                                                  GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED,
                                                                  null, null, null, 0, false));
    final Spacer spacer1 = new Spacer();
    myPanel.add(spacer1, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_VERTICAL, 1,
                                             GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
  }

  private void loadButtonText(AbstractButton component, String text) {
    TextWithMnemonic textWithMnemonic = TextWithMnemonic.parse(text);
    component.setText(text);
    if (textWithMnemonic.hasMnemonic()) {
      component.setMnemonic(textWithMnemonic.getMnemonicCode());
      component.setDisplayedMnemonicIndex(textWithMnemonic.getMnemonicIndex());
    }
  }
}
