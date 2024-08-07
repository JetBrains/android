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
package com.google.idea.blaze.base.dependencies;

import com.google.idea.blaze.base.settings.Blaze;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.VerticalLayout;
import java.util.List;
import javax.annotation.Nullable;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;

/** A dialog box to pick a directory + target to add to a project to cover a given source file. */
class AddSourceToProjectDialog extends DialogWrapper {

  private final Project project;

  private final JPanel mainPanel;

  private final JList<TargetInfo> targetsComponent;

  AddSourceToProjectDialog(Project project, List<TargetInfo> targets) {
    super(project, /* canBeParent= */ true, IdeModalityType.MODELESS);
    this.project = project;

    mainPanel = new JPanel(new VerticalLayout(12));

    JList<TargetInfo> targetsComponent = new JBList<>(targets);
    if (targets.size() == 1) {
      targetsComponent.setSelectedIndex(0);
    }
    this.targetsComponent = targetsComponent;

    setTitle("Add Source File to Project");
    setupUi();
    init();
  }

  private void setupUi() {
    JPanel panel = new JPanel(new VerticalLayout(12));
    panel.setBorder(
        IdeBorderFactory.createTitledBorder(
            String.format("Add %s target(s) to project", Blaze.buildSystemName(project)), false));
    panel.add(targetsComponent);
    mainPanel.add(panel);
  }

  List<TargetInfo> getSelectedTargets() {
    return targetsComponent.getSelectedValuesList();
  }

  @Nullable
  @Override
  protected ValidationInfo doValidate() {
    List<TargetInfo> targets = getSelectedTargets();
    if (targets.isEmpty()) {
      return new ValidationInfo("Choose a target building this source.");
    }
    return null;
  }

  @Override
  protected JComponent createCenterPanel() {
    return mainPanel;
  }
}
