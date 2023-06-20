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
package com.android.tools.idea.common;

import android.view.View;
import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.adtui.common.SwingCoordinate;
import com.android.tools.analytics.*;
import com.android.tools.idea.common.fixtures.KeyEventBuilder;
import com.android.tools.idea.uibuilder.analytics.NlUsageTracker;
import com.android.tools.idea.common.fixtures.MouseEventBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.GuiInputHandler;
import com.android.tools.idea.common.fixtures.DropTargetDragEventBuilder;
import com.android.tools.idea.common.fixtures.DropTargetDropEventBuilder;
import com.android.tools.idea.uibuilder.model.NlComponentMixin;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DnDConstants;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.IOException;

import static com.android.tools.idea.uibuilder.surface.ScreenView.DEVICE_CONTENT_SIZE_POLICY;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.*;

public class LayoutTestUtilities {
  public static void dragMouse(GuiInputHandler manager,
                               @SwingCoordinate int x1,
                               @SwingCoordinate int y1,
                               @SwingCoordinate int x2,
                               @SwingCoordinate int y2,
                               int modifiers) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseMotionListener);
    MouseMotionListener mouseListener = (MouseMotionListener)listener;
    int frames = 5;
    double x = x1;
    double y = y1;
    double xSlope = (x2 - x) / frames;
    double ySlope = (y2 - y) / frames;

    JComponent interactionPane = manager.getInteractable().getInteractionPane();
    for (int i = 0; i < frames + 1; i++) {
      MouseEvent event = new MouseEventBuilder((int)x, (int)y)
        .withSource(interactionPane)
        .withMask(modifiers | InputEvent.BUTTON1_DOWN_MASK)
        .withId(MouseEvent.MOUSE_DRAGGED)
        .build();
      mouseListener.mouseDragged(event);
      x += xSlope;
      y += ySlope;
    }
  }

  public static void moveMouse(GuiInputHandler manager,
                               @SwingCoordinate int x1,
                               @SwingCoordinate int y1,
                               @SwingCoordinate int x2,
                               @SwingCoordinate int y2) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseMotionListener);
    MouseMotionListener mouseListener = (MouseMotionListener)listener;

    JComponent interactionPane = manager.getInteractable().getInteractionPane();
    int frames = 5;
    double x = x1;
    double y = y1;
    double xSlope = (x2 - x) / frames;
    double ySlope = (y2 - y) / frames;
    for (int i = 0; i < frames + 1; i++) {
      MouseEvent event = new MouseEventBuilder((int)x, (int)y)
        .withSource(interactionPane)
        .withId(MouseEvent.MOUSE_MOVED)
        .build();
      mouseListener.mouseMoved(event);
      x += xSlope;
      y += ySlope;
    }
  }

  public static void pressMouse(GuiInputHandler manager, int button, @SwingCoordinate int x, @SwingCoordinate int y, int modifiers) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseListener);
    MouseListener mouseListener = (MouseListener)listener;
    JComponent interactionPane = manager.getInteractable().getInteractionPane();
    mouseListener.mousePressed(new MouseEventBuilder(x, y)
                                 .withSource(interactionPane)
                                 .withMask(modifiers)
                                 .withButton(button)
                                 .withId(MouseEvent.MOUSE_PRESSED)
                                 .build());
  }

  public static void releaseMouse(GuiInputHandler manager, int button, @SwingCoordinate int x, @SwingCoordinate int y, int modifiers) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof MouseListener);
    MouseListener mouseListener = (MouseListener)listener;
    JComponent interactionPane = manager.getInteractable().getInteractionPane();
    mouseListener.mouseReleased(new MouseEventBuilder(x, y)
                                  .withSource(interactionPane)
                                  .withMask(modifiers)
                                  .withButton(button)
                                  .withId(MouseEvent.MOUSE_RELEASED).build());
  }

  public static void clickMouse(GuiInputHandler manager,
                                int button,
                                int count,
                                @SwingCoordinate int x,
                                @SwingCoordinate int y,
                                int modifiers) {
    JComponent interactionPane = manager.getInteractable().getInteractionPane();
    for (int i = 0; i < count; i++) {
      pressMouse(manager, button, x, y, modifiers);
      releaseMouse(manager, button, x, y, modifiers);

      Object listener = manager.getListener();
      assertTrue(listener instanceof MouseListener);
      MouseListener mouseListener = (MouseListener)listener;
      MouseEvent event =
        new MouseEventBuilder(x, y)
          .withSource(interactionPane)
          .withButton(button)
          .withMask(modifiers)
          .withClickCount(i + 1)
          .withId(MouseEvent.MOUSE_CLICKED)
          .build();
      mouseListener.mouseClicked(event);
    }
  }

  public static void dragDrop(GuiInputHandler manager,
                              @SwingCoordinate int x1,
                              @SwingCoordinate int y1,
                              @SwingCoordinate int x2,
                              @SwingCoordinate int y2,
                              Transferable transferable) {
    dragDrop(manager, x1, y1, x2, y2, transferable, DnDConstants.ACTION_COPY);
  }

  public static void dragDrop(GuiInputHandler manager,
                              @SwingCoordinate int x1,
                              @SwingCoordinate int y1,
                              @SwingCoordinate int x2,
                              @SwingCoordinate int y2,
                              Transferable transferable,
                              int dropAction) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof DropTargetListener);
    DropTargetListener dropListener = (DropTargetListener)listener;
    int frames = 5;
    double x = x1;
    double y = y1;
    double xSlope = (x2 - x) / frames;
    double ySlope = (y2 - y) / frames;

    DropTargetContext context = createDropTargetContext();
    dropListener.dragEnter(new DropTargetDragEventBuilder(context, (int)x, (int)y, transferable).withDropAction(dropAction).build());
    for (int i = 0; i < frames + 1; i++) {
      dropListener.dragOver(new DropTargetDragEventBuilder(context, (int)x, (int)y, transferable).withDropAction(dropAction).build());
      x += xSlope;
      y += ySlope;
    }

    DropTargetDropEvent dropEvent =
      new DropTargetDropEventBuilder(context, (int)x, (int)y, transferable).withDropAction(dropAction).build();
    dropListener.drop(dropEvent);

    verify(dropEvent, times(1)).acceptDrop(anyInt());
    verify(dropEvent, times(1)).dropComplete(true);
  }

  public static void releaseKey(@NotNull GuiInputHandler manager, int keyCode) {
    Object listener = manager.getListener();
    assertTrue(listener instanceof KeyListener);
    KeyListener keyListener = (KeyListener)listener;
    JComponent interactionPane = manager.getInteractable().getInteractionPane();
    keyListener.keyReleased(new KeyEventBuilder(keyCode, KeyEvent.CHAR_UNDEFINED)
                              .withSource(interactionPane)
                              .build());
  }

  public static ScreenView createScreen(SyncNlModel model) {
    return createScreen(model, 1, 0, 0);
  }

  public static ScreenView createScreen(SyncNlModel model, double scale,
                                        @SwingCoordinate int x, @SwingCoordinate int y) {
    DesignSurface<LayoutlibSceneManager> surface = (DesignSurface<LayoutlibSceneManager>)model.getSurface();
    LayoutlibSceneManager spy = spy(surface.getSceneManager());
    when(surface.getSceneManager()).thenReturn(spy);
    ScreenView screenView = new ScreenView((NlDesignSurface)surface, spy, DEVICE_CONTENT_SIZE_POLICY) {
      @Override
      public double getScale() {
        return scale;
      }
    };
    screenView.setLocation(x, y);
    when(spy.getSceneView()).thenReturn(screenView);
    surface.getScene().buildDisplayList(new DisplayList(), 0);
    return screenView;
  }

  public static DropTargetContext createDropTargetContext() {
    return mock(DropTargetContext.class);
  }

  public static Transferable createTransferable(DataFlavor flavor, Object data) throws IOException, UnsupportedFlavorException {
    Transferable transferable = mock(Transferable.class);

    when(transferable.getTransferDataFlavors()).thenReturn(new DataFlavor[]{flavor});
    when(transferable.getTransferData(eq(flavor))).thenReturn(data);
    when(transferable.isDataFlavorSupported(eq(flavor))).thenReturn(true);

    return transferable;
  }

  public static View mockViewWithBaseline(int baseline) {
    View view = mock(View.class);
    when(view.getBaseline()).thenReturn(baseline);
    return view;
  }

  @Nullable
  public static AnAction findActionForKey(@NotNull JComponent component, int keyCode, int modifiers) {
    Shortcut shortcutToFind = new KeyboardShortcut(KeyStroke.getKeyStroke(keyCode, modifiers), null);
    java.util.List<AnAction> actions = ActionUtil.getActions(component);
    for (AnAction action : actions) {
      for (Shortcut shortcut : action.getShortcutSet().getShortcuts()) {
        if (shortcut.equals(shortcutToFind)) {
          return action;
        }
      }
    }
    return null;
  }

  public static NlUsageTracker mockNlUsageTracker(@NotNull DesignSurface<?> surface) {
    AnalyticsSettingsData settings = new AnalyticsSettingsData();
    settings.setOptedIn(true);
    AnalyticsSettings.setInstanceForTest(settings);

    UsageTrackerWriter tracker = new TestUsageTracker(new VirtualTimeScheduler());
    UsageTracker.setWriterForTest(tracker);

    NlUsageTracker usageTracker = mock(NlUsageTracker.class);
    NlUsageTracker.MANAGER.setInstanceForTest(surface, usageTracker);
    return usageTracker;
  }

  public static void cleanUsageTrackerAfterTesting(@NotNull DesignSurface<?> surface) {
    NlUsageTracker.MANAGER.cleanAfterTesting(surface);
    UsageTracker.cleanAfterTesting();
  }

  public static NlComponent createMockComponent() {
    NlComponent mock = mock(NlComponent.class);
    when(mock.getMixin()).thenReturn(new NlComponentMixin(mock));
    return mock;
  }
}
