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

import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED;
import static javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED;

import com.android.tools.profilers.IdeProfilerComponents;
import com.android.tools.profilers.memory.adapters.AndroidBitmapDataProvider;
import com.android.tools.profilers.memory.adapters.BitmapDecoder;
import com.android.tools.profilers.memory.adapters.CaptureObject;
import com.android.tools.profilers.memory.adapters.InstanceObject;
import com.intellij.ui.components.JBScrollPane;
import java.awt.BorderLayout;
import java.awt.image.BufferedImage;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.intellij.util.ui.JBUI;

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

    InstanceObject bitmapInstance = AndroidBitmapDataProvider.getBitmapClassInstance(instanceObject);
    // If instanceObject is not an instance of "android.graphics.Bitmap" or "android.graphics.drawable.BitmapDrawable"
    if (bitmapInstance == null) {
      return null;
    }

    AndroidBitmapDataProvider bitmapDataProvider = AndroidBitmapDataProvider.createDecoder(bitmapInstance);
    if (bitmapDataProvider == null) {
      if (bitmapInstance.getDepth() != Integer.MAX_VALUE) {
        // The bitmap object is live (reachable from GC roots), but its image data is missing in this heap dump.
        // This typically happens if the '-b' flag, which includes bitmap pixel data, wasn't used/supported in the heap dump command.
        // This missing-data scenario is expected on API levels 26-28 where the '-b' flag isn't supported,
        // and on API levels 29-34 if mainline updates haven't been applied.
        return null;
      }
      JComponent viewer = new JLabel("This bitmap can't be previewed because its data isn't currently connected to any active part " +
                                     "of the program. It will be automatically cleaned up by the garbage collector.");

      JPanel panel = new JPanel(new BorderLayout());
      panel.setBorder(JBUI.Borders.empty(8));
      panel.setName("Bitmap Preview");
      panel.add(viewer);
      return new JBScrollPane(panel);
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
