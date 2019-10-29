/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.editors.theme.attributes;

import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.editors.theme.ColorPalette;
import com.android.tools.idea.editors.theme.datamodels.EditedStyleItem;
import com.android.tools.idea.res.ResourceHelper;
import com.google.common.collect.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.util.Collections;
import java.util.List;

/**
 * {@link ColorPalette.ColorPaletteModel} that gets the colors list from an {@link AttributesTableModel}.
 */
public class AttributesModelColorPaletteModel implements ColorPalette.ColorPaletteModel, TableModelListener {
  private final ResourceResolver myResourceResolver;
  private final AttributesTableModel myModel;
  private final Project myProject;

  private List<Color> myColorList;
  private final Multimap<Color, EditedStyleItem> myColorReferences = HashMultimap.create();

  public AttributesModelColorPaletteModel(@NotNull Configuration configuration, @NotNull AttributesTableModel model) {
    myResourceResolver = configuration.getResourceResolver();
    myProject = configuration.getModule().getProject();
    myModel = model;
    myModel.addTableModelListener(this);

    loadColors();
  }

  @Override
  public int getCount() {
    return myColorList.size();
  }

  @NotNull
  @Override
  public Color getColorAt(int i) {
    return myColorList.get(i);
  }

  @Override
  public int indexOf(@NotNull Color c) {
    return myColorList.indexOf(c);
  }

  @NotNull
  @Override
  public String getToolTipAt(int i) {
    StringBuilder tooltip = new StringBuilder("This color is used in:\n\n");
    for(EditedStyleItem item : myColorReferences.get(myColorList.get(i))) {
      tooltip.append(item.getAttrName()).append('\n');
    }
    return  tooltip.toString();
  }

  private void loadColors() {
    if (myResourceResolver == null) {
      myColorList = Collections.emptyList();
      return;
    }

    int rows = myModel.getRowCount();
    Multiset<Color> colorSet = HashMultiset.create();
    for (int i = 0; i < rows; i++) {
      if (myModel.getCellClass(i, 0) != Color.class) {
        continue;
      }

      EditedStyleItem item = (EditedStyleItem)myModel.getValueAt(i, 0);
      for (Color color : ResourceHelper.resolveMultipleColors(myResourceResolver, item.getSelectedValue(), myProject)) {
        myColorReferences.put(color, item);
        colorSet.add(color);
      }
    }

    myColorList = ImmutableList.copyOf(Multisets.copyHighestCountFirst(colorSet).elementSet());
  }

  @Override
  public void tableChanged(TableModelEvent e) {
    loadColors();
  }

  public List<EditedStyleItem> getReferences(Color color) {
    return ImmutableList.copyOf(myColorReferences.get(color));
  }
}
