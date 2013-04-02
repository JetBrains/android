/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.intellij.android.designer;

import com.android.resources.Density;
import com.intellij.android.designer.designSurface.AndroidDesignerEditorPanel;
import com.intellij.android.designer.designSurface.RootView;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadVisualComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Locale;

import static com.android.SdkConstants.VALUE_N_DP;
import static com.android.resources.Density.DEFAULT_DENSITY;

public class AndroidDesignerUtils {
  private AndroidDesignerUtils() {
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // Pixel (px) to Device Independent Pixel (dp) conversion
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Nullable
  public static AndroidDesignerEditorPanel getPanel(@NotNull EditableArea area) {
    RadComponent root = area.getRootComponent();
    if (root instanceof RadVisualComponent) {
      Component nativeComponent = ((RadVisualComponent)root).getNativeComponent();
      if (nativeComponent instanceof RootView) {
        return ((RootView)nativeComponent).getPanel();
      }
    }

    return null;
  }

  /** Returns the dpi used to render in the designer corresponding to the given {@link EditableArea} */
  public static int getDpi(@NotNull EditableArea area) {
    AndroidDesignerEditorPanel panel = getPanel(area);
    if (panel != null) {
      return panel.getDpi();
    }

    return DEFAULT_DENSITY;
  }

  /**
   * Converts a pixel to a dp (device independent pixel) for the current screen density
   *
   * @param area the associated {@link EditableArea}
   * @param px the pixel dimension
   * @return the corresponding dp dimension
   */
  public static int pxToDp(@NotNull EditableArea area, int px) {
    int dpi = getDpi(area);
    return px * DEFAULT_DENSITY / dpi;
  }

  /**
   * Converts a pixel to a dp (device independent pixel) for the current screen density,
   * and returns it as a dimension string.
   *
   * @param area the associated {@link EditableArea}
   * @param px the pixel dimension
   * @return the corresponding dp dimension string
   */
  @NotNull
  public static String pxToDpWithUnits(@NotNull EditableArea area, int px) {
    return String.format(Locale.US, VALUE_N_DP, pxToDp(area, px));
  }

  /**
   * Converts a device independent pixel to a screen pixel for the current screen density
   *
   * @param dp the device independent pixel dimension
   * @return the corresponding pixel dimension
   */
  public static int dpToPx(@NotNull EditableArea area, int dp) {
    int dpi = getDpi(area);
    return dp * dpi / DEFAULT_DENSITY;
  }
}
