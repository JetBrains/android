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
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeListener;
import java.util.List;

class ProductFlavorsPanel extends JPanel {
  @NonNls private static final String PRODUCT_FLAVOR_PROPERTY = "productFlavor";

  @NotNull private final List<PsProductFlavor> mySelectedProductFlavors = Lists.newArrayList();
  @NotNull private final List<JCheckBox> myCheckBoxes = Lists.newArrayList();
  @NotNull private final SelectionChangeEventDispatcher<List<PsProductFlavor>> myEventDispatcher = new SelectionChangeEventDispatcher<>();

  ProductFlavorsPanel(@NotNull List<PsProductFlavor> productFlavors) {
    setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

    ChangeListener changeListener = e -> {
      updateSelection();
      myEventDispatcher.selectionChanged(mySelectedProductFlavors);
    };

    for (PsProductFlavor productFlavor : productFlavors) {
      JCheckBox checkBox = new JCheckBox(productFlavor.getName());
      checkBox.putClientProperty(PRODUCT_FLAVOR_PROPERTY, productFlavor);
      checkBox.addChangeListener(changeListener);
      checkBox.setSelected(true);
      myCheckBoxes.add(checkBox);
      add(checkBox);
    }

    updateSelection();
  }

  private void updateSelection() {
    mySelectedProductFlavors.clear();
    myCheckBoxes.stream().filter(AbstractButton::isSelected).forEach(radioButton -> {
      Object value = radioButton.getClientProperty(PRODUCT_FLAVOR_PROPERTY);
      if (value instanceof PsProductFlavor) {
        mySelectedProductFlavors.add((PsProductFlavor)value);
      }
    });
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
    return myCheckBoxes.isEmpty() ? null : myCheckBoxes.get(0);
  }
}
