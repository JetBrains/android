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
package com.android.tools.idea.run.editor;

import com.intellij.openapi.project.Project;

import javax.swing.*;

import static com.android.tools.idea.run.editor.ProfilerState.EXPERIMENTAL_PROFILING_FLAG_ENABLED;

/**
 * The configuration panel for the Android profiler settings.
 */
public class AndroidProfilersPanel {
  private JPanel myPanel;
  private JCheckBox myAdvancedProfilingCheckBox;

  public JComponent getComponent() {
    return myPanel;
  }

  AndroidProfilersPanel(Project project, ProfilerState state) {
    resetFrom(state);
  }

  /**
   * Resets the settings panel to the values in the specified {@link ProfilerState}.
   */
  void resetFrom(ProfilerState state) {
    myAdvancedProfilingCheckBox.setSelected(state.ADVANCED_PROFILING_ENABLED);
  }

  /**
   * Assigns the current UI state to the specified {@link ProfilerState}.
   */
  void applyTo(ProfilerState state) {
    state.ADVANCED_PROFILING_ENABLED = EXPERIMENTAL_PROFILING_FLAG_ENABLED && myAdvancedProfilingCheckBox.isSelected();
  }
}
