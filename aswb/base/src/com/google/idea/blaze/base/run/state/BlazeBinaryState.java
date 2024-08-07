/*
 * Copyright 2016 The Bazel Authors. All rights reserved.
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
package com.google.idea.blaze.base.run.state;

import com.google.common.base.Strings;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.components.JBTextField;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jdom.Element;

/** State for a Blaze binary to run configurations with. */
public final class BlazeBinaryState implements RunConfigurationState {
  private static final String BLAZE_BINARY_ATTR = "blaze-binary";

  @Nullable private String blazeBinary;

  public BlazeBinaryState() {}

  @Nullable
  public String getBlazeBinary() {
    return blazeBinary;
  }

  public void setBlazeBinary(@Nullable String blazeBinary) {
    this.blazeBinary = blazeBinary;
  }

  @Override
  public void readExternal(Element element) {
    blazeBinary = element.getAttributeValue(BLAZE_BINARY_ATTR);
  }

  @Override
  public void writeExternal(Element element) {
    if (!Strings.isNullOrEmpty(blazeBinary)) {
      element.setAttribute(BLAZE_BINARY_ATTR, blazeBinary);
    } else {
      element.removeAttribute(BLAZE_BINARY_ATTR);
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new BlazeBinaryStateEditor(project);
  }

  private static class BlazeBinaryStateEditor implements RunConfigurationStateEditor {
    private final String buildSystemName;

    private final JBTextField blazeBinaryField = new JBTextField(1);

    BlazeBinaryStateEditor(Project project) {
      buildSystemName = Blaze.buildSystemName(project);
      blazeBinaryField.getEmptyText().setText("(Use global)");
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      blazeBinaryField.setEnabled(enabled);
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      BlazeBinaryState state = (BlazeBinaryState) genericState;
      blazeBinaryField.setText(Strings.nullToEmpty(state.getBlazeBinary()));
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      BlazeBinaryState state = (BlazeBinaryState) genericState;
      state.setBlazeBinary(Strings.emptyToNull(blazeBinaryField.getText()));
    }

    @Override
    public JComponent createComponent() {
      return UiUtil.createBox(new JLabel(buildSystemName + " binary:"), blazeBinaryField);
    }
  }
}
