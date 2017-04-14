/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.profilers.memory.instanceviewers;

import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.memory.adapters.AndroidBitmapDataProvider;
import com.android.tools.profilers.memory.adapters.BitmapDecoder;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.intellij.ui.components.JBScrollPane;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

public class BitmapViewer implements InstanceViewer {
  @NotNull
  @Override
  public String getTitle() {
    return "Bitmap Preview";
  }

  @Nullable
  @Override
  public JComponent createComponent(@NotNull IdeProfilerComponents ideProfilerComponents,
                                    @NotNull CaptureObject captureObject,
                                    @NotNull InstanceObject instanceObject) {
    AndroidBitmapDataProvider bitmapDataProvider = AndroidBitmapDataProvider.createDecoder(instanceObject);
    if (bitmapDataProvider == null) {
      return null;
    }
    BufferedImage image = BitmapDecoder.getBitmap(bitmapDataProvider);
    if (image == null) {
      return null;
    }
    JComponent viewer = ideProfilerComponents.createResizableImageComponent(image);

    JPanel panel = new JPanel(new BorderLayout());
    panel.setName("Bitmap Preview");
    panel.add(viewer, BorderLayout.CENTER);
    return new JBScrollPane(panel, VERTICAL_SCROLLBAR_AS_NEEDED, HORIZONTAL_SCROLLBAR_AS_NEEDED);
  }
}
