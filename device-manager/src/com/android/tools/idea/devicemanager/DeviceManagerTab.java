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
package com.android.tools.idea.devicemanager;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import org.jetbrains.annotations.NotNull;

public interface DeviceManagerTab {
  @NotNull
  ExtensionPointName<DeviceManagerTab> EP_NAME = new ExtensionPointName<>("org.jetbrains.android.deviceManagerTab");

  default boolean isApplicable() {
    return true;
  }

  @NotNull
  String getName();

  @NotNull
  DevicePanel getPanel(@NotNull Project project, @NotNull Disposable parentDisposable);

  @NotNull
  default JComponent getErrorComponent(@NotNull Throwable throwable) {
    JPanel errorPanel = new JPanel(new BorderLayout());
    JLabel errorLabel = new JLabel(throwable.getMessage());
    errorLabel.setHorizontalAlignment(SwingConstants.CENTER);
    errorPanel.add(errorLabel, BorderLayout.CENTER);
    return errorPanel;
  }

  default void setRecreateCallback(@NotNull Runnable r, @NotNull Disposable disposable) {}
}
