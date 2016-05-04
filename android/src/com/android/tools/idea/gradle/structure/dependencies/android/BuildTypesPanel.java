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

import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.android.tools.idea.gradle.structure.model.android.PsBuildType;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.List;

class BuildTypesPanel extends JPanel {
  @NonNls private static final String BUILD_TYPE_PROPERTY = "buildType";

  @NotNull private final JRadioButton myAllRadioButton;
  @NotNull private final List<PsBuildType> myAllBuildTypes;

  @NotNull private final List<PsBuildType> mySelectedBuildTypes = Lists.newArrayList();
  @NotNull private final List<JRadioButton> myRadioButtons = Lists.newArrayList();
  @NotNull private final SelectionChangeEventDispatcher<List<PsBuildType>> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  BuildTypesPanel(@NotNull List<PsBuildType> buildTypes) {
    myAllBuildTypes = buildTypes;
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

    ChangeListener changeListener = e -> {
      updateSelection();
      myEventDispatcher.selectionChanged(mySelectedBuildTypes);
    };
    ButtonGroup group = new ButtonGroup();

    myAllRadioButton = new JRadioButton("<All>");
    myAllRadioButton.setSelected(true);
    myAllRadioButton.addChangeListener(changeListener);
    add(myAllRadioButton);
    group.add(myAllRadioButton);

    for (PsBuildType buildType : buildTypes) {
      JRadioButton radioButton = new JRadioButton(buildType.getName());
      radioButton.putClientProperty(BUILD_TYPE_PROPERTY, buildType);
      radioButton.addChangeListener(changeListener);
      myRadioButtons.add(radioButton);
      add(radioButton);
      group.add(radioButton);
    }

    updateSelection();
  }

  private void updateSelection() {
    mySelectedBuildTypes.clear();
    if (myAllRadioButton.isSelected()) {
      mySelectedBuildTypes.addAll(myAllBuildTypes);
      return;
    }
    myRadioButtons.stream().filter(AbstractButton::isSelected).forEach(radioButton -> {
      Object value = radioButton.getClientProperty(BUILD_TYPE_PROPERTY);
      if (value instanceof PsBuildType) {
        mySelectedBuildTypes.add((PsBuildType)value);
      }
    });
  }

  @NotNull
  List<PsBuildType> getSelectedBuildTypes() {
    return mySelectedBuildTypes;
  }

  void add(@NotNull SelectionChangeListener<List<PsBuildType>> listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  @NotNull
  JComponent getPreferredFocusedComponent() {
    return myAllRadioButton;
  }
}
