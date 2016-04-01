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
package com.android.tools.idea.uibuilder.fixtures;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ResizeInteraction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.intellij.lang.annotations.MagicConstant;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import static org.junit.Assert.*;

public class ResizeFixture {
  @NotNull private final ComponentFixture myComponentFixture;
  @NotNull private final ResizeInteraction myInteraction;
  @SwingCoordinate private int myCurrentX;
  @SwingCoordinate private int myCurrentY;
  private final ScreenView myScreen;
  private int myModifiers;

  public ResizeFixture(@NotNull ComponentFixture componentFixture,
                       @Nullable SegmentType horizontalEdge,
                       @Nullable SegmentType verticalEdge) {
    myComponentFixture = componentFixture;
    assertTrue(horizontalEdge != null || verticalEdge != null);
    assertTrue(horizontalEdge == null || horizontalEdge.isHorizontal());
    assertTrue(verticalEdge == null || !verticalEdge.isHorizontal());

    myScreen = componentFixture.getScreen();
    SelectionHandle.Position position;
    if (horizontalEdge == SegmentType.TOP) {
      if (verticalEdge == SegmentType.LEFT) {
        position = SelectionHandle.Position.TOP_LEFT;
      } else if (verticalEdge == SegmentType.RIGHT) {
        position = SelectionHandle.Position.TOP_RIGHT;
      } else {
        assertNull(verticalEdge);
        position = SelectionHandle.Position.TOP_MIDDLE;
      }
    } else if (horizontalEdge == SegmentType.BOTTOM) {
      if (verticalEdge == SegmentType.LEFT) {
        position = SelectionHandle.Position.BOTTOM_LEFT;
      } else if (verticalEdge == SegmentType.RIGHT) {
        position = SelectionHandle.Position.BOTTOM_RIGHT;
      } else {
        assertNull(verticalEdge);
        position = SelectionHandle.Position.BOTTOM_MIDDLE;
      }
    } else {
      // Can't resize from edges like the baseline
      fail("Can't resize from edges like the baseline: " + horizontalEdge);
      position = null;
    }
    NlComponent component = componentFixture.getComponent();
    SelectionHandle handle = new SelectionHandle(component, position);
    myInteraction = new ResizeInteraction(myScreen, component, handle);

    int startX = Coordinates.getSwingX(myScreen, handle.getCenterX());
    int startY = Coordinates.getSwingX(myScreen, handle.getCenterY());
    myInteraction.begin(startX, startY, 0);
    myCurrentX = startX;
    myCurrentY = startY;
    moveTo(myCurrentX, myCurrentY);
  }

  public ResizeFixture drag(@AndroidCoordinate int deltaX, @AndroidCoordinate int deltaY) {
    moveTo(myCurrentX + Coordinates.getSwingDimension(myScreen, deltaX),
           myCurrentY + Coordinates.getSwingDimension(myScreen, deltaY));
    return this;
  }

  public ResizeFixture dragTo(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    moveTo(Coordinates.getSwingX(myScreen, x), Coordinates.getSwingY(myScreen, y));
    return this;
  }

  public ResizeFixture modifiers(int modifiers) {
    myModifiers = modifiers;
    return this;
  }

  public ResizeFixture pressKey(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode, char keyChar) {
    if (keyCode == KeyEvent.VK_SHIFT) {
      myModifiers |= InputEvent.SHIFT_MASK;
    }
    if (keyCode == KeyEvent.VK_META) {
      myModifiers |= InputEvent.META_MASK;
    }
    if (keyCode == KeyEvent.VK_CONTROL) {
      myModifiers |= InputEvent.CTRL_MASK;
    }
    if (keyCode == KeyEvent.VK_ALT) {
      myModifiers |= InputEvent.ALT_MASK;
    }
    DesignSurface surface = myScreen.getSurface();
    KeyEvent event = new KeyEventBuilder(keyCode, keyChar).withSource(surface).build();
    myInteraction.keyPressed(event);
    return this;
  }

  public ResizeFixture releaseKey(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode, char keyChar) {
    if (keyCode == KeyEvent.VK_SHIFT) {
      myModifiers |= InputEvent.SHIFT_MASK;
    }
    if (keyCode == KeyEvent.VK_META) {
      myModifiers |= InputEvent.META_MASK;
    }
    if (keyCode == KeyEvent.VK_CONTROL) {
      myModifiers |= InputEvent.CTRL_MASK;
    }
    if (keyCode == KeyEvent.VK_ALT) {
      myModifiers |= InputEvent.ALT_MASK;
    }
    DesignSurface surface = myScreen.getSurface();
    KeyEvent event = new KeyEventBuilder(keyCode, keyChar).withSource(surface).build();
    myInteraction.keyReleased(event);
    return this;
  }

  private void moveTo(@SwingCoordinate int x, @SwingCoordinate int y) {
    myCurrentX = x;
    myCurrentY = y;
    myInteraction.update(myCurrentX, myCurrentY, myModifiers);
  }

  public ComponentFixture release() {
    myInteraction.end(myCurrentX, myCurrentY, myModifiers, false);
    return myComponentFixture;
  }

  public ComponentFixture cancel() {
    myInteraction.end(myCurrentX, myCurrentY, myModifiers, true);
    return myComponentFixture;
  }
}