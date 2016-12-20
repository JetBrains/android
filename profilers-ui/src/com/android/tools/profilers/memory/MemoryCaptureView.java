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

import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class MemoryCaptureView extends AspectObserver {
  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final JLabel myLabel = new JLabel();

  @Nullable private CaptureObject myCaptureObject = null;

  MemoryCaptureView(@NotNull MemoryProfilerStage stage) {
    myStage = stage;
    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::reset)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::refresh);
    refresh();
  }

  @NotNull
  JLabel getComponent() {
    return myLabel;
  }

  private void reset() {
    myLabel.setText("");
    myCaptureObject = myStage.getSelectedCapture();
  }

  private void refresh() {
    CaptureObject captureObject = myStage.getSelectedCapture();
    if (captureObject == myCaptureObject && myCaptureObject != null) {
      myLabel.setText(myCaptureObject.getLabel());
    }
  }
}
