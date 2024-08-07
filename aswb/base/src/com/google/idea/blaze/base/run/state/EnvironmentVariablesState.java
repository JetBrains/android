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

import com.google.common.collect.ImmutableMap;
import com.intellij.execution.configuration.EnvironmentVariablesComponent;
import com.intellij.execution.configuration.EnvironmentVariablesData;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import javax.swing.JComponent;
import org.jdom.Element;

/** State for user-defined environment variables. */
public class EnvironmentVariablesState implements RunConfigurationState {

  private static final String ELEMENT_TAG = "env_state";

  private EnvironmentVariablesData data =
      EnvironmentVariablesData.create(ImmutableMap.of(), /* passParentEnvs= */ true);

  public EnvironmentVariablesData getData() {
    return data;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    Element child = element.getChild(ELEMENT_TAG);
    if (child != null) {
      data = EnvironmentVariablesData.readExternal(child);
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    element.removeChildren(ELEMENT_TAG);
    Element child = new Element(ELEMENT_TAG);
    data.writeExternal(child);
    element.addContent(child);
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new Editor();
  }

  private static class Editor implements RunConfigurationStateEditor {
    private final EnvironmentVariablesComponent component = new EnvironmentVariablesComponent();

    private Editor() {
      component.setText("&Environment variables (only set when debugging)");
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      EnvironmentVariablesState state = (EnvironmentVariablesState) genericState;
      component.setEnvs(state.data.getEnvs());
      component.setPassParentEnvs(state.data.isPassParentEnvs());
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      EnvironmentVariablesState state = (EnvironmentVariablesState) genericState;
      state.data =
          EnvironmentVariablesData.create(component.getEnvs(), component.isPassParentEnvs());
    }

    @Override
    public JComponent createComponent() {
      return component;
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      component.setEnabled(enabled);
    }
  }
}
