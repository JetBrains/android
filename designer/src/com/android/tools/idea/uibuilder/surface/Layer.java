/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.intellij.openapi.Disposable;

import javax.swing.*;
import java.awt.*;

/**
 * A layer can be thought of as a very lightweight {@link JComponent} that is stacked
 * on top of a {@link DesignSurface}. The critical difference between using a {@linkplain Layer}
 * and a nested {@link JComponent} is that the layer does not have its own coordinate system
 * and crucially, its own clipping shape.
 */
public abstract class Layer implements Disposable {

  /** Whether the layer is hidden */
  private boolean myHidden;

  /**
   * Construct the overlay
   */
  public Layer() {
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
   * @param gc The Graphics object to draw into
   *
   * @return return true if repaint is needed
   */
  public boolean paint(@NotNull Graphics2D gc) {
    assert false; // probably using wrong paint signature in overlay
    return false;
  }

  /**
   * Returns whether the overlay is hidden
   *
   * @return true if the selection overlay is hidden
   */
  public boolean isHidden() {
    return myHidden;
  }

  /**
   * Hides the overlay
   *
   * @param hidden true to hide the overlay, false to unhide it (default)
   */
  public void setHidden(boolean hidden) {
    myHidden = hidden;
  }

  /** Returns a tooltip from this layer if one is appropriate at the given co-ordinates. */
  @Nullable
  public String getTooltip(@SwingCoordinate int x, @SwingCoordinate int y) {
    return null;
  }
}
