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

import android.annotation.NonNull;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.List;

class ConfigurationsForm {
  private JPanel myMainPanel;
  private JCheckBox myMainCheckBox;
  private JCheckBox myAndroidTestCheckBox;
  private JCheckBox myUnitTestCheckBox;

  @NotNull private final List<Configuration> mySelectedConfiguration = Lists.newArrayList();
  @NotNull private final SelectionChangeEventDispatcher<List<Configuration>> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  ConfigurationsForm() {
    myMainCheckBox.addChangeListener(e -> {
      JCheckBox checkBox = (JCheckBox)e.getSource();
      if (checkBox.isSelected()) {
        myAndroidTestCheckBox.setSelected(true);
        myUnitTestCheckBox.setSelected(true);
      }
      updateSelection();
      myEventDispatcher.selectionChanged(mySelectedConfiguration);
    });

    ChangeListener testChangeListener = e -> {
      JCheckBox checkBox = (JCheckBox)e.getSource();
      if (!checkBox.isSelected()) {
        myMainCheckBox.setSelected(false);
      }
      updateSelection();
      myEventDispatcher.selectionChanged(mySelectedConfiguration);
    };
    myAndroidTestCheckBox.addChangeListener(testChangeListener);
    myUnitTestCheckBox.addChangeListener(testChangeListener);

    updateSelection();
  }

  private void updateSelection() {
    mySelectedConfiguration.clear();
    if (myMainCheckBox.isSelected()) {
      mySelectedConfiguration.add(Configuration.MAIN);
    }
    else {
      if (myAndroidTestCheckBox.isSelected()) {
        mySelectedConfiguration.add(Configuration.ANDROID_TEST);
      }
      if (myUnitTestCheckBox.isSelected()) {
        mySelectedConfiguration.add(Configuration.UNIT_TEST);
      }
    }
  }

  @NonNull
  JPanel getPanel() {
    return myMainPanel;
  }

  @NotNull
  List<Configuration> getSelectedConfigurations() {
    return mySelectedConfiguration;
  }

  void add(@NotNull SelectionChangeListener<List<Configuration>> listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myMainCheckBox;
  }

  enum Configuration {
    MAIN, ANDROID_TEST, UNIT_TEST
  }
}
