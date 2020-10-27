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

import com.android.tools.idea.gradle.structure.configurables.ui.PsCheckBoxList;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeEventDispatcher;
import com.android.tools.idea.gradle.structure.configurables.ui.SelectionChangeListener;
import com.android.tools.idea.gradle.structure.model.android.PsBuildType;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.ui.SideBorder.BOTTOM;
import static com.intellij.ui.SideBorder.RIGHT;

class BuildTypesPanel extends JPanel {
  @NotNull private final PsCheckBoxList<PsBuildType> myBuildTypesList;
  @NotNull private final SelectionChangeEventDispatcher<List<PsBuildType>> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  BuildTypesPanel(@NotNull List<PsBuildType> buildTypes) {
    super(new BorderLayout());

    List<PsBuildType> items = Lists.newArrayList(buildTypes);
    myBuildTypesList = new PsCheckBoxList<>(items);

    // Select all build types
    myBuildTypesList.setSelectionChangeListener(myEventDispatcher::selectionChanged);
    myBuildTypesList.setItemsSelected(true);

    new ListSpeedSearch(myBuildTypesList);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myBuildTypesList);
    scrollPane.setBorder(IdeBorderFactory.createBorder(RIGHT | BOTTOM));
    add(scrollPane, BorderLayout.CENTER);
  }

  @NotNull
  List<PsBuildType> getSelectedBuildTypes() {
    return myBuildTypesList.getSelectedItems();
  }

  void add(@NotNull SelectionChangeListener<List<PsBuildType>> listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  @NotNull
  JComponent getPreferredFocusedComponent() {
    return myBuildTypesList;
  }
}
