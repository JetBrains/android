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
package com.android.tools.idea.gradle.structure.dependencies.android;

import static com.android.tools.idea.gradle.structure.dependencies.android.Configuration.ANDROID_TEST;
import static com.android.tools.idea.gradle.structure.dependencies.android.Configuration.MAIN;
import static com.android.tools.idea.gradle.structure.dependencies.android.Configuration.TEST_FIXTURES;
import static com.android.tools.idea.gradle.structure.dependencies.android.Configuration.UNIT_TEST;
import static com.intellij.ui.SideBorder.BOTTOM;
import static com.intellij.ui.SideBorder.LEFT;
import static com.intellij.ui.SideBorder.RIGHT;

import com.android.tools.idea.gradle.structure.configurables.ui.PsCheckBoxList;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class ConfigurationsPanel extends JPanel {
  @NotNull private final List<Configuration> mySelectedConfigurations;
  @NotNull private final PsCheckBoxList<Configuration> myConfigurationsList;
  @NotNull private final SelectionChangeEventDispatcher<List<Configuration>> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  ConfigurationsPanel() {
    super(new BorderLayout());

    List<Configuration> configurations = Lists.newArrayList(MAIN, ANDROID_TEST, UNIT_TEST, TEST_FIXTURES);

    myConfigurationsList = new PsCheckBoxList<>(configurations);

    // Select "main" by default.
    mySelectedConfigurations = Lists.newArrayList(MAIN);
    for (Configuration configuration : configurations) {
      myConfigurationsList.setItemSelected(configuration, true);
    }
    myConfigurationsList.addCheckBoxListListener((index, value) -> {
      Configuration configuration = myConfigurationsList.getItemAt(index);
      if (configuration != null) {
        if (configuration == MAIN) {
          if (value) {
            myConfigurationsList.setItemSelected(ANDROID_TEST, true);
            myConfigurationsList.setItemSelected(TEST_FIXTURES, true);
            myConfigurationsList.setItemSelected(UNIT_TEST, true);
          }
        }
        else {
          if (!value) {
            myConfigurationsList.setItemSelected(MAIN, false);
          }
        }
      }
      updateSelection();
    });

    myConfigurationsList.setSelectionChangeListener(newSelection -> updateSelection());

    ListSpeedSearch.installOn(myConfigurationsList);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myConfigurationsList);
    scrollPane.setBorder(IdeBorderFactory.createBorder(LEFT | RIGHT | BOTTOM));
    add(scrollPane, BorderLayout.CENTER);
  }

  private void updateSelection() {
    mySelectedConfigurations.clear();
    if (myConfigurationsList.isItemSelected(MAIN)) {
      mySelectedConfigurations.add(MAIN);
    }
    else {
      if (myConfigurationsList.isItemSelected(ANDROID_TEST)) {
        mySelectedConfigurations.add(ANDROID_TEST);
      }
      if (myConfigurationsList.isItemSelected(TEST_FIXTURES)) {
        mySelectedConfigurations.add(TEST_FIXTURES);
      }
      if (myConfigurationsList.isItemSelected(UNIT_TEST)) {
        mySelectedConfigurations.add(UNIT_TEST);
      }
    }
    myEventDispatcher.selectionChanged(mySelectedConfigurations);
  }

  void add(@NotNull SelectionChangeListener<List<Configuration>> listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  @Nullable
  JComponent getPreferredFocusedComponent() {
    return myConfigurationsList;
  }

  @NotNull
  List<Configuration> getSelectedConfigurations() {
    return mySelectedConfigurations;
  }
}
