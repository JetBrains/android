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
package com.android.tools.idea.gradle.structure.dependencies.java;

import com.android.tools.idea.gradle.structure.configurables.ui.PsCheckBoxList;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule;
import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListSpeedSearch;
import icons.AndroidIcons;
import org.jdesktop.swingx.JXLabel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.List;

import static com.intellij.openapi.util.text.StringUtil.isEmpty;
import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.ui.SideBorder.*;
import static com.intellij.util.ui.UIUtil.getTextFieldBackground;
import static com.intellij.util.ui.UIUtil.getTextFieldBorder;

class MainForm implements Disposable {
  private JPanel myContentsPanel;
  private JPanel myMainPanel;
  private JXLabel myScopesLabel;

  @NotNull private final ToolWindowPanel myToolWindowPanel;
  @NotNull private final PsCheckBoxList<String> myConfigurationsList;

  MainForm(@NotNull PsJavaModule module) {
    myScopesLabel.setBorder(BorderFactory.createCompoundBorder(getTextFieldBorder(), IdeBorderFactory.createEmptyBorder(2)));
    myScopesLabel.setBackground(getTextFieldBackground());
    myScopesLabel.setText(" ");

    List<String> configurations = Lists.newArrayList(module.getGradleModel().getConfigurations());
    Collections.sort(configurations, (s1, s2) -> {
      // compile goes first.
      if ("compile".equals(s1)) {
        return -1;
      }
      else if ("compile".endsWith(s2)) {
        return 1;
      }
      return s1.compareTo(s2);
    });

    String selected = null;
    if (!configurations.isEmpty()) {
      selected = configurations.get(0);
    }

    myConfigurationsList = new PsCheckBoxList<>(configurations);

    if (selected != null) {
      myConfigurationsList.setItemSelected(selected, true);
      updateLabel(getSelectedScopeNames());
    }

    myConfigurationsList.setSelectionChangeListener(this::updateLabel);

    new ListSpeedSearch(myConfigurationsList);

    myToolWindowPanel = new ToolWindowPanel("Configurations", AndroidIcons.Android, null) {
    };
    ToolWindowHeader header = myToolWindowPanel.getHeader();
    header.setPreferredFocusedComponent(myConfigurationsList);
    header.setAdditionalActions(myConfigurationsList.createSelectAllAction(), myConfigurationsList.createUnselectAllAction());
    header.setBorder(IdeBorderFactory.createBorder(LEFT | TOP | RIGHT));

    JScrollPane scrollPane = createScrollPane(myConfigurationsList);
    scrollPane.setBorder(IdeBorderFactory.createBorder(LEFT | BOTTOM | RIGHT));

    myToolWindowPanel.add(scrollPane, BorderLayout.CENTER);
    myContentsPanel.add(myToolWindowPanel, BorderLayout.CENTER);
  }

  private void updateLabel(@NotNull List<String> newSelection) {
    String selectedScopes = Joiner.on(", ").skipNulls().join(getSelectedScopeNames());
    if (isEmpty(selectedScopes)) {
      selectedScopes = " ";
    }
    myScopesLabel.setText(selectedScopes);
  }

  @NotNull
  JPanel getPanel() {
    return myMainPanel;
  }

  @Nullable
  ValidationInfo validateInput() {
    if (getSelectedScopeNames().isEmpty()) {
      return new ValidationInfo("Please select at least one configuration", myConfigurationsList);
    }
    return null;
  }

  @NotNull
  List<String> getSelectedScopeNames() {
    return myConfigurationsList.getSelectedItems();
  }

  @Override
  public void dispose() {
    Disposer.dispose(myToolWindowPanel);
  }
}
