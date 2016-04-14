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
package com.android.tools.idea.ui;

import com.intellij.ide.ui.laf.darcula.ui.DarculaTextBorder;
import com.intellij.ide.ui.laf.darcula.ui.DarculaTextFieldUI;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.SearchTextField;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.PanelUI;

/**
 * Nice looking search box, even on GTK+ theme, same idea as SettingsSearch.
 * @see com.intellij.openapi.options.newEditor.SettingsSearch
 */
public class SearchField extends SearchTextField {

  public SearchField(boolean historyEnabled) {
    super(historyEnabled);
    updateTheme();
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    JTextField editor = getTextEditor();
    editor.setEnabled(enabled);
    updateBackground();
  }

  @Override
  public void setUI(PanelUI ui) {
    super.setUI(ui);
    updateTheme();
  }

  private void updateTheme() {
    JTextField editor = getTextEditor();
    if (editor != null) {
      if (!SystemInfo.isMac) {
        editor.putClientProperty("JTextField.variant", "search");
        if (!(editor.getUI() instanceof DarculaTextFieldUI)) {
          editor.setUI((DarculaTextFieldUI)DarculaTextFieldUI.createUI(editor));
          editor.setBorder(new DarculaTextBorder());
        }
      }
      updateBackground();
    }
  }

  private void updateBackground() {
    getTextEditor().setBackground(isEnabled() ? UIUtil.getTextFieldBackground() : UIUtil.getPanelBackground());
  }

  @Override
  protected boolean isSearchControlUISupported() {
    return true;
  }
}
