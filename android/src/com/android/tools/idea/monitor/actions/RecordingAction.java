/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.monitor.actions;

import com.android.tools.idea.monitor.BaseMonitorView;
import com.android.tools.idea.stats.UsageTracker;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.actionSystem.ToggleAction;
import org.jetbrains.annotations.NotNull;

public class RecordingAction extends ToggleAction {
  @NotNull private final BaseMonitorView myMonitorView;

  public RecordingAction(@NotNull BaseMonitorView monitorView) {
    // TODO Perhaps use a different icon? Something like "disabled" with an X?
    super(null, null, AllIcons.Actions.Pause);
    myMonitorView = monitorView;
  }

  @Override
  public boolean isSelected(AnActionEvent e) {
    return myMonitorView.getIsPaused();
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    super.update(e);
    Presentation presentation = e.getPresentation();
    if (isSelected(e)) {
      presentation.setText("Disabled");
      presentation.setDescription("Click to enable " + myMonitorView.getDescription() + " recording.");
    }
    else {
      presentation.setText("Enabled");
      presentation.setDescription("Click to disable " + myMonitorView.getDescription() + " recording.");
    }
  }

  @Override
  public void setSelected(AnActionEvent e, boolean state) {
    if (myMonitorView.getIsPaused() != state) {
      UsageTracker.getInstance()
        .trackEvent(UsageTracker.CATEGORY_PROFILING, UsageTracker.ACTION_MONITOR_RUNNING, myMonitorView.getDescription(), state ? 0 : 1);
    }
    myMonitorView.setIsPaused(state);
  }
}
