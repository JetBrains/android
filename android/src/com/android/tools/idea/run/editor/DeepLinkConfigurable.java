/*
 * Copyright (C) 2015 The Android Open Source Project
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

import com.intellij.execution.ExecutionBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBTextField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class DeepLinkConfigurable implements LaunchOptionConfigurable<DeepLinkLaunch.State> {
  private JPanel myPanel;
  private ComponentWithBrowseButton<JBTextField> myDeepLinkField;

  public DeepLinkConfigurable(@NotNull final Project project, @NotNull final LaunchOptionConfigurableContext context) {
    myDeepLinkField.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (!project.isInitialized()) {
          return;
        }
        Module module = context.getModule();
        if (module == null) {
          Messages.showErrorDialog(project, ExecutionBundle.message("module.not.specified.error.text"), "Deep Link Launcher");
          return;
        }
        DeepLinkChooserDialog dialog = new DeepLinkChooserDialog(project, module);
        dialog.setTitle("Select URL");
        dialog.show();

        String deepLinkSelected = dialog.getSelectedDeepLink();
        if (deepLinkSelected != null && !deepLinkSelected.isEmpty()) {
          myDeepLinkField.getChildComponent().setText(deepLinkSelected);
        }
      }
    });
    myDeepLinkField.getChildComponent().getEmptyText().setText("Specify URL declared in the manifest");
  }

  private void createUIComponents() {
    myDeepLinkField = new ComponentWithBrowseButton<JBTextField>(new JBTextField(), null);
  }

  @Nullable
  @Override
  public JComponent createComponent() {
    return myPanel;
  }

  @Override
  public void resetFrom(@NotNull DeepLinkLaunch.State state) {
    myDeepLinkField.getChildComponent().setText(StringUtil.notNullize(state.DEEP_LINK));
  }

  @Override
  public void applyTo(@NotNull DeepLinkLaunch.State state) {
    state.DEEP_LINK = StringUtil.notNullize(myDeepLinkField.getChildComponent().getText());
  }
}
