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
package com.android.tools.idea.logcat;

import com.android.tools.idea.ddms.DeviceContext;
import com.android.tools.idea.ddms.DevicePanel;
import com.intellij.execution.ui.RunnerLayoutUi;
import com.intellij.execution.ui.layout.PlaceInGrid;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBLoadingPanel;
import com.intellij.ui.content.Content;
import icons.StudioIcons.Shell.ToolWindows;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

public final class LogcatPanel extends JBLoadingPanel {
  private final DevicePanel myDevicePanel;
  private final AndroidLogcatView myLogcatView;

  public LogcatPanel(@NotNull Project project) {
    super(new BorderLayout(), project);

    DeviceContext context = new DeviceContext();
    myDevicePanel = new DevicePanel(project, context);
    myLogcatView = new AndroidLogcatView(project, context, "Logcat");

    add(new DeviceAndSearchPanel(myDevicePanel, myLogcatView), BorderLayout.NORTH);
    add(createCenterComponent(project), BorderLayout.CENTER);
  }

  @NotNull
  private Component createCenterComponent(@NotNull Project project) {
    RunnerLayoutUi ui = RunnerLayoutUi.Factory.getInstance(project).create("Android", "Logcat", "Logcat", project);

    Content content = ui.createContent("Android Logcat", myLogcatView.getContentPanel(), "logcat", ToolWindows.LOGCAT, null);
    content.setCloseable(false);
    content.setDisposer(myLogcatView);
    content.putUserData(AndroidLogcatView.ANDROID_LOGCAT_VIEW_KEY, myLogcatView);

    ui.addContent(content, 0, PlaceInGrid.center, false);
    return ui.getComponent();
  }

  @NotNull
  public DevicePanel getDevicePanel() {
    return myDevicePanel;
  }

  @NotNull
  public AndroidLogcatView getLogcatView() {
    return myLogcatView;
  }
}
