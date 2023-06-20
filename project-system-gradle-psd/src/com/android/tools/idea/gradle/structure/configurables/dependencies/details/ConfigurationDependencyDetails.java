/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.tools.idea.gradle.structure.configurables.dependencies.details;

import static com.android.tools.idea.gradle.structure.dependencies.QuickSearchComboBoxKt.createQuickSearchComboBox;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.ui.EditorComboBox;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;

interface ConfigurationDependencyDetails extends DependencyDetails {
  JPanel getConfigurationUI();

  PsContext getContext();

  default void displayConfiguration(@NotNull PsDeclaredDependency dependency, @NotNull PsModule.ImportantFor importantFor) {
    if (dependency != getModel()) {
      JPanel panel = getConfigurationUI();
      for (Component c : panel.getComponents()) {
        panel.remove(c);
      }

      final EditorComboBox ui = createQuickSearchComboBox(
        getContext().getProject().getIdeProject(),
        dependency.getParent().getConfigurations(null),
        dependency.getParent().getConfigurations(importantFor)
      );
      ui.setName("configuration");
      ui.setSelectedItem(dependency.getConfigurationName());
      panel.add(ui);
      dependency.getParent().add(new PsModule.DependenciesChangeListener() {
        @Override
        public void dependencyChanged(@NotNull PsModule.DependencyChangedEvent event) {
          if (event instanceof PsModule.DependencyModifiedEvent) {
            PsDeclaredDependency eventDependency = ((PsModule.DependencyModifiedEvent)event).getDependency().getValue();
            if (dependency.equals(eventDependency)) {
              ui.setSelectedItem(dependency.getConfigurationName());
            }
          }
        }
      }, getContext());
      ui.addActionListener(new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent e) {
          modifyConfiguration();
        }
      });
    }
  }

  default void modifyConfiguration() {
    PsDeclaredDependency dependency = (PsDeclaredDependency)getModel();
    EditorComboBox ui = (EditorComboBox)getConfigurationUI().getComponent(0);
    String configuration = ui.getText();
    if (dependency != null &&
        configuration != null &&
        !configuration.equals(dependency.getConfigurationName())) {
      PsModule module = dependency.getParent();
      assert module.getDependencies().getItems().contains(dependency);
      module.modifyDependencyConfiguration(dependency, configuration);
    }
  }
}
