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
import com.google.idea.blaze.base.command.BlazeCommandName;
import com.google.idea.blaze.base.settings.Blaze;
import com.google.idea.blaze.base.ui.UiUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import javax.annotation.Nullable;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import org.jdom.Element;

/** State for a {@link BlazeCommandName}. */
public final class BlazeCommandState implements RunConfigurationState {
  private static final String COMMAND_ATTR = "blaze-command";

  @Nullable private BlazeCommandName command;

  public BlazeCommandState() {}

  @Nullable
  public BlazeCommandName getCommand() {
    return command;
  }

  public void setCommand(@Nullable BlazeCommandName command) {
    this.command = command;
  }

  @Override
  public void readExternal(Element element) {
    String commandString = element.getAttributeValue(COMMAND_ATTR);
    command =
        Strings.isNullOrEmpty(commandString) ? null : BlazeCommandName.fromString(commandString);
  }

  @Override
  public void writeExternal(Element element) {
    if (command != null) {
      element.setAttribute(COMMAND_ATTR, command.toString());
    } else {
      element.removeAttribute(COMMAND_ATTR);
    }
  }

  @Override
  public RunConfigurationStateEditor getEditor(Project project) {
    return new BlazeCommandStateEditor(project);
  }

  private static class BlazeCommandStateEditor implements RunConfigurationStateEditor {
    private final String buildSystemName;

    private final ComboBox<?> commandCombo;

    BlazeCommandStateEditor(Project project) {
      buildSystemName = Blaze.buildSystemName(project);
      commandCombo =
          new ComboBox<>(new DefaultComboBoxModel<>(BlazeCommandName.knownCommands().toArray()));
      // Allow the user to manually specify an unlisted command.
      commandCombo.setEditable(true);
    }

    @Override
    public void setComponentEnabled(boolean enabled) {
      commandCombo.setEnabled(enabled);
    }

    @Override
    public void resetEditorFrom(RunConfigurationState genericState) {
      BlazeCommandState state = (BlazeCommandState) genericState;
      commandCombo.setSelectedItem(state.getCommand());
    }

    @Override
    public void applyEditorTo(RunConfigurationState genericState) {
      BlazeCommandState state = (BlazeCommandState) genericState;
      Object selectedCommand = commandCombo.getSelectedItem();
      if (selectedCommand instanceof BlazeCommandName) {
        state.setCommand((BlazeCommandName) selectedCommand);
      } else {
        state.setCommand(
            Strings.isNullOrEmpty((String) selectedCommand)
                ? null
                : BlazeCommandName.fromString(selectedCommand.toString()));
      }
    }

    @Override
    public JComponent createComponent() {
      return UiUtil.createBox(new JLabel(buildSystemName + " command:"), commandCombo);
    }
  }
}
