/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.monitor;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction;
import org.jetbrains.annotations.NotNull;

class MonitorMoveAction extends AnAction implements RightAlignedToolbarAction {
  @NotNull private MonitorPanel myMonitorPanel;
  @NotNull private BaseMonitorView myMonitor;
  private int myDirection;

  MonitorMoveAction(@NotNull MonitorPanel monitorPanel, @NotNull BaseMonitorView monitor, int direction) {
    super(String.format("Move %s Monitor %s",
                        monitor.getTitleName(), direction < 0 ? "Up" : "Down"),
          String.format("Switches the %s Monitor with the %s monitor.",
                        monitor.getTitleName(), direction < 0 ? "previous" : "next"),
          direction < 0 ? AllIcons.Actions.PreviousOccurence : AllIcons.Actions.NextOccurence);
    myMonitorPanel = monitorPanel;
    myMonitor = monitor;
    myDirection = direction;
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    switch (myDirection) {
      case -1:
        myMonitorPanel.moveMonitorUp(myMonitor);
        break;
      case 1:
        myMonitorPanel.moveMonitorDown(myMonitor);
        break;
    }
    update(e);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation()
      .setEnabled(myMonitorPanel.canMove(myMonitor, myDirection));
  }
}
