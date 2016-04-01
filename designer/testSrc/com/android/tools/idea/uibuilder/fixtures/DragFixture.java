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
import com.android.tools.idea.uibuilder.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.DragDropInteraction;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import org.intellij.lang.annotations.MagicConstant;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;

public class DragFixture {
  @NotNull private final DragDropInteraction myInteraction;
  @NotNull private final ComponentListFixture myComponents;
  @NotNull private final ScreenView myScreen;
  @SwingCoordinate private int myCurrentX;
  @SwingCoordinate private int myCurrentY;
  private int myModifiers;

  public DragFixture(@NotNull ComponentListFixture components) {
    myComponents = components;

    myScreen = myComponents.getScreen();
    List<NlComponent> componentList = myComponents.getComponents();
    myInteraction = new DragDropInteraction(myScreen.getSurface(), componentList);

    // Drag from center of primary
    NlComponent primary = componentList.get(0);
    int startX = Coordinates.getSwingX(myScreen, primary.x + primary.w / 2);
    int startY = Coordinates.getSwingX(myScreen, primary.y + primary.h / 2);
    myInteraction.begin(startX, startY, 0);
    myCurrentX = startX;
    myCurrentY = startY;
    moveTo(myCurrentX, myCurrentY);
  }

  public DragFixture drag(@AndroidCoordinate int deltaX, @AndroidCoordinate int deltaY) {
    moveTo(myCurrentX + Coordinates.getSwingDimension(myScreen, deltaX),
           myCurrentY + Coordinates.getSwingDimension(myScreen, deltaY));
    return this;
  }

  public DragFixture modifiers(int modifiers) {
    myModifiers = modifiers;
    return this;
  }

  public DragFixture dragTo(@AndroidCoordinate int x, @AndroidCoordinate int y) {
    moveTo(Coordinates.getSwingX(myScreen, x), Coordinates.getSwingY(myScreen, y));
    return this;
  }

  public DragFixture pressKey(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode, char keyChar) {
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

  public DragFixture releaseKey(@MagicConstant(flagsFromClass = KeyEvent.class) int keyCode, char keyChar) {
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

  public ComponentListFixture release() {
    myInteraction.end(myCurrentX, myCurrentY, myModifiers, false);
    return myComponents;
  }

  public ComponentListFixture cancel() {
    myInteraction.end(myCurrentX, myCurrentY, myModifiers, true);
    return myComponents;
  }
}
