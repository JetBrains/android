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
package com.android.tools.adtui.chart.linechart;

import com.android.tools.adtui.AnimatedComponent;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.util.ArrayList;

/**
 * A custom component that handles drawing and mouse interaction with DurationData
 * TODO: currently this supports dispatching unhandled events to a selected component, but that dispatching mechanism is direct.
 * We need a solution to bubble the event up to the correct component that is registered to handle the event.
 */
public final class OverlayComponent extends AnimatedComponent {

  @NotNull private final ArrayList<DurationDataRenderer> myDurationRenderers;
  @NotNull private final Component myDispatchComponent;

  public OverlayComponent(@NotNull Component dispatchComponent) {
    myDurationRenderers = new ArrayList<>();
    myDispatchComponent = dispatchComponent;

    addMouseListener(new MouseListener() {
      @Override
      public void mousePressed(MouseEvent e) {
        handleOrDispatchEvent(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        myDispatchComponent.dispatchEvent(e);
      }

      @Override
      public void mouseEntered(MouseEvent e) {
        myDispatchComponent.dispatchEvent(e);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        myDispatchComponent.dispatchEvent(e);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        myDispatchComponent.dispatchEvent(e);
      }
    });

    addMouseMotionListener(new MouseMotionListener() {
      @Override
      public void mouseDragged(MouseEvent e) {
        myDispatchComponent.dispatchEvent(e);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        handleOrDispatchEvent(e);
      }
    });
  }

  public void addDurationDataRenderer(@NotNull DurationDataRenderer renderer) {
    myDurationRenderers.add(renderer);
  }

  @Override
  protected void draw(Graphics2D g, Dimension size) {
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    // TODO make this an interface
    for (DurationDataRenderer renderer : myDurationRenderers) {
      renderer.renderOverlay(this, g);
    }
  }

  private void handleOrDispatchEvent(MouseEvent e) {
    boolean handled = false;
    for (DurationDataRenderer renderer : myDurationRenderers) {
      // TODO make this an interface
      handled |= renderer.handleMouseEvent(e);
      if (handled) {
        break;
      }
    }

    if (!handled) {
      myDispatchComponent.dispatchEvent(e);
    }
  }
}
