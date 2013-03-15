/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.configurations;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Presentation;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class ActivityMenuAction extends FlatComboAction {
  private final ConfigurationToolBar myConfigurationToolBar;

  public ActivityMenuAction(ConfigurationToolBar configurationToolBar) {
    myConfigurationToolBar = configurationToolBar;
    Presentation presentation = getTemplatePresentation();
    presentation.setIcon(AllIcons.Nodes.Class);
    updatePresentation(presentation);
  }

  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    updatePresentation(e.getPresentation());
  }

  private void updatePresentation(Presentation presentation) {
    Configuration configuration = myConfigurationToolBar.getConfiguration();
    boolean visible = configuration != null;
    if (visible) {
      String activity = configuration.getActivity();
      if (activity != null) {
        presentation.setText(activity);
      }
    }
    if (visible != presentation.isVisible()) {
      presentation.setVisible(visible);
    }
  }

  @Override
  @NotNull
  protected DefaultActionGroup createPopupActionGroup(JComponent button) {
    return new DefaultActionGroup(null, true);
  }
}
