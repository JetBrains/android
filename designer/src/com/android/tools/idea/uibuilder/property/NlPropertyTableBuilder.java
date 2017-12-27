/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.property;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.property.editors.NlPropertyEditors;
import com.android.tools.adtui.ptable.PTable;
import com.android.tools.adtui.ptable.PTableItem;
import com.android.tools.idea.uibuilder.property.renderer.NlPropertyRenderers;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class NlPropertyTableBuilder {
  private final Project myProject;
  private final PTable myTable;
  private final List<NlComponent> myComponents;
  private final List<NlPropertyItem> myProperties;

  NlPropertyTableBuilder(@NotNull Project project, @NotNull PTable table,
                         @NotNull List<NlComponent> components, @NotNull List<NlPropertyItem> properties) {
    myProject = project;
    myTable = table;
    myComponents = components;
    myProperties = properties;
  }

  public boolean build() {
    List<PTableItem> groupedProperties;
    if (myComponents.isEmpty()) {
      groupedProperties = Collections.emptyList();
    }
    else {
      List<NlPropertyItem> sortedProperties = new NlPropertiesSorter().sort(myProperties, myComponents);
      groupedProperties = new NlPropertiesGrouper().group(sortedProperties, myComponents);
    }
    if (myTable.isEditing()) {
      myTable.removeEditor();
    }

    int selectedRow = myTable.getSelectedRow();
    PTableItem selectedItem = myTable.getSelectedItem();

    myTable.getModel().setItems(groupedProperties);
    myTable.setRendererProvider(NlPropertyRenderers.getInstance());
    myTable.setEditorProvider(NlPropertyEditors.getInstance(myProject));
    if (myTable.getRowCount() > 0) {
      myTable.restoreSelection(selectedRow, selectedItem);
    }
    return !groupedProperties.isEmpty();
  }
}
