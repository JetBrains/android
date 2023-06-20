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

import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.serviceContainer.NonInjectable;
import java.util.Collection;
import java.util.Objects;
import java.util.function.Function;
import org.jetbrains.annotations.NotNull;

final class SelectMultipleDevicesAction extends AnAction {
  static final String ID = "SelectMultipleDevices";
  private final @NotNull Function<Project, AsyncDevicesGetter> myAsyncDevicesGetterGetInstance;

  @SuppressWarnings("unused")
  private SelectMultipleDevicesAction() {
    this(AsyncDevicesGetter::getInstance);
  }

  @VisibleForTesting
  @NonInjectable
  SelectMultipleDevicesAction(@NotNull Function<Project, AsyncDevicesGetter> asyncDevicesGetterGetInstance) {
    myAsyncDevicesGetterGetInstance = asyncDevicesGetterGetInstance;
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    Project project = event.getProject();
    Presentation presentation = event.getPresentation();

    if (project == null) {
      presentation.setEnabledAndVisible(false);
      return;
    }

    boolean empty = myAsyncDevicesGetterGetInstance.apply(project).get().map(Collection::isEmpty).orElse(true);
    presentation.setEnabledAndVisible(!empty);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent event) {
    DeviceAndSnapshotComboBoxAction.getInstance().selectMultipleDevices(Objects.requireNonNull(event.getProject()));
  }
}
