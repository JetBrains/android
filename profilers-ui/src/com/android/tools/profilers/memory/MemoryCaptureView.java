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
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.FileOutputStream;
import java.io.IOException;

public final class MemoryCaptureView extends AspectObserver {
  private static Logger getLogger() {
    return Logger.getInstance(MemoryCaptureView.class);
  }

  @NotNull private final MemoryProfilerStage myStage;

  @NotNull private final JLabel myLabel = new JLabel();

  @NotNull private final JButton myExportButton;

  @Nullable private CaptureObject myCaptureObject = null;

  MemoryCaptureView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::reset)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::refresh);
    myExportButton =
      ideProfilerComponents.createExportButton("Export",
                                               "Exports the currently selected capture to file.",
                                               () -> "Export As",
                                               this::getFileExtension,
                                               file -> stage.getStudioProfilers().getIdeServices().saveFile(file, this::saveToFile, null));
    reset();
  }

  @NotNull
  JLabel getComponent() {
    return myLabel;
  }

  @NotNull
  JButton getExportButton() {
    return myExportButton;
  }

  private void reset() {
    myLabel.setText("");
    myCaptureObject = myStage.getSelectedCapture();
    myExportButton.setEnabled(false);
  }

  private void refresh() {
    CaptureObject captureObject = myStage.getSelectedCapture();
    boolean validCapture = captureObject == myCaptureObject && myCaptureObject != null;
    myExportButton.setEnabled(validCapture);
    if (validCapture) {
      myLabel.setText(myCaptureObject.getLabel());
    }
  }

  @Nullable
  private String getFileExtension() {
    return myCaptureObject == null ? null : myCaptureObject.getExportableExtension();
  }

  private void saveToFile(@NotNull FileOutputStream outputStream) {
    if (myCaptureObject != null) {
      try {
        myCaptureObject.saveToFile(outputStream);
      }
      catch (IOException e) {
        getLogger().warn(e);
      }
    }
  }
}
