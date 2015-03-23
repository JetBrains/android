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
import com.android.tools.idea.editors.theme.EditedStyleItem;
import com.android.tools.idea.rendering.ResourceHelper;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import org.jetbrains.annotations.NotNull;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * {@link ColorPalette.ColorPaletteModel} that gets the colors list from an {@link AttributesTableModel}.
 */
public class AttributesModelColorPaletteModel implements ColorPalette.ColorPaletteModel, TableModelListener {
  private final ResourceResolver myResourceResolver;
  private final AttributesTableModel myModel;

  private List<Color> myColorList;

  public AttributesModelColorPaletteModel(@NotNull Configuration configuration, @NotNull AttributesTableModel model) {
    myResourceResolver = configuration.getResourceResolver();
    myModel = model;
    myModel.addTableModelListener(this);

    loadColors();
  }

  @Override
  public int getCount() {
    return myColorList.size();
  }

  @Override
  public Color getColorAt(int i) {
    return myColorList.get(i);
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
      colorSet.addAll(ResourceHelper.resolveMultipleColors(myResourceResolver, item.getItemResourceValue()));
    }

    myColorList = ImmutableList.copyOf(Multisets.copyHighestCountFirst(colorSet).elementSet());
  }

  @Override
  public void tableChanged(TableModelEvent e) {
    loadColors();
  }
}
