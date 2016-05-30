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
import com.android.tools.idea.gradle.structure.model.android.PsProductFlavor;
import com.google.common.collect.Lists;
import com.intellij.openapi.Disposable;
import com.intellij.ui.CheckBoxList;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

import static com.intellij.ui.SideBorder.BOTTOM;
import static com.intellij.ui.SideBorder.RIGHT;

class ProductFlavorsPanel extends JPanel {
  @NotNull private final List<PsProductFlavor> mySelectedProductFlavors;
  @NotNull private final CheckBoxList<PsProductFlavor> myProductFlavorsList;
  @NotNull private final SelectionChangeEventDispatcher<List<PsProductFlavor>> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  ProductFlavorsPanel(@NotNull List<PsProductFlavor> productFlavors) {
    super(new BorderLayout());

    myProductFlavorsList = new CheckBoxList<>();
    myProductFlavorsList.setItems(productFlavors, null);

    // By default select all product flavors.
    for (PsProductFlavor productFlavor : productFlavors) {
      myProductFlavorsList.setItemSelected(productFlavor, true);
    }
    mySelectedProductFlavors = Lists.newArrayList(productFlavors);

    myProductFlavorsList.setCheckBoxListListener((index, value) -> {
      PsProductFlavor productFlavor = myProductFlavorsList.getItemAt(index);
      if (productFlavor != null) {
        updateSelection(productFlavor, value);
        myEventDispatcher.selectionChanged(mySelectedProductFlavors);
      }
    });

    new ListSpeedSearch(myProductFlavorsList);

    JScrollPane scrollPane = ScrollPaneFactory.createScrollPane(myProductFlavorsList);
    scrollPane.setBorder(IdeBorderFactory.createBorder(RIGHT | BOTTOM));
    add(scrollPane, BorderLayout.CENTER);
  }

  private void updateSelection(@NotNull PsProductFlavor productFlavor, boolean selected) {
    if (selected) {
      mySelectedProductFlavors.add(productFlavor);
      return;
    }
    mySelectedProductFlavors.remove(productFlavor);
  }

  @NotNull
  List<PsProductFlavor> getSelectedProductFlavors() {
    return mySelectedProductFlavors;
  }

  void add(@NotNull SelectionChangeListener<List<PsProductFlavor>> listener, @NotNull Disposable parentDisposable) {
    myEventDispatcher.addListener(listener, parentDisposable);
  }

  @Nullable
  public JComponent getPreferredFocusedComponent() {
    return myProductFlavorsList;
  }
}
