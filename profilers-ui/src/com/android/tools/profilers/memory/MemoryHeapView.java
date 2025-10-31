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

import static com.android.tools.profilers.ProfilerLayout.createToolbarLayout;

import android.databinding.tool.util.StringUtils;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.ProfilerDropDownComponent;
import com.android.tools.profilers.ProfilerFlows;
import com.android.tools.profilers.Selection;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.classifiers.HeapSet;
import com.google.common.annotations.VisibleForTesting;
import java.util.Collection;
import java.util.Objects;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import kotlin.Unit;
import kotlinx.coroutines.flow.MutableStateFlow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MemoryHeapView extends AspectObserver {
  @NotNull private final MemoryCaptureSelection mySelection;

  @NotNull private final JPanel myHeapToolbar = new JPanel(createToolbarLayout());
  @NotNull private final ProfilerDropDownComponent<HeapSet> myHeapDropDownComponent;
  @NotNull private final MutableStateFlow<Selection<HeapSet>> myHeapSelectionFlow;

  @Nullable private CaptureObject myCaptureObject = null;
  @Nullable private List<HeapSet> myHeaps = null;

  MemoryHeapView(@NotNull MemoryCaptureSelection selection) {
    mySelection = selection;

    myHeapSelectionFlow = ProfilerFlows.createMutableStateFlow(Selection.Companion.emptySelection());
    mySelection.getAspect().addDependency(this)
      .onChange(CaptureSelectionAspect.CURRENT_LOADING_CAPTURE, this::setNewCapture)
      .onChange(CaptureSelectionAspect.CURRENT_LOADED_CAPTURE, this::updateCaptureState)
      .onChange(CaptureSelectionAspect.CURRENT_HEAP, this::refreshHeap);

    JLabel heapLabel = new JLabel("Heap:");
    heapLabel.setForeground(UIUtil.getLabelDisabledForeground());
    heapLabel.setBorder(JBUI.Borders.empty(1, 12, 0, 0));
    myHeapToolbar.add(heapLabel);

    myHeapDropDownComponent = new ProfilerDropDownComponent<>(
      StringUtils.capitalize(CaptureObject.APP_HEAP_NAME),
      "Select heap",
      null,
      myHeapSelectionFlow,
      null,
      heap -> {
        mySelection.selectHeapSet(heap);
        return Unit.INSTANCE;
      },
      this::getHeapDisplayName
    );
    myHeapToolbar.add(myHeapDropDownComponent);

    setNewCapture();
    refreshHeap();
  }

  @NotNull
  JComponent getComponent() {
    return myHeapToolbar;
  }

  @VisibleForTesting
  @NotNull
  ProfilerDropDownComponent<HeapSet> getHeapDropDown() {
    return myHeapDropDownComponent;
  }

  private void setNewCapture() {
    myCaptureObject = mySelection.getSelectedCapture();
    if (myCaptureObject == null) {
      myHeaps = null;
      return; // Loading probably failed.
    }
    mySelection.selectHeapSet(null);
    myHeaps = new ArrayList<>(myCaptureObject.getHeapSets());
    refreshHeap();
  }

  private void updateCaptureState() {
    setNewCapture();
  }

  private void refreshHeap() {
    HeapSet heapSet = mySelection.getSelectedHeapSet();
    List<HeapSet> heaps = myHeaps == null ? Collections.emptyList() : myHeaps;
    myHeapSelectionFlow.setValue(new Selection<>(heapSet, heaps));
  }

  private String getHeapDisplayName(HeapSet value) {
    if (value != null) {
      return StringUtils.capitalize(value.getName());
    }
    return StringUtils.capitalize(CaptureObject.APP_HEAP_NAME);
  }
}
