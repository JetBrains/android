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
package com.android.tools.idea.welcome.wizard;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;

public final class ConfirmFirstRunWizardCloseDialog {
  public enum Result {
    DoNotClose, Rerun, Skip
  }

  public static Result show() {
    Result[] result = {Result.DoNotClose};
    Messages.showCheckboxMessageDialog(
      "You are about to exit the Setup Wizard, which helps you\n" +
      "configure necessary components for Android development.",
      "Exit Setup Wizard",
      new String[]{Messages.getOkButton(), Messages.getCancelButton()},
      "Show the wizard on next startup (Recommended)",
      true, 1, 1, Messages.getWarningIcon(),
      (exitCode, cb) -> {
        if (exitCode == DialogWrapper.OK_EXIT_CODE) {
          result[0] = cb.isSelected() ? Result.Rerun : Result.Skip;
        }
        return 0;
      }
    );
    return result[0];
  }
}
