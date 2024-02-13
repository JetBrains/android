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
package com.android.tools.idea.actions.license;

import com.android.tools.idea.sdk.AndroidSdkPathStore;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBDimension;
import java.awt.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Surfaces a UI action for displaying a dialog of licenses for 3rd-party libraries
 */
public class ShowLicensesUsedAction extends DumbAwareAction {

  public ShowLicensesUsedAction() {
    super("_Licenses");
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = getEventProject(e);

    new Task.Backgroundable(project, "Collecting licenses", true) {
      public String myLicenseText;

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        indicator.setIndeterminate(true);

        Path ideHome = Paths.get(PathManager.getHomePath());
        LicensesLocator locator = new LicensesLocator(ideHome, SystemInfo.isMac);
        CompletableFuture<String> cf =
          new LicenseTextCollector(ideHome, locator.getLicenseFiles()).getLicenseText();
        // TODO: b/295135492  Remove the special case for emulator once we decide to add all
        // the licenses in Android Sdk.
        CompletableFuture<String> emuCf = null;
        Path sdkPath = AndroidSdkPathStore.getInstance().getAndroidSdkPath();
        if (sdkPath != null) {
          Path emulatorPath = sdkPath.resolve("emulator");
          if (emulatorPath != null) {
            //`isMacLayout` is false because special treatment is not needed for emulator's
            // file layout.
            LicensesLocator emulatorLocator = new LicensesLocator(emulatorPath, false);
            emuCf = new LicenseTextCollector(emulatorPath, emulatorLocator.getLicenseFiles())
                        .getLicenseText();
          }
        }

        while (!indicator.isCanceled()) {
          try {
            StringBuilder sb = new StringBuilder();
            sb.append(cf.get(100, TimeUnit.MILLISECONDS));
            if (emuCf != null) {
              sb.append(emuCf.get(100, TimeUnit.MILLISECONDS));
            }
            myLicenseText = sb.toString();
            return;
          }
          catch (InterruptedException e) {
            return;
          }
          catch (ExecutionException e) {
            throw new RuntimeException(e.getCause());
          }
          catch (TimeoutException ignored) {
          }
        }
      }

      @Override
      public void onSuccess() {
        LicenseDialog licenseDialog = new LicenseDialog(project, myLicenseText);
        licenseDialog.init();
        try {
          licenseDialog.show();
        } catch (Exception ex) {
          Logger.getInstance(ShowLicensesUsedAction.class).error(ex);
        }
      }

      @Override
      public void onThrowable(@NotNull Throwable error) {
        Messages.showErrorDialog(project, "Error collecting licenses: " + error, "Show Licenses");
      }
    }.queue();
  }

  private static class LicenseDialog extends DialogWrapper {
    private final String myLicenseText;

    protected LicenseDialog(@Nullable Project project, @NotNull String licenseText) {
      super(project);
      myLicenseText = licenseText;
    }

    @Override
    protected void init() {
      super.init();
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
      JPanel panel = new JPanel(new BorderLayout());

      JTextArea textArea = new JTextArea();
      textArea.setText(myLicenseText);
      textArea.setEditable(false);
      textArea.setCaretPosition(0);

      JBScrollPane pane = new JBScrollPane(textArea);
      pane.setMinimumSize(new JBDimension(600, 400));
      pane.setPreferredSize(new JBDimension(600, 400));

      panel.add(pane, BorderLayout.CENTER);
      return panel;
    }
  }
}
