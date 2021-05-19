/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.tools.idea.run.deployment;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

// TODO Reduce the visibility of SelectMultipleDevicesAction and ID
public final class SelectMultipleDevicesAction extends AnAction {
  public static final String ID = "SelectMultipleDevices";

  private SelectMultipleDevicesAction() {
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    DeviceAndSnapshotComboBoxAction.getInstance().selectMultipleDevices(Objects.requireNonNull(event.getProject()));
  }
}
