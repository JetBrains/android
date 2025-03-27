/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.profilers;

import com.android.tools.profilers.UiMessageHandler;
import com.intellij.openapi.ui.DoNotAskOption;
import com.intellij.openapi.ui.MessageDialogBuilder;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.OkCancelDialogBuilder;
import com.intellij.util.Consumer;
import javax.swing.Icon;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IntellijUiMessageHandler implements UiMessageHandler {
  private static final String DO_NOT_SHOW_TEXT = "Do not ask me again";

  @Override
  public void displayErrorMessage(@Nullable JComponent parent, @NotNull String title, @NotNull String message) {
    Messages.showErrorDialog(parent, message, title);
  }

  @Override
  public boolean displayOkCancelMessage(@NotNull String title,
                                        @NotNull String message,
                                        @NotNull String okText,
                                        @NotNull String cancelText,
                                        @Nullable Icon icon,
                                        @Nullable Consumer<Boolean> doNotShowSettingSaver) {
    OkCancelDialogBuilder dialog = MessageDialogBuilder.okCancel(title, message);
    if (doNotShowSettingSaver != null) {
      dialog = dialog.doNotAsk(new DoNotAskOption.Adapter() {
        @Override
        public void rememberChoice(boolean isSelected, int exitCode) {
          doNotShowSettingSaver.consume(isSelected);
        }

        @NotNull
        @Override
        public String getDoNotShowMessage() {
          return DO_NOT_SHOW_TEXT;
        }
      });
    }

    // guessWindowAndAsk() returns true if user selects "OK", false if "Cancel"
    return dialog.guessWindowAndAsk();
  }
}
