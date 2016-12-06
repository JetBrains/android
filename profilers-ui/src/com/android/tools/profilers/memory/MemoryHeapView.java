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

import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.HeapObject;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public class MemoryHeapView {
  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private ComboBox<HeapObject> myComboBox = new ComboBox<>();

  @Nullable private CaptureObject myCaptureObject = null;

  MemoryHeapView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;

    myStage.getAspect().addDependency()
      .setExecutor(ApplicationManager.getApplication(), Application::invokeLater)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::setNewCapture)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::updateCaptureState)
      .onChange(MemoryProfilerAspect.CURRENT_HEAP, this::refreshHeap);

    myComboBox.addActionListener(e -> {
      // TODO abstract out selection path so we don't need to special case
      Object item = myComboBox.getSelectedItem();
      if (item != null && item instanceof HeapObject) {
        myStage.selectHeap((HeapObject)item);
      }
    });
    setNewCapture();
    refreshHeap();
  }

  @NotNull
  ComboBox<HeapObject> getComponent() {
    return myComboBox;
  }

  private void setNewCapture() {
    myCaptureObject = myStage.getSelectedCapture();
    myComboBox.setModel(new DefaultComboBoxModel<>());
    myStage.selectHeap(null); // Clear the heap such that views lower in the hierarchy has a chance to repopulate themselves.
  }

  private void updateCaptureState() {
    CaptureObject captureObject = myStage.getSelectedCapture();
    if (myCaptureObject != captureObject) {
      return;
    }

    assert myCaptureObject != null; // Clearing the capture should have gone through {@link #setNewCapture()} instead.
    myCaptureObject = captureObject;
    List<HeapObject> heaps = myCaptureObject.getHeaps();
    ComboBoxModel<HeapObject> comboBoxModel = new DefaultComboBoxModel<>(heaps.toArray(new HeapObject[heaps.size()]));
    myComboBox.setModel(comboBoxModel);

    // TODO provide a default selection in the model API?
    for (HeapObject heap : heaps) {
      if (heap.getHeapName().equals("app")) {
        myComboBox.setSelectedItem(heap);
        myStage.selectHeap(heap);
        return;
      }
    }

    HeapObject heap = heaps.get(0);
    myComboBox.setSelectedItem(heap);
    myStage.selectHeap(heap);
  }

  void refreshHeap() {
    HeapObject heapObject = myStage.getSelectedHeap();
    Object selectedObject = myComboBox.getSelectedItem();
    if (heapObject != selectedObject) {
      myComboBox.setSelectedItem(heapObject);
    }
  }
}
