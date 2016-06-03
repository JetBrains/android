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
package com.android.tools.idea.monitor.tool;

import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.DevicePanel;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class AndroidMonitorToolWindow implements Disposable {
  @NotNull
  private final Project myProject;

  private final JPanel myComponent;

  public AndroidMonitorToolWindow(@NotNull final Project project) {
    myProject = project;



    DeviceContext deviceContext = new DeviceContext();
    DevicePanel devicePanel = new DevicePanel(project, deviceContext);

    myComponent = new JPanel(new BorderLayout());
    myComponent.add(devicePanel.getComponent(), BorderLayout.NORTH);
// TODO myComponent.add(..., BorderLayout.CENTER);
  }

  @Override
  public void dispose() {

  }

  public JComponent getComponent() {
    return myComponent;
  }
}