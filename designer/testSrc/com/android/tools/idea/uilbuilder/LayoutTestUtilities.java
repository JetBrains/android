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
package com.android.tools.idea.uilbuilder;

import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.model.SelectionModel;
import com.android.tools.idea.uibuilder.model.SwingCoordinate;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.InteractionManager;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.mockito.Mockito;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class LayoutTestUtilities {
  public static void moveMouse(InteractionManager manager, int x1, int y1, int x2, int y2, int modifiers) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseMotionListener);
    MouseMotionListener mouseListener = (MouseMotionListener)listener;
    int frames = 5;
    double x = x1;
    double y = y1;
    double xSlope = (x2 - x) / frames;
    double ySlope = (y2 - y) / frames;

    JComponent layeredPane = manager.getSurface().getLayeredPane();
    for (int i = 0; i < frames + 1; i++) {
      MouseEvent event = new MouseEventBuilder((int)x, (int)y).withSource(layeredPane).withMask(modifiers).build();
      mouseListener.mouseMoved(
        event);
      x += xSlope;
      y += ySlope;
    }
  }

  public static void pressMouse(InteractionManager manager, int button, int x, int y, int modifiers) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseListener);
    MouseListener mouseListener = (MouseListener)listener;
    JComponent layeredPane = manager.getSurface().getLayeredPane();
    mouseListener.mousePressed(new MouseEventBuilder(x, y).withSource(layeredPane).withMask(modifiers).build());
  }

  public static void releaseMouse(InteractionManager manager, int button, int x, int y, int modifiers) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseListener);
    MouseListener mouseListener = (MouseListener)listener;
    JComponent layeredPane = manager.getSurface().getLayeredPane();
    mouseListener.mousePressed(new MouseEventBuilder(x, y).withSource(layeredPane).withMask(modifiers).build());
  }

  public static void clickMouse(InteractionManager manager, int button, int count, int x, int y, int modifiers) {
    JComponent layeredPane = manager.getSurface().getLayeredPane();
    for (int i = 0; i < count; i++) {
      pressMouse(manager, button, x, y, modifiers);
      releaseMouse(manager, button, x, y, modifiers);

      Object listener = manager.getListener();
      assertTrue(listener instanceof MouseListener);
      MouseListener mouseListener = (MouseListener)listener;
      MouseEvent event =
        new MouseEventBuilder(x, y).withSource(layeredPane).withButton(button).withMask(modifiers).withClickCount(i).build();
      mouseListener.mouseClicked(event);
    }
  }

  public static void dragDrop(InteractionManager manager, int x1, int y1, int x2, int y2, Transferable transferable) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof DropTargetListener);
    DropTargetListener dropListener = (DropTargetListener)listener;
    int frames = 5;
    double x = x1;
    double y = y1;
    double xSlope = (x2 - x) / frames;
    double ySlope = (y2 - y) / frames;

    DropTargetContext context = createDropTargetContext();
    dropListener.dragEnter(new DropTargetDragEventBuilder(context, (int)x, (int)y, transferable).build());
    for (int i = 0; i < frames + 1; i++) {
      dropListener.dragOver(new DropTargetDragEventBuilder(context, (int)x, (int)y, transferable).build());
      x += xSlope;
      y += ySlope;
    }

    DropTargetDropEvent dropEvent = new DropTargetDropEventBuilder(context, (int)x, (int)y, transferable).build();
    dropListener.drop(dropEvent);

    verify(dropEvent, times(1)).acceptDrop(anyInt());
    verify(context, times(1)).dropComplete(true);
  }

  public static NlModel createModel(AndroidFacet facet, XmlFile xmlFile) {
    return NlModel.create(null, facet, xmlFile);
  }

  public static ScreenView createScreen(NlModel model, SelectionModel selectionModel) {
    ScreenView screenView = mock(ScreenView.class);
    when(screenView.getModel()).thenReturn(model);
    when(screenView.getSelectionModel()).thenReturn(selectionModel);
    return screenView;
  }

  public static DesignSurface createSurface(ScreenView screenView) {
    JComponent layeredPane = new JPanel();
    DesignSurface surface = mock(DesignSurface.class);
    when(surface.getLayeredPane()).thenReturn(layeredPane);
    when(surface.getScreenView(anyInt(), anyInt())).thenReturn(screenView);
    return surface;
  }

  public static InteractionManager createManager(DesignSurface surface) {
    InteractionManager manager = new InteractionManager(surface);
    manager.registerListeners();
    return manager;
  }

  public static class MouseEventBuilder {
    private final int myX;
    private final int myY;
    private Object mySource = LayoutTestUtilities.class;
    private int myButton = 1;
    private int myMask = 0;
    private int myClickCount = 1;

    public MouseEventBuilder(@SwingCoordinate int x, @SwingCoordinate int y) {
      myX = x;
      myY = y;
    }

    public MouseEventBuilder withSource(Object source) {
      mySource = source;
      return this;
    }

    public MouseEventBuilder withMask(int mask) {
      myMask = mask;
      return this;
    }

    public MouseEventBuilder withButton(int button) {
      myButton = button;
      return this;
    }

    public MouseEventBuilder withClickCount(int clickCount) {
      myClickCount = clickCount;
      return this;
    }

    public MouseEvent build() {
      MouseEvent event = mock(MouseEvent.class);
      when(event.getSource()).thenReturn(mySource);
      when(event.getX()).thenReturn(myX);
      when(event.getY()).thenReturn(myY);
      when(event.getModifiers()).thenReturn(myMask);
      when(event.getModifiersEx()).thenReturn(myMask);
      when(event.getButton()).thenReturn(myButton);
      when(event.getClickCount()).thenReturn(myClickCount);
      when(event.getPoint()).thenReturn(new Point(myX, myY));
      when(event.getWhen()).thenReturn(System.currentTimeMillis());
      return event;
    }
  }

  public static class KeyEventBuilder {
    private final int myKeyCode;
    private final char myKeyChar;
    private Object mySource = LayoutTestUtilities.class;
    private int myMask = 0;

    public KeyEventBuilder(int keyCode, char keyChar) {
      myKeyCode = keyCode;
      myKeyChar = keyChar;
    }

    public KeyEventBuilder withSource(Object source) {
      mySource = source;
      return this;
    }

    public KeyEventBuilder withMask(int mask) {
      myMask = mask;
      return this;
    }

    public KeyEvent build() {
      KeyEvent event = mock(KeyEvent.class);
      when(event.getSource()).thenReturn(mySource);
      when(event.getKeyCode()).thenReturn(myKeyCode);
      when(event.getKeyChar()).thenReturn(myKeyChar);
      when(event.getModifiers()).thenReturn(myMask);
      when(event.getModifiersEx()).thenReturn(myMask);
      when(event.getKeyLocation()).thenReturn(KeyEvent.KEY_LOCATION_UNKNOWN);
      when(event.getWhen()).thenReturn(System.currentTimeMillis());
      return event;
    }
  }

  public static class DropTargetEventBuilder {
    private final DropTargetContext myDropTargetContext;
    private Object mySource = LayoutTestUtilities.class;

    public DropTargetEventBuilder(DropTargetContext dropTargetContext) {
      myDropTargetContext = dropTargetContext;
    }

    public DropTargetEvent build() {
      DropTargetEvent event = mock(DropTargetEvent.class);
      when(event.getSource()).thenReturn(mySource);
      when(event.getDropTargetContext()).thenReturn(myDropTargetContext);
      return event;
    }
  }

  public static class DropTargetDragEventBuilder {
    private final DropTargetContext myDropTargetContext;
    private final int myX;
    private final int myY;
    private final Transferable myTransferable;
    private Object mySource = LayoutTestUtilities.class;
    private int mySourceActions = DnDConstants.ACTION_COPY_OR_MOVE;
    private int myDropAction = DnDConstants.ACTION_COPY;

    public DropTargetDragEventBuilder(DropTargetContext dropTargetContext, @SwingCoordinate int x, @SwingCoordinate int y,
                                      Transferable transferable) {
      myDropTargetContext = dropTargetContext;
      myX = x;
      myY = y;
      myTransferable = transferable;
    }

    public DropTargetDragEventBuilder withSource(Object source) {
      mySource = source;
      return this;
    }

    public DropTargetDragEventBuilder withSourceActions(int actions) {
      mySourceActions = actions;
      return this;
    }

    public DropTargetDragEventBuilder withDropAction(int action) {
      myDropAction = action;
      return this;
    }

    public DropTargetDragEvent build() {
      DropTargetDragEvent event = mock(DropTargetDragEvent.class);
      when(event.getSource()).thenReturn(mySource);
      when(event.getLocation()).thenReturn(new Point(myX, myY));
      when(event.getDropTargetContext()).thenReturn(myDropTargetContext);
      when(event.getDropAction()).thenReturn(myDropAction);
      when(event.getSourceActions()).thenReturn(mySourceActions);
      when(event.getTransferable()).thenReturn(myTransferable);
      DataFlavor[] flavors = myTransferable.getTransferDataFlavors();
      when(event.getCurrentDataFlavors()).thenReturn(flavors);
      when(event.getCurrentDataFlavorsAsList()).thenReturn(Arrays.asList(flavors));
      return event;
    }
  }

  public static class DropTargetDropEventBuilder {
    private final DropTargetContext myDropTargetContext;
    private final int myX;
    private final int myY;
    private final Transferable myTransferable;
    private Object mySource = LayoutTestUtilities.class;
    private int mySourceActions = DnDConstants.ACTION_COPY_OR_MOVE;
    private int myDropAction = DnDConstants.ACTION_COPY;

    public DropTargetDropEventBuilder(DropTargetContext dropTargetContext, @SwingCoordinate int x, @SwingCoordinate int y,
                                      Transferable transferable) {
      myDropTargetContext = dropTargetContext;
      myX = x;
      myY = y;
      myTransferable = transferable;
    }

    public DropTargetDropEventBuilder withSource(Object source) {
      mySource = source;
      return this;
    }

    public DropTargetDropEventBuilder withSourceActions(int actions) {
      mySourceActions = actions;
      return this;
    }

    public DropTargetDropEventBuilder withDropAction(int action) {
      myDropAction = action;
      return this;
    }

    public DropTargetDropEvent build() {
      DropTargetDropEvent event = mock(DropTargetDropEvent.class);
      when(event.getSource()).thenReturn(mySource);
      when(event.getLocation()).thenReturn(new Point(myX, myY));
      when(event.getDropTargetContext()).thenReturn(myDropTargetContext);
      when(event.getDropAction()).thenReturn(myDropAction);
      when(event.getSourceActions()).thenReturn(mySourceActions);
      when(event.getTransferable()).thenReturn(myTransferable);
      DataFlavor[] flavors = myTransferable.getTransferDataFlavors();
      when(event.getCurrentDataFlavors()).thenReturn(flavors);
      when(event.getCurrentDataFlavorsAsList()).thenReturn(Arrays.asList(flavors));
      return event;
    }
  }

  public static DropTargetContext createDropTargetContext() {
    return mock(DropTargetContext.class);
  }

  public static Transferable createTransferable(DataFlavor flavor, Object data) throws IOException, UnsupportedFlavorException {
    Transferable transferable = mock(Transferable.class);

    when(transferable.getTransferDataFlavors()).thenReturn(new DataFlavor[] { flavor });
    when(transferable.getTransferData(eq(flavor))).thenReturn(data);
    when(transferable.isDataFlavorSupported(eq(flavor))).thenReturn(true);

    return transferable;
  }
}
