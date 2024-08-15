/*
 * Copyright 2019 The Bazel Authors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.idea.blaze.android.run.state;

import com.google.idea.blaze.base.run.state.RunConfigurationState;
import com.google.idea.blaze.base.run.state.RunConfigurationStateEditor;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import org.jdom.Element;

/** State for android debugger settings. */
public class DebuggerSettingsState implements RunConfigurationState {
  private static final String NATIVE_DEBUG_ATTR = "blaze-native-debug";

  private boolean nativeDebuggingEnabled;

  public DebuggerSettingsState(boolean nativeDebuggingEnabled) {
    this.nativeDebuggingEnabled = nativeDebuggingEnabled;
  }

  public boolean isNativeDebuggingEnabled() {
    return nativeDebuggingEnabled;
  }

  public void setNativeDebuggingEnabled(boolean enabled) {
    nativeDebuggingEnabled = enabled;
  }

  @Override
  public void readExternal(Element element) {
    setNativeDebuggingEnabled(Boolean.parseBoolean(element.getAttributeValue(NATIVE_DEBUG_ATTR)));
  }

  @Override
  public void writeExternal(Element element) {
    element.setAttribute(NATIVE_DEBUG_ATTR, Boolean.toString(nativeDebuggingEnabled));
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new DebuggerSettingsStateEditor();
  }

  /** Component for editing user editable debugger options. */
  public static class DebuggerSettingsStateEditor implements RunConfigurationStateEditor {
    private final JCheckBox enableNativeDebuggingCheckBox;

    DebuggerSettingsStateEditor() {
      enableNativeDebuggingCheckBox = new JCheckBox("Enable native debugging", false);
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      DebuggerSettingsState state = (DebuggerSettingsState) genericState;
      enableNativeDebuggingCheckBox.setSelected(state.isNativeDebuggingEnabled());
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      DebuggerSettingsState state = (DebuggerSettingsState) genericState;
      state.setNativeDebuggingEnabled(enableNativeDebuggingCheckBox.isSelected());
    }

    @Override
    public JComponent createComponent() {
      return UiUtil.createBox(enableNativeDebuggingCheckBox);
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      enableNativeDebuggingCheckBox.setEnabled(enabled);
    }
  }
}
