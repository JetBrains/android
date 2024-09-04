/*
 * Copyright 2017 The Bazel Authors. All rights reserved.
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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.ui.PortField;
import com.intellij.util.ui.FormBuilder;
import javax.swing.JComponent;
import org.jdom.Element;

/** User-defined debug port. */
public class DebugPortState implements RunConfigurationState {

  private static final String ATTRIBUTE_TAG = "debug_port";

  private final int defaultPort;

  public int port;

  public DebugPortState(int defaultPort) {
    this.defaultPort = defaultPort;
    port = defaultPort;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    String value = element.getAttributeValue(ATTRIBUTE_TAG);
    if (value == null) {
      return;
    }
    try {
      port = Integer.parseInt(value);
    } catch (NumberFormatException e) {
      // ignore
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    if (port != defaultPort) {
      element.setAttribute(ATTRIBUTE_TAG, Integer.toString(port));
    } else {
      element.removeAttribute(ATTRIBUTE_TAG);
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new Editor();
  }

  private class Editor implements RunConfigurationStateEditor {
    private final PortField portField = new PortField(defaultPort);

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      DebugPortState state = (DebugPortState) genericState;
      portField.setNumber(state.port);
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      DebugPortState state = (DebugPortState) genericState;
      state.port = portField.getNumber();
    }

    @Override
    public JComponent createComponent() {
      return FormBuilder.createFormBuilder().addLabeledComponent("&Port:", portField).getPanel();
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      portField.setEnabled(enabled);
    }
  }
}
