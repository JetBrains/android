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
package com.android.tools.idea.actions;

import com.android.tools.idea.templates.TemplateUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;

/**
 * Surfaces a UI action for displaying a dialog of licenses for 3rd-party libraries
 */
public class ShowLicensesUsedAction extends AnAction {

  public ShowLicensesUsedAction() {
    super("Show Licenses...");
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    LicenseDialog licenseDialog = new LicenseDialog(getEventProject(e));
    licenseDialog.init();
    try {
      licenseDialog.show();
    } catch (Exception ex) {
      // Pass
    }
  }

  private static class LicenseDialog extends DialogWrapper {
    protected LicenseDialog(@Nullable Project project) {
      super(project);
      getWindow().setMinimumSize(new Dimension(600, 400));
    }

    @Override
    protected void init() {
      super.init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());
      StringBuilder sb = new StringBuilder(5000);
      File licenseDir = new File(PathManager.getHomePath(), "license");
      assert licenseDir.exists() : licenseDir;
      for (File file : TemplateUtils.listFiles(licenseDir)) {
        sb.append("<br><br>");
        String licenseText = null;
        try {
          licenseText = Files.toString(file, Charsets.UTF_8);
          licenseText = licenseText.replaceAll("\\<.*?\\>", "");
          sb.append(licenseText.replace("\n", "<br>"));
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }

      String text = "<html>" + sb.toString() + "</html>";
      JBScrollPane pane = new JBScrollPane(new JBLabel(text));
      pane.setPreferredSize(new Dimension(600, 400));
      panel.add(pane, BorderLayout.CENTER);
      return panel;
    }
  }
}
