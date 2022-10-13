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
package com.android.tools.idea.device.explorer.files.actions;

import com.android.tools.idea.file.explorer.toolwindow.DeviceExplorerController;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

public class GotoSharedPrefsFolderAction extends DumbAwareAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    DeviceExplorerController controller = DeviceExplorerController.getProjectController(e.getProject());
    e.getPresentation().setEnabled(controller != null && controller.hasActiveDevice());
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
  }
}
