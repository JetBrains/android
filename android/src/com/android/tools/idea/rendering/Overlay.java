/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.idea.rendering;

import com.android.ide.common.rendering.HardwareConfigHelper;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.Screen;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.OverlayContainer;
import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

public abstract class Overlay implements Disposable {

  /** Whether the hover is hidden */
  private boolean mHiding;

  /**
   * Construct the overlay, using the given graphics context for painting.
   */
  public Overlay() {
    super();
  }

  /**
   * Initializes the overlay before the first use, if applicable. This is a
   * good place to initialize resources like colors.
   */
  public void create() {
  }

  /**
   * Releases resources held by the overlay. Called by the editor when an
   * overlay has been removed.
   */
  @Override
  public void dispose() {
  }

  /**
   * Paints the overlay.
   *
   * @param component
   * @param gc The Graphics object to draw into
   */
  public void paint(@Nullable Component component, @NotNull Graphics2D gc, int deltaX, int deltaY) {
    assert false; // probably using wrong paint signature in overlay
  }

  /**
   * Returns whether the overlay is hidden
   *
   * @return true if the selection overlay is hidden
   */
  public boolean isHiding() {
    return mHiding;
  }

  /**
   * Hides the overlay
   *
   * @param hiding true to hide the overlay, false to unhide it (default)
   */
  public void setHiding(boolean hiding) {
    mHiding = hiding;
  }

  /** Utility method which paints the overlays for a given container */
  public static void paintOverlays(@NotNull OverlayContainer container, @Nullable Component component, @NotNull Graphics g,
                                   int deltaX, int deltaY) {
    Graphics2D g2 = (Graphics2D)g;
    List<Overlay> overlays = container.getOverlays();
    if (overlays != null) {
      for (Overlay overlay : overlays) {
        if (!overlay.isHiding()) {
          overlay.paint(component, g2, deltaX, deltaY);
        }
      }
    }
  }

  /**
   * Sets a screen clip for the overlay if applicable. Returns the new clip if it was set.
   */
  @Nullable
  protected Shape setScreenClip(@NotNull OverlayContainer container, @NotNull Component component, @NotNull Graphics2D gc,
                                int deltaX, int deltaY) {
    Configuration configuration = container.getConfiguration();
    Shape clip = null;
    if (configuration != null) {
      Device device = configuration.getDevice();
      if (device != null && device.isScreenRound()) {
        Screen screen = device.getDefaultHardware().getScreen();
        int width = screen.getXDimension();
        int height = screen.getYDimension();
        Rectangle m = container.fromModel(component, new Rectangle(0, 0, width, height));
        clip = RenderedImage.getClip(device, m.x + deltaX, m.y + deltaY, m.width, m.height);
      }
      if (clip != null) {
        gc.setClip(clip);
      }
    }
    return clip;
  }
}
