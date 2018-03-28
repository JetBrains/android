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

import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowHeader;
import com.android.tools.idea.gradle.structure.configurables.ui.ToolWindowPanel;
import com.android.tools.idea.gradle.structure.dependencies.AbstractDependencyScopesPanel;
import com.android.tools.idea.gradle.structure.model.java.PsJavaModule;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.components.JBList;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static com.intellij.ui.SideBorder.*;

public class JavaDependencyScopesPanel extends AbstractDependencyScopesPanel {
  @NotNull private final ToolWindowPanel myToolWindowPanel;
  @NotNull private final JBList<String> myConfigurationsList;

  private JPanel myContentsPanel;
  private JPanel myMainPanel;

  public JavaDependencyScopesPanel(@NotNull PsJavaModule module) {
    List<String> configurations = Lists.newArrayList(module.getConfigurations());

    String selected = null;
    if (!configurations.isEmpty()) {
      selected = configurations.get(0);
    }

    myConfigurationsList = new JBList<>(configurations);

    if (selected != null) {
      myConfigurationsList.setSelectedValue(selected, true);
    }

    new ListSpeedSearch(myConfigurationsList);

    myToolWindowPanel = new ToolWindowPanel("Configurations", AndroidIcons.Android, null) {
    };
    ToolWindowHeader header = myToolWindowPanel.getHeader();
    header.setPreferredFocusedComponent(myConfigurationsList);
    header.setBorder(IdeBorderFactory.createBorder(LEFT | TOP | RIGHT));

    JScrollPane scrollPane = createScrollPane(myConfigurationsList);
    scrollPane.setBorder(IdeBorderFactory.createBorder(LEFT | BOTTOM | RIGHT));

    myToolWindowPanel.add(scrollPane, BorderLayout.CENTER);
    myContentsPanel.add(myToolWindowPanel, BorderLayout.CENTER);

    setUpContents(myMainPanel, getInstructions());
  }

  @NotNull
  private static String getInstructions() {
    return "Assign a scope to the new dependency by selecting the configurations below.<br/><a " +
           "href='https://docs.gradle.org/current/userguide/artifact_dependencies_tutorial.html'>Open Documentation</a>";
  }

  @Override
  @Nullable
  public ValidationInfo validateInput() {
    if (getSelectedScopeNames().isEmpty()) {
      return new ValidationInfo("Please select at least one configuration", myConfigurationsList);
    }
    return null;
  }

  @Override
  @NotNull
  public List<String> getSelectedScopeNames() {
    List<String> scopes = ImmutableList.of(myConfigurationsList.getSelectedValue());
    return scopes;
  }

  @Override
  public void dispose() {
    Disposer.dispose(myToolWindowPanel);
  }
}
