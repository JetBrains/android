/*
 * Copyright (C) 2018 The Android Open Source Project
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

import com.android.tools.idea.run.AndroidRunConfigurationBase;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ex.ConfigurableCardPanel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.CardLayoutPanel;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.TitledSeparator;
import com.intellij.ui.components.JBLabel;
import com.intellij.uiDesigner.core.GridConstraints;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import org.jetbrains.annotations.NotNull;

public final class DeploymentTargetOptions {
  @NotNull
  private final List<DeployTargetProvider> myProviders;

  @NotNull
  private final Map<String, DeployTargetConfigurableWrapper> myIdToConfigurableMap;

  @NotNull
  private final Component mySeparator;

  @NotNull
  private final Component myTargetLabel;

  @NotNull
  private final JComboBox<DeployTargetProvider> myTargetComboBox;

  @NotNull
  private final CardLayoutPanel<Configurable, Configurable, JComponent> myCardLayoutPanel;

  DeploymentTargetOptions(@NotNull List<DeployTargetProvider> providers,
                          @NotNull AndroidRunConfigurationEditor editor,
                          @NotNull Project project) {
    myProviders = providers;
    myIdToConfigurableMap = newIdToConfigurableMap(editor, project);

    mySeparator = new TitledSeparator("Deployment Target Options");
    myTargetLabel = new JBLabel("Target:");
    myTargetComboBox = newTargetComboBox();
    myCardLayoutPanel = new ConfigurableCardPanel();
  }

  @NotNull
  private Map<String, DeployTargetConfigurableWrapper> newIdToConfigurableMap(@NotNull AndroidRunConfigurationEditor editor,
                                                                              @NotNull Project project) {
    DeployTargetConfigurableContext context = new RunConfigurationEditorContext(editor.getModuleSelector(), editor.getModuleComboBox());

    return myProviders.stream().collect(Collectors.toMap(
      DeployTargetProvider::getId,
      provider -> new DeployTargetConfigurableWrapper(project, editor, context, provider)));
  }

  @NotNull
  private JComboBox<DeployTargetProvider> newTargetComboBox() {
    JComboBox<DeployTargetProvider> comboBox = new ComboBox<>(new CollectionComboBoxModel<>(myProviders));
    comboBox.setRenderer(new DeployTargetProvider.Renderer());

    comboBox.addActionListener(event -> {
      DeployTargetProvider provider = (DeployTargetProvider)comboBox.getSelectedItem();

      if (provider != null) {
        myCardLayoutPanel.select(myIdToConfigurableMap.get(provider.getId()), true);
      }
    });

    return comboBox;
  }

  void addTo(@NotNull Container container) {
    container.add(mySeparator, newSeparatorConstraints());
    container.add(myTargetLabel, newTargetLabelConstraints());
    container.add(myTargetComboBox, newTargetComboBoxConstraints());
    container.add(myCardLayoutPanel, newCardLayoutPanelConstraints());
  }

  @NotNull
  private static Object newSeparatorConstraints() {
    GridConstraints constraints = new GridConstraints();

    constraints.setRow(3);
    constraints.setColSpan(3);
    constraints.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    constraints.setFill(GridConstraints.FILL_HORIZONTAL);

    return constraints;
  }

  @NotNull
  private static Object newTargetLabelConstraints() {
    GridConstraints constraints = new GridConstraints();

    constraints.setRow(4);
    constraints.setVSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    constraints.setHSizePolicy(GridConstraints.SIZEPOLICY_FIXED);
    constraints.setFill(GridConstraints.FILL_HORIZONTAL);
    constraints.setIndent(2);

    return constraints;
  }

  @NotNull
  private static Object newTargetComboBoxConstraints() {
    GridConstraints constraints = new GridConstraints();

    constraints.setRow(4);
    constraints.setColumn(1);
    constraints.setFill(GridConstraints.FILL_HORIZONTAL);

    return constraints;
  }

  @NotNull
  private static Object newCardLayoutPanelConstraints() {
    GridConstraints constraints = new GridConstraints();

    constraints.setRow(5);
    constraints.setColSpan(3);
    constraints.setFill(GridConstraints.FILL_BOTH);
    constraints.setIndent(4);

    return constraints;
  }

  void resetFrom(@NotNull AndroidRunConfigurationBase configuration) {
    DeployTargetContext context = configuration.getDeployTargetContext();

    myTargetComboBox.setSelectedItem(context.getCurrentDeployTargetProvider());

    int id = configuration.hashCode();
    myProviders.forEach(provider -> myIdToConfigurableMap.get(provider.getId()).resetFrom(context.getDeployTargetState(provider), id));
  }

  void applyTo(@NotNull AndroidRunConfigurationBase configuration) {
    DeployTargetContext context = configuration.getDeployTargetContext();

    context.setTargetSelectionMode((DeployTargetProvider)Objects.requireNonNull(myTargetComboBox.getSelectedItem()));

    int id = configuration.hashCode();
    myProviders.forEach(provider -> myIdToConfigurableMap.get(provider.getId()).applyTo(context.getDeployTargetState(provider), id));
  }

  @NotNull
  @VisibleForTesting
  JComboBox getTargetComboBox() {
    return myTargetComboBox;
  }
}
