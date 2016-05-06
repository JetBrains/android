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

import com.android.tools.idea.editors.gfxtrace.gapi.GapiPaths;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import static com.android.tools.idea.startup.GradleSpecificInitializer.ENABLE_EXPERIMENTAL_PROFILING;

/**
 * The configuration panel for the Android profiler settings.
 */
public class AndroidProfilersPanel {
  private static final boolean EXPERIMENTAL_ENABLED = System.getProperty(ENABLE_EXPERIMENTAL_PROFILING) != null;

  private JPanel myPanel;
  private JCheckBox myAdvancedProfilingCheckBox;

  private JCheckBox myGapidEnabled;
  private JCheckBox myGapidDisablePCS;

  public JComponent getComponent() {
    return myPanel;
  }

  AndroidProfilersPanel(ProfilerState state) {
    myAdvancedProfilingCheckBox.setVisible(EXPERIMENTAL_ENABLED);
    myGapidEnabled.setVisible(EXPERIMENTAL_ENABLED);

    ChangeListener checkboxChangeListener = new ChangeListener() {
      @Override
      public void stateChanged(ChangeEvent e) {
        boolean enabled = !EXPERIMENTAL_ENABLED || myAdvancedProfilingCheckBox.isSelected();
        myGapidEnabled.setEnabled(enabled && isGapidSdkComponentInstalled());

        boolean gapidEnabled = !EXPERIMENTAL_ENABLED || (enabled && isGapidSdkComponentInstalled() && myGapidEnabled.isSelected());
        myGapidDisablePCS.setEnabled(gapidEnabled);
      }
    };

    myAdvancedProfilingCheckBox.addChangeListener(checkboxChangeListener);
    myGapidEnabled.addChangeListener(checkboxChangeListener);

    resetFrom(state);
  }

  private static boolean isGapidSdkComponentInstalled() {
    return !EXPERIMENTAL_ENABLED || GapiPaths.findTracerAar().exists();
  }

  /**
   * Resets the settings panel to the values in the specified {@link ProfilerState}.
   */
  void resetFrom(ProfilerState state) {
    myAdvancedProfilingCheckBox.setSelected(state.ENABLE_ADVANCED_PROFILING);
    myGapidEnabled.setSelected(state.GAPID_ENABLED);
    myGapidDisablePCS.setSelected(state.GAPID_DISABLE_PCS);

    if (isGapidSdkComponentInstalled()) {
      myGapidEnabled.setToolTipText(null);
    }
    else {
      myGapidEnabled.setEnabled(false);
      myGapidDisablePCS.setEnabled(false);
      myGapidEnabled.setToolTipText("GPU debugger tools not installed or out of date.");
    }
  }

  /**
   * Assigns the current UI state to the specified {@link ProfilerState}.
   */
  void applyTo(ProfilerState state) {

    boolean enabled = System.getProperty(ENABLE_EXPERIMENTAL_PROFILING) != null;
    state.ENABLE_ADVANCED_PROFILING = myAdvancedProfilingCheckBox.isSelected() && enabled;
    state.GAPID_ENABLED = myGapidEnabled.isSelected();
    state.GAPID_DISABLE_PCS = myGapidDisablePCS.isSelected();
  }
}
