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

import com.google.common.collect.ImmutableList;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import java.util.List;
import java.util.stream.Collectors;
import javax.swing.JComponent;
import org.jdom.Element;

/** Helper class for managing composite state. */
public abstract class RunConfigurationCompositeState implements RunConfigurationState {

  private ImmutableList<RunConfigurationState> states;

  /**
   * Called once prior to (de)serializing the run configuration and/or setting up the UI. Returns
   * the {@link RunConfigurationState}s comprising this composite state. The order of the states
   * determines their position in the UI.
   */
  protected abstract ImmutableList<RunConfigurationState> initializeStates();

  /** The {@link RunConfigurationState}s comprising this composite state. */
  protected ImmutableList<RunConfigurationState> getStates() {
    if (states == null) {
      states = initializeStates();
    }
    return states;
  }

  @Override
  public final void readExternal(Element element) throws InvalidDataException {
    for (RunConfigurationState state : getStates()) {
      state.readExternal(element);
    }
  }

  /** Updates the element with the handler's state. */
  @Override
  @SuppressWarnings("ThrowsUncheckedException")
  public final void writeExternal(Element element) throws WriteExternalException {
    for (RunConfigurationState state : getStates()) {
      state.writeExternal(element);
    }
  }

  /** @return A {@link RunConfigurationStateEditor} for this state. */
  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new RunConfigurationCompositeStateEditor(project, getStates());
  }

  static class RunConfigurationCompositeStateEditor implements RunConfigurationStateEditor {
    List<RunConfigurationStateEditor> editors;

    public RunConfigurationCompositeStateEditor(
        Project project, List<RunConfigurationState> states) {
      editors = states.stream().map(state -> state.getEditor(project)).collect(Collectors.toList());
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      RunConfigurationCompositeState state = (RunConfigurationCompositeState) genericState;
      for (int i = 0; i < editors.size(); ++i) {
        editors.get(i).resetEditorFrom(state.getStates().get(i));
      }
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      RunConfigurationCompositeState state = (RunConfigurationCompositeState) genericState;
      for (int i = 0; i < editors.size(); ++i) {
        editors.get(i).applyEditorTo(state.getStates().get(i));
      }
    }

    @Override
    public JComponent createComponent() {
      return UiUtil.createBox(
          editors
              .stream()
              .map(RunConfigurationStateEditor::createComponent)
              .collect(Collectors.toList()));
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      editors.forEach(editor -> editor.setComponentEnabled(enabled));
    }
  }
}
