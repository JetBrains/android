/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.common.fixtures;

import static org.mockito.Mockito.mock;

import com.android.sdklib.AndroidCoordinate;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.DragEnterEvent;
import com.android.tools.idea.common.surface.DragOverEvent;
import com.android.tools.idea.common.surface.DropEvent;
import com.android.tools.idea.common.surface.InteractionInformation;
import com.android.tools.idea.common.surface.InteractionNonInputEvent;
import com.android.tools.idea.common.surface.KeyPressedEvent;
import com.android.tools.idea.common.surface.KeyReleasedEvent;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.surface.interaction.DragDropInteraction;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDragEvent;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.List;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.NotNull;

/**
 * Fixture to simulate dragging across swing components. See {@link java.awt.dnd.DropTargetListener}.
 */
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

    int startX = Coordinates.getSwingX(myScreen, NlComponentHelperKt.getX(primary) + NlComponentHelperKt.getW(primary) / 2);
    int startY = Coordinates.getSwingY(myScreen, NlComponentHelperKt.getY(primary) + NlComponentHelperKt.getH(primary) / 2);

    DropTargetContext context = mock(DropTargetContext.class);
    Transferable transferable = myScreen.getSurface().getSelectionAsTransferable();
    DropTargetDragEvent dragDropEvent = new DropTargetDragEventBuilder(context, startX, startY, transferable).build();

    DropTargetDragEvent event = createDropTargetDragEvent(startX, startY);
    myInteraction.begin(new DragEnterEvent(event, new InteractionInformation(startX, startY, myModifiers)));
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

    DesignSurface<?> surface = myScreen.getSurface();
    KeyEvent event = new KeyEventBuilder(keyCode, keyChar).withSource(surface).build();
    myInteraction.update(new KeyPressedEvent(event, new InteractionInformation(myCurrentX, myCurrentY, myModifiers)));
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
    DesignSurface<?> surface = myScreen.getSurface();
    KeyEvent event = new KeyEventBuilder(keyCode, keyChar).withSource(surface).build();
    myInteraction.update(new KeyReleasedEvent(event, new InteractionInformation(myCurrentX, myCurrentY, myModifiers)));
    return this;
  }

  private void moveTo(@SwingCoordinate int x, @SwingCoordinate int y) {
    myCurrentX = x;
    myCurrentY = y;
    myInteraction.update(new DragOverEvent(createDropTargetDragEvent(x, y),
                                           new InteractionInformation(myCurrentX, myCurrentY, myModifiers)));
  }

  public ComponentListFixture release() {
    myInteraction.commit(new DropEvent(createDropTargetDropEvent(myCurrentX, myCurrentY),
                                       new InteractionInformation(myCurrentX, myCurrentY, myModifiers)));
    return myComponents;
  }

  public ComponentListFixture cancel() {
    myInteraction.cancel(new InteractionNonInputEvent(new InteractionInformation(myCurrentX, myCurrentY, myModifiers)));
    return myComponents;
  }

  @NotNull
  private DropTargetDragEvent createDropTargetDragEvent(@SwingCoordinate int x, @SwingCoordinate int y) {
    DropTargetContext context = mock(DropTargetContext.class);
    Transferable transferable = myScreen.getSurface().getSelectionAsTransferable();
    return new DropTargetDragEventBuilder(context, x, y, transferable).build();
  }

  @NotNull
  private DropTargetDropEvent createDropTargetDropEvent(@SwingCoordinate int x, @SwingCoordinate int y) {
    DropTargetContext context = mock(DropTargetContext.class);
    Transferable transferable = myScreen.getSurface().getSelectionAsTransferable();
    return new DropTargetDropEventBuilder(context, x, y, transferable).build();
  }
}
