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

import com.android.tools.adtui.TabularLayout;
import com.android.tools.adtui.stdui.CommonButton;
import com.android.tools.adtui.flat.FlatSeparator;
import com.android.tools.adtui.model.AspectObserver;
import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.google.common.annotations.VisibleForTesting;
import com.intellij.icons.AllIcons;
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

  @NotNull private final JLabel myLabel;

  @NotNull private final JButton myExportButton;

  @NotNull private final JPanel myPanel;

  @Nullable private CaptureObject myCaptureObject = null;

  MemoryCaptureView(@NotNull MemoryProfilerStage stage, @NotNull IdeProfilerComponents ideProfilerComponents) {
    myStage = stage;
    myStage.getAspect().addDependency(this)
      .onChange(MemoryProfilerAspect.CURRENT_LOADING_CAPTURE, this::reset)
      .onChange(MemoryProfilerAspect.CURRENT_LOADED_CAPTURE, this::refresh);

    myPanel = new JPanel(new TabularLayout("Fit,Fit,Fit,Fit", "*"));

    myLabel = new JLabel();
    myLabel.setBorder(BorderFactory.createEmptyBorder(0, 3, 0, 3));

    myExportButton = new CommonButton(AllIcons.Actions.Export);
    myExportButton.setToolTipText("Export capture to file");
    myExportButton.addActionListener(e -> ideProfilerComponents.createExportDialog().open(
      () -> "Export As",
      () -> "heapdump",
      this::getFileExtension,
      file -> stage.getStudioProfilers().getIdeServices().saveFile(file, this::saveToFile, null)));
    reset();
  }

  @VisibleForTesting
  @NotNull
  JLabel getLabel() {
    return myLabel;
  }

  @NotNull
  JComponent getComponent() {
    return myPanel;
  }

  private void reset() {
    myPanel.removeAll();
    myLabel.setText("");
    myCaptureObject = myStage.getSelectedCapture();
    myExportButton.setEnabled(false);
  }

  private void refresh() {
    CaptureObject captureObject = myStage.getSelectedCapture();
    boolean validCapture = captureObject == myCaptureObject && myCaptureObject != null;

    if (validCapture) {
      if (captureObject.isExportable()) {
        myExportButton.setEnabled(captureObject.isExportable());
        myPanel.add(myExportButton, new TabularLayout.Constraint(0, 0));
        myPanel.add(new FlatSeparator(), new TabularLayout.Constraint(0, 1));
      }
      myLabel.setText(myCaptureObject.getName());
      myPanel.add(myLabel, new TabularLayout.Constraint(0, 2));
      myPanel.add(new FlatSeparator(), new TabularLayout.Constraint(0, 3));
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
