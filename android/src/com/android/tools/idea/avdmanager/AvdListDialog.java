/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.avdmanager;

import com.android.sdklib.internal.avd.AvdInfo;
import com.android.tools.idea.wizard.WizardConstants;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * Display existing AVDs and offer actions for editing/creating.
 */
public class AvdListDialog extends DialogWrapper implements AvdUiAction.AvdInfoProvider {
  private AvdDisplayList myAvdDisplayList;

  public AvdListDialog(@Nullable Project project) {
    super(project);
    myAvdDisplayList = new AvdDisplayList();
    setTitle("AVD Manager");
    Window window = getWindow();
    if (window == null) {
      assert ApplicationManager.getApplication().isUnitTestMode();
    } else {
      window.setPreferredSize(WizardConstants.DEFAULT_WIZARD_WINDOW_SIZE);
    }
  }

  @Nullable
  @Override
  protected JComponent createNorthPanel() {
    JPanel titlePanel = new JPanel(new BorderLayout());
    JBLabel label = new JBLabel("Your Virtual Devices");
    Font font = label.getFont();
    if (font == null) {
      font = UIUtil.getLabelFont();
    }
    font = new Font(font.getName(), font.getStyle() | Font.BOLD, font.getSize() + 4);
    label.setFont(font);
    label.setBorder(IdeBorderFactory.createEmptyBorder(18, 12, 12, 12));
    titlePanel.add(label, BorderLayout.WEST);

    return titlePanel;
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    return myAvdDisplayList;
  }

  @Nullable
  @Override
  public AvdInfo getAvdInfo() {
    return null;
  }

  @Override
  public void refreshAvds() {
    myAvdDisplayList.refreshAvds();
  }

  public static class LaunchMe extends AnAction {
    public LaunchMe() {
      super("Launch AVD List");
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      AvdListDialog dialog = new AvdListDialog(null);
      dialog.init();
      dialog.show();
    }
  }
}
