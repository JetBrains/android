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
import com.android.tools.idea.gradle.structure.model.android.PsAndroidModule;
import com.android.tools.idea.gradle.structure.model.android.PsBuildType;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.ui.CheckBoxList;
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
  @NotNull private final PsBuildType myFakeAllBuildType;
  @NotNull private final List<PsBuildType> myBuildTypes;
  @NotNull private final List<PsBuildType> mySelectedBuildTypes;
  @NotNull private final CheckBoxList<PsBuildType> myBuildTypesList;
  @NotNull private final SelectionChangeEventDispatcher<List<PsBuildType>> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  BuildTypesPanel(@NotNull PsAndroidModule module, @NotNull List<PsBuildType> buildTypes) {
    super(new BorderLayout());

    myFakeAllBuildType = new PsBuildType(module, null, null) {
      @Override
      @NotNull
      public String getName() {
        return "<All>";
      }
    };

    myBuildTypes = buildTypes;

    myBuildTypesList = new CheckBoxList<>();
    List<PsBuildType> items = Lists.newArrayList(buildTypes);
    items.add(0, myFakeAllBuildType);
    myBuildTypesList.setItems(items, null);

    // Select "<All"> build types
    myBuildTypesList.setItemSelected(myFakeAllBuildType, true);
    mySelectedBuildTypes = Lists.newArrayList(myBuildTypes);

    myBuildTypesList.setCheckBoxListListener((index, value) -> {
      mySelectedBuildTypes.clear();
      PsBuildType buildType = myBuildTypesList.getItemAt(index);
      if (buildType != null) {
        if (value) {
          if (buildType == myFakeAllBuildType) {
            mySelectedBuildTypes.addAll(myBuildTypes);
          }
          else {
            mySelectedBuildTypes.add(buildType);
          }
        }

        // We force only one selected item, as if the list contained radio buttons instead of check boxes.
        int itemsCount = myBuildTypesList.getItemsCount();
        for (int i = 0; i < itemsCount; i++) {
          if (i != index) {
            PsBuildType item = myBuildTypesList.getItemAt(i);
            if (item != null) {
              myBuildTypesList.setItemSelected(item, false);
            }
          }
        }

        myEventDispatcher.selectionChanged(mySelectedBuildTypes);
      }
    });

    new ListSpeedSearch(myBuildTypesList);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myBuildTypesList);
    scrollPane.setBorder(IdeBorderFactory.createBorder(RIGHT | BOTTOM));
    add(scrollPane, BorderLayout.CENTER);
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
    return myBuildTypesList;
  }
}
