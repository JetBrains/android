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

import com.android.tools.idea.configurations.OverlayContainer;
import com.intellij.android.designer.designSurface.graphics.DesignerGraphics;
import com.intellij.android.designer.designSurface.graphics.DrawingStyle;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;

/** Overlay which paints the currently hovered view (the view under the mouse) */
public class HoverOverlay extends Overlay {
  private final OverlayContainer myContainer;
  private RenderedView myHoveredView;

  public HoverOverlay(@NotNull OverlayContainer container) {
    myContainer = container;
  }

  public boolean setHoveredView(@Nullable RenderedView view) {
    boolean changed = view != myHoveredView;
    if (changed) {
      myHoveredView = view;
    }
    return changed;
  }

  @Override
  public void paint(@Nullable Component component, @NotNull Graphics2D gc, int deltaX, int deltaY) {
    if (component == null || myHoveredView == null) {
      return;
    }

    RenderedViewHierarchy viewHierarchy = myContainer.getViewHierarchy();
    if (viewHierarchy == null) {
      return;
    }
    boolean hoverIsSelected = myHoveredView.tag != null && myContainer.isSelected(myHoveredView.tag);
    DrawingStyle style = hoverIsSelected ? DrawingStyle.HOVER_SELECTION : DrawingStyle.HOVER;
    Rectangle r = myContainer.fromModel(component, myHoveredView.getBounds());

    Shape prevClip = gc.getClip();
    Shape clip = setScreenClip(myContainer, component, gc, deltaX, deltaY);
    DesignerGraphics.drawFilledRect(style, gc, r.x + deltaX, r.y + deltaY, r.width, r.height);
    if (clip != null) {
      gc.setClip(prevClip);
    }
  }
}
