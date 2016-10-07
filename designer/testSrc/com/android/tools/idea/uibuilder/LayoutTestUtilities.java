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
package com.android.tools.idea.uibuilder;

import com.android.annotations.VisibleForTesting;
import com.android.resources.Density;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.fixtures.DropTargetDragEventBuilder;
import com.android.tools.idea.uibuilder.fixtures.DropTargetDropEventBuilder;
import com.android.tools.idea.uibuilder.fixtures.MouseEventBuilder;
import com.android.tools.idea.uibuilder.model.*;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.InteractionManager;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.DropTargetContext;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.dnd.DropTargetListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

public class LayoutTestUtilities {
  private static Map<NlComponent, Integer> ourComponentIds;

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
    verify(dropEvent, times(1)).dropComplete(true);
  }

  public static NlModel createModel(DesignSurface surface, AndroidFacet facet, XmlFile xmlFile) {
    NlModel model = SyncNlModel.create(surface, xmlFile.getProject(), facet, xmlFile);
    model.notifyModified(NlModel.ChangeType.UPDATE_HIERARCHY);
    return model;
  }

  public static ScreenView createScreen(DesignSurface surface, NlModel model, SelectionModel selectionModel) {
    return createScreen(surface, model, selectionModel, 1, 0, 0, Density.MEDIUM);
  }

  public static ScreenView createScreen(DesignSurface surface, NlModel model, SelectionModel selectionModel, double scale,
                                        @SwingCoordinate int x, @SwingCoordinate int y, Density density) {
    Configuration configuration = mock(Configuration.class);
    when(configuration.getDensity()).thenReturn(density);
    when(configuration.getFile()).thenReturn(model.getFile().getVirtualFile());

    ScreenView screenView = mock(ScreenView.class);
    when(screenView.getConfiguration()).thenReturn(configuration);
    when(screenView.getModel()).thenReturn(model);
    when(screenView.getScale()).thenReturn(scale);
    when(screenView.getSelectionModel()).thenReturn(selectionModel);
    when(screenView.getSize()).thenReturn(new Dimension());
    when(screenView.getSurface()).thenReturn(surface);
    when(screenView.getX()).thenReturn(x);
    when(screenView.getY()).thenReturn(y);

    when(surface.getScreenView(anyInt(), anyInt())).thenReturn(screenView);
    return screenView;
  }

  public static DesignSurface createSurface() {
    JComponent layeredPane = new JPanel();
    DesignSurface surface = mock(DesignSurface.class);
    when(surface.getLayeredPane()).thenReturn(layeredPane);
    return surface;
  }

  public static InteractionManager createManager(DesignSurface surface) {
    InteractionManager manager = new InteractionManager(surface);
    manager.registerListeners();
    return manager;
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

  /**
   * Dumps out the component tree, recursively
   *
   * @param roots set of root components
   * @return a string representation of the component tree
   */
  public static String toTree(@NotNull List<NlComponent> roots) {
    return toTree(roots, false);
  }

  /**
   * Dumps out the component tree, recursively
   *
   * @param roots           set of root components
   * @param includeIdentity if true, display an instance identifier next to each component;
   *                        these are assigned sequentially since the last call to {@link #resetComponentTestIds()}
   * @return a string representation of the component tree
   */
  public static String toTree(@NotNull List<NlComponent> roots, boolean includeIdentity) {
    StringBuilder sb = new StringBuilder(200);
    for (NlComponent root : roots) {
      describe(sb, root, 0, includeIdentity);
    }
    return sb.toString().trim();
  }

  /**
   * Reset instance id's used by {@link NlComponentTest#toTree(List, boolean)}
   */
  @VisibleForTesting
  public static void resetComponentTestIds() {
    ourComponentIds = null;
  }

  @VisibleForTesting
  private static int getInstanceId(@NotNull NlComponent root) {
    if (ourComponentIds == null) {
      ourComponentIds = Maps.newHashMap();
    }
    Integer id = ourComponentIds.get(root);
    if (id == null) {
      id = ourComponentIds.size();
      ourComponentIds.put(root, id);
    }

    return id;
  }

  private static void describe(@NotNull StringBuilder sb, @NotNull NlComponent component, int depth, boolean includeIdentity) {
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }
    sb.append(describe(component, includeIdentity));
    sb.append('\n');
    for (NlComponent child : component.getChildren()) {
      describe(sb, child, depth + 1, includeIdentity);
    }
  }

  private static String describe(@NotNull NlComponent root, boolean includeIdentity) {
    Objects.ToStringHelper helper = Objects.toStringHelper(root).omitNullValues()
      .add("tag", describe(root.getTag()))
      .add("bounds", "[" + root.x + "," + root.y + ":" + root.w + "x" + root.h);
    if (includeIdentity) {
      helper.add("instance", getInstanceId(root));
    }
    return helper.toString();
  }

  private static String describe(@Nullable XmlTag tag) {
    if (tag == null) {
      return "";
    }
    else {
      return '<' + tag.getName() + '>';
    }
  }
}
