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
package com.android.tools.profilers.memory;

import com.android.tools.adtui.flat.FlatComboBox;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.memory.MemoryProfilerConfiguration.ClassGrouping;
import com.intellij.util.ui.JBEmptyBorder;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.plaf.basic.BasicComboBoxRenderer;

public class MemoryClassGrouping extends AspectObserver {
  @NotNull private final MemoryProfilerStage myStage;
  @NotNull private final FlatComboBox<ClassGrouping> myComboBox;

  public MemoryClassGrouping(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myStage.getAspect().addDependency(this).onChange(MemoryProfilerAspect.CLASS_GROUPING, this::groupingChanged);
    myComboBox = new FlatComboBox<>();
    myComboBox.setModel(new DefaultComboBoxModel<>(ClassGrouping.values()));
    BasicComboBoxRenderer comboBoxRenderer = new BasicComboBoxRenderer();
    comboBoxRenderer.setBorder(new JBEmptyBorder(UIUtil.getListCellPadding()));
    myComboBox.setRenderer(comboBoxRenderer);
    myComboBox.addActionListener(e -> {
      Object item = myComboBox.getSelectedItem();
      if (item != null && item instanceof ClassGrouping) {
        myStage.getConfiguration().setClassGrouping((ClassGrouping)item);
      }
    });
  }

  @NotNull
  FlatComboBox<ClassGrouping> getComponent() {
    return myComboBox;
  }

  public void groupingChanged() {
    if (myComboBox.getSelectedItem() != myStage.getConfiguration().getClassGrouping()) {
      myComboBox.setSelectedItem(myStage.getConfiguration().getClassGrouping());
    }
  }
}
