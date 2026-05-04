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
package com.android.tools.profilers;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

/**
 * View shown if no processes are selected
 */
public class NullMonitorStageView extends StageView<NullMonitorStage> {
  private static final String ANDROID_PROFILER_TITLE = "Android Profiler";


  public NullMonitorStageView(@NotNull StudioProfilersView profilersView, @NotNull NullMonitorStage stage) {
    super(profilersView, stage);

    ProfilerEmptyStateView messageView = new ProfilerEmptyStateView(ANDROID_PROFILER_TITLE, stage.getUnsupportedReason());
    getComponent().add(messageView, BorderLayout.CENTER);
  }

  @Override
  public JComponent getToolbar() {
    return new JPanel();
  }

  @Override
  public boolean needsProcessSelection() {
    return true;
  }
}
