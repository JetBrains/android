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
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.HeapSet;
import com.intellij.ui.ColoredListCellRenderer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collection;
import java.util.Objects;

public class MemoryHeapView extends AspectObserver {
  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private FlatComboBox<HeapSet> myComboBox = new FlatComboBox<>();

  @Nullable private CaptureObject myCaptureObject = null;

  MemoryHeapView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::setNewCapture)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::updateCaptureState)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP, this::refreshHeap);

    myComboBox.addActionListener(e -> {
      Object item = myComboBox.getSelectedItem();
      if (item != null && item instanceof HeapSet) {
        myStage.selectHeapSet((HeapSet)item);
      }
    });
    setNewCapture();
    refreshHeap();
  }

  @NotNull
  FlatComboBox<HeapSet> getComponent() {
    return myComboBox;
  }

  private void setNewCapture() {
    myCaptureObject = myStage.getSelectedCapture();
    myComboBox.setModel(new DefaultComboBoxModel<>());
    myComboBox.setRenderer(new HeapListCellRenderer());
    myStage.selectHeapSet(null); // Clear the heap such that views lower in the hierarchy has a chance to repopulate themselves.
  }

  private void updateCaptureState() {
    CaptureObject captureObject = myStage.getSelectedCapture();
    if (myCaptureObject != captureObject) {
      return;
    }

    myCaptureObject = captureObject;
    if (myCaptureObject == null) {
      return; // Loading probably failed.
    }

    Collection<HeapSet> heaps = myCaptureObject.getHeapSets();
    HeapSet[] heapsArray = heaps.toArray(new HeapSet[heaps.size()]);
    ComboBoxModel<HeapSet> comboBoxModel = new DefaultComboBoxModel<>(heapsArray);
    myComboBox.setModel(comboBoxModel);
  }

  void refreshHeap() {
    HeapSet heapSet = myStage.getSelectedHeapSet();
    Object selectedObject = myComboBox.getSelectedItem();
    if (!Objects.equals(heapSet, selectedObject)) {
      myComboBox.setSelectedItem(heapSet);
    }
  }

  private static final class HeapListCellRenderer extends ColoredListCellRenderer<HeapSet> {
    @Override
    protected void customizeCellRenderer(@NotNull JList<? extends HeapSet> list,
                                         HeapSet value,
                                         int index,
                                         boolean selected,
                                         boolean hasFocus) {
      if (value != null) {
        append(value.getName() + " heap");
      }
    }
  }
}
