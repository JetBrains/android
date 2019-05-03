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

import com.android.tools.idea.gradle.structure.model.PsDeclaredDependency;
import com.android.tools.idea.gradle.structure.model.PsModule;
import com.intellij.openapi.ui.ComboBox;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JComboBox;
import org.jetbrains.annotations.NotNull;

interface ConfigurationDependencyDetails extends DependencyDetails {
  JComboBox<String> getConfigurationUI();

  default JComboBox<String> createConfigurationUI() {
    JComboBox<String> ui = new ComboBox<>();
    ui.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        modifyConfiguration();
      }
    });
    return ui;
  }

  default void displayConfiguration(@NotNull PsDeclaredDependency dependency, @NotNull PsModule.ImportantFor importantFor) {
    if (dependency != getModel()) {
      if (getModel() != null) {
        modifyConfiguration();
      }
      JComboBox<String> ui = getConfigurationUI();
      ActionListener[] listeners = ui.getActionListeners();
      try {
        for (ActionListener l : listeners) {
          ui.removeActionListener(l);
        }
        ui.removeAllItems();
        String configuration = dependency.getJoinedConfigurationNames();
        ui.addItem(configuration);
        for (String c : dependency.getParent().getConfigurations(PsModule.ImportantFor.LIBRARY)) {
          if (c != configuration) ui.addItem(c);
        }
        ui.setSelectedItem(configuration);
      } finally {
        for (ActionListener l : listeners) {
          ui.addActionListener(l);
        }
      }
    }
  }

  default void modifyConfiguration() {
    PsDeclaredDependency dependency = (PsDeclaredDependency) getModel();
    String configuration = (String) getConfigurationUI().getEditor().getItem();
    if (dependency != null && configuration != null) {
        PsModule module = dependency.getParent();
        module.modifyDependencyConfiguration(dependency, configuration);
    }
  }
}
