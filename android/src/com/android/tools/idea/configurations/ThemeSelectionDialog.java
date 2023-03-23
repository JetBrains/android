/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.DoubleClickListener;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.Set;


public class ThemeSelectionDialog extends DialogWrapper {
  @NotNull private final ThemeSelectionPanel myPanel;

  public ThemeSelectionDialog(@NotNull Configuration configuration) {
    this(configuration, Collections.<String>emptySet());
  }

  public ThemeSelectionDialog(@NotNull Configuration configuration, @NotNull Set<String> excludedThemes) {
    super(configuration.getConfigModule().getProject());
    myPanel = new ThemeSelectionPanel(this, configuration, excludedThemes);
    setTitle("Select Theme");
    init();
  }

  public void setThemeChangedListener(@NotNull ThemeSelectionPanel.ThemeChangedListener themeChangedListener) {
    myPanel.setThemeChangedListener(themeChangedListener);
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JPanel contentPanel = myPanel.getContentPanel();
    contentPanel.setPreferredSize(JBUI.size(800, 500));
    myPanel.installDoubleClickListener(new DoubleClickListener() {
      @Override
      protected boolean onDoubleClick(MouseEvent event) {
        close(OK_EXIT_CODE);
        return true;
      }
    });
    return contentPanel;
  }

  @Override
  protected void doOKAction() {
    super.doOKAction();
  }

  @Override
  protected String getDimensionServiceKey() {
    return "AndroidThemeDialog";
  }

  @Nullable
  public String getTheme() {
    return myPanel.getTheme();
  }

  public void checkValidation() {
    initValidation();
  }

  @Override
  @Nullable
  protected ValidationInfo doValidate() {
    String theme = myPanel.getTheme();
    if (theme == null) {
      return new ValidationInfo("Select a theme");
    }
    return null;
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myPanel.getPreferredFocusedComponent();
  }
}
