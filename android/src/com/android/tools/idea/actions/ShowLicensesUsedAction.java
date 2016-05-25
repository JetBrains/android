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

import com.android.tools.idea.npw.WizardUtils;
import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PathUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Surfaces a UI action for displaying a dialog of licenses for 3rd-party libraries
 */
public class ShowLicensesUsedAction extends DumbAwareAction {

  public ShowLicensesUsedAction() {
    super("_Licenses");
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
      getWindow().setMinimumSize(JBUI.size(600, 400));
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
      collectLicenses(sb, licenseDir);

      List<String> extraPlugins = Arrays.asList(
        "android",
        "google-appindexing",
        "google-cloud-testing",
        "google-cloud-tools",
        "google-cloud-tools-core",
        "google-login",
        "google-services",
        "firebase"
      );

      for (String plugin : extraPlugins) {
        File licenses = new File(PathManager.getPreInstalledPluginsPath(), PathUtil.toSystemDependentName(plugin + "/lib/licenses"));
        if (licenses.isDirectory()) {
          collectLicenses(sb, licenses);
        }
      }

      String text = "<html>" + sb.toString() + "</html>";
      JTextPane label = new JTextPane();
      label.setContentType(UIUtil.HTML_MIME);
      label.setText(text);
      JBScrollPane pane = new JBScrollPane(label);

      pane.setPreferredSize(JBUI.size(600, 400));
      panel.add(pane, BorderLayout.CENTER);
      return panel;
    }

    private static void collectLicenses(@NotNull StringBuilder sb, @NotNull File licenseDir) {
      for (File file : WizardUtils.listFiles(licenseDir)) {
        sb.append("<br><br>------------ License file: ");
        sb.append(file.getName());
        sb.append("------------");
        sb.append("<br><br>");
        sb.append(getLicenseText(file));
      }
    }

    @NotNull
    private static String getLicenseText(@NotNull File f) {
      try {
        return Files.toString(f, Charsets.UTF_8).replaceAll("\\<.*?\\>", "").replace("\n", "<br>");
      }
      catch (IOException e) {
        return "";
      }
    }
  }
}
