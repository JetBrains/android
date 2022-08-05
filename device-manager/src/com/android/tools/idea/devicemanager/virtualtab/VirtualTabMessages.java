/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.devicemanager.virtualtab;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class VirtualTabMessages {
  private VirtualTabMessages() {
  }

  static void showErrorDialog(@NotNull Throwable throwable, @Nullable Project project) {
    String title = throwable instanceof ErrorDialogException ? ((ErrorDialogException)throwable).getTitle() : "Device Manager";
    String message = throwable.getMessage();

    if (message == null) {
      message = "There was an unspecified error in the device manager. Please consult idea.log for more information.";
    }

    Messages.showErrorDialog(project, message, title);
  }
}
