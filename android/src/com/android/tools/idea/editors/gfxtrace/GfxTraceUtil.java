/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.editors.gfxtrace;

import com.android.tools.idea.editors.gfxtrace.actions.GfxTraceCaptureAction;
import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;
import com.android.tools.idea.sdk.wizard.SdkQuickfixUtils;
import com.android.tools.idea.wizard.model.ModelWizardDialog;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.WindowManager;

import javax.swing.*;
import java.awt.*;
import java.util.Collection;

public class GfxTraceUtil {
  /**
   * Checks if there is a valid Gapi installed, and if not try and install it.
   * @return true if a valid Gapi is installed, false otherwise.
   */
  public static boolean checkAndTryInstallGapidSdkComponent(Project project) {
    if (!GapiPaths.isValid()) {
      Window window = WindowManager.getInstance().suggestParentWindow(project);
      int result = JOptionPane.showConfirmDialog(window, "GPU Tools are not installed, install now?", "GPU Tools Missing", JOptionPane.OK_CANCEL_OPTION);
      if (result != JOptionPane.OK_OPTION) {
        // user clicked cancel
        return false;
      }

      final Collection<String> missingComponents = GapiPaths.getMissingSdkComponents();
      if (missingComponents.isEmpty()) {
        Logger.getInstance(GfxTraceCaptureAction.class).warn("no valid package to install found");
        return false;
      }
      ModelWizardDialog dialog = SdkQuickfixUtils.createDialogForPaths(project, missingComponents);
      if (dialog == null) {
        Logger.getInstance(GfxTraceCaptureAction.class).warn("this is strange, we got no dialog back from createDialogForPaths");
        return false;
      }
      dialog.setTitle("Install Missing Components");
      if (!dialog.showAndGet()) {
        // user cancelled or install did not happen
        return false;
      }
    }
    return true;
  }
}
