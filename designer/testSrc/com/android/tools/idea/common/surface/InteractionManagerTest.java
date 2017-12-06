/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.common.surface;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.api.ViewEditor;
import com.android.tools.idea.uibuilder.handlers.ImageViewHandler;
import com.android.tools.idea.uibuilder.handlers.ViewHandlerManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.util.Disposer;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mockito;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.*;

/**
 * TODO: remove layout-specific stuff, add generic tests.
 */
public class InteractionManagerTest extends LayoutTestCase {

  public void testDragAndDrop() throws Exception {
    // Drops a fragment (xmlFragment below) into the design surface (via drag & drop events) and verifies that
    // the resulting document ends up modified as expected.
    SyncNlModel model = model("test.xml", component(LINEAR_LAYOUT)
      .withAttribute(ATTR_ORIENTATION, VALUE_VERTICAL)
      .withBounds(0, 0, 100, 100)).build();

    ScreenView screenView = createScreen(model);

    DesignSurface designSurface = screenView.getSurface();
    InteractionManager manager = createManager(designSurface);

    @Language("XML")
    String xmlFragment = "" +
                         "<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                         "     android:id=\"@+id/textView\"\n" +
                         "     android:layout_width=\"wrap_content\"\n" +
                         "     android:layout_height=\"wrap_content\"\n" +
                         "     android:text=\"Hello World\"\n" +
                         "/>";
    Transferable transferable = createTransferable(DataFlavor.stringFlavor, xmlFragment);
    dragDrop(manager, 0, 0, 100, 100, transferable);
    Disposer.dispose(model);

    String expected = "NlComponent{tag=<LinearLayout>, bounds=[0,100:2x2, instance=0}\n" +
                      "    NlComponent{tag=<TextView>, bounds=[0,100:2x2, instance=1}";
    assertEquals(expected, new NlTreeDumper().toTree(model.getComponents()));
    assertEquals("Hello World", model.find("textView").getAttribute(ANDROID_URI, ATTR_TEXT));
  }

  public void testDragAndDropWithOnCreate() throws Exception {
    // Drops an ImageView and verifies that onCreate was called.
    ViewHandlerManager viewManager = ViewHandlerManager.get(myFacet);
    viewManager.registerHandler(IMAGE_VIEW, new FakeImageViewHandler());

    SyncNlModel model = model("test.xml", component(LINEAR_LAYOUT)
      .withAttribute(ATTR_ORIENTATION, VALUE_VERTICAL)
      .withBounds(0, 0, 100, 100)).build();

    ScreenView screenView = createScreen(model);

    DesignSurface designSurface = screenView.getSurface();
    InteractionManager manager = createManager(designSurface);

    @Language("XML")
    String xmlFragment = "" +
                         "<ImageView xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                         "     android:layout_width=\"wrap_content\"\n" +
                         "     android:layout_height=\"wrap_content\"\n" +
                         "/>";
    Transferable transferable = createTransferable(DataFlavor.stringFlavor, xmlFragment);
    dragDrop(manager, 0, 0, 100, 100, transferable);
    Disposer.dispose(model);

    String expected = "NlComponent{tag=<LinearLayout>, bounds=[0,100:2x2, instance=0}\n" +
                      "    NlComponent{tag=<ImageView>, bounds=[0,100:2x2, instance=1}";
    assertEquals(expected, new NlTreeDumper().toTree(model.getComponents()));
    assertEquals("@android:drawable/selected_image", model.find("imageView").getAttribute(ANDROID_URI, ATTR_SRC));
  }

  public void testSelectSingleComponent() {
    InteractionManager manager = setupLinearLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    LayoutTestUtilities.clickMouse(manager, MouseEvent.BUTTON1, 1,
                                   Coordinates.getSwingXDip(screenView, textView.getCenterX()),
                                   Coordinates.getSwingYDip(screenView, textView.getCenterY()), 0);
    ImmutableList<NlComponent> selections = surface.getSelectionModel().getSelection();
    assertEquals(1, selections.size());
    assertEquals(textView.getNlComponent(), selections.get(0));
  }

  public void testMultiSelectComponent() {
    InteractionManager manager = setupLinearLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);

    surface.getSelectionModel().clear();
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    LayoutTestUtilities.clickMouse(manager, MouseEvent.BUTTON1, 1,
                                   Coordinates.getSwingXDip(screenView, textView.getCenterX()),
                                   Coordinates.getSwingYDip(screenView, textView.getCenterY()), 0);

    SceneComponent button = screenView.getScene().getSceneComponent("button");
    LayoutTestUtilities.clickMouse(manager, MouseEvent.BUTTON1, 1,
                                   Coordinates.getSwingXDip(screenView, button.getCenterX()),
                                   Coordinates.getSwingYDip(screenView, button.getCenterY()), InputEvent.SHIFT_DOWN_MASK);

    ImmutableList<NlComponent> selections = surface.getSelectionModel().getSelection();
    assertEquals(2, selections.size());
    assertEquals(textView.getNlComponent(), selections.get(0));
    assertEquals(button.getNlComponent(), selections.get(1));
  }

  public void testMarqueeSelect() {
    InteractionManager manager = setupLinearLayoutCursorTest();
    DesignSurface surface = manager.getSurface();

    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);

    SceneComponent button = screenView.getScene().getSceneComponent("button");
    int startX = -20;
    int startY = -20;

    int endX = Coordinates.getSwingXDip(screenView, button.getDrawX()) + 3;
    int endY = Coordinates.getSwingXDip(screenView, button.getDrawY()) + 3;
    LayoutTestUtilities.pressMouse(manager, MouseEvent.BUTTON1, startX, startY, 0);
    LayoutTestUtilities.dragMouse(manager, startX, startY, endX, endY, 0);
    LayoutTestUtilities.releaseMouse(manager, MouseEvent.BUTTON1, endX, endY, 0);

    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    ImmutableList<NlComponent> selections = surface.getSelectionModel().getSelection();
    assertEquals(ImmutableList.of(textView.getNlComponent(), button.getNlComponent()), selections);

    surface.getSelectionModel().clear();

    startX = Coordinates.getSwingXDip(screenView, button.getDrawX() + button.getDrawWidth() + 20);
    startY = Coordinates.getSwingYDip(screenView, button.getDrawY() + button.getDrawHeight() + 20);
    endX = Coordinates.getSwingXDip(screenView, button.getDrawX() + button.getDrawWidth()) - 3;
    endY = Coordinates.getSwingXDip(screenView, button.getDrawY() + button.getDrawHeight()) - 3;
    LayoutTestUtilities.pressMouse(manager, MouseEvent.BUTTON1, startX, startY, 0);
    LayoutTestUtilities.dragMouse(manager, startX, startY, endX, endY, 0);
    LayoutTestUtilities.releaseMouse(manager, MouseEvent.BUTTON1, endX, endY, 0);

    selections = surface.getSelectionModel().getSelection();
    assertEquals(ImmutableList.of(button.getNlComponent()), selections);

    manager.stopListening();
    Disposer.dispose(surface);
  }

  public void testLinearLayoutCursorHoverComponent() {
    InteractionManager manager = setupLinearLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    manager.updateCursor(Coordinates.getSwingXDip(screenView, textView.getCenterX()),
                         Coordinates.getSwingYDip(screenView, textView.getCenterY()));
    Mockito.verify(surface).setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  public void testLinearLayoutCursorHoverComponentHandle() {
    InteractionManager manager = setupLinearLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    SelectionModel selectionModel = screenView.getSelectionModel();
    selectionModel.setSelection(ImmutableList.of(textView.getNlComponent()));
    selectionModel.getHandles(textView.getNlComponent());
    manager.updateCursor(Coordinates.getSwingXDip(screenView, textView.getDrawX() + textView.getDrawWidth()),
                         Coordinates.getSwingYDip(screenView, textView.getDrawY() + textView.getDrawHeight()));
    Mockito.verify(surface).setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
  }

  public void testLinearLayoutCursorHoverRoot() {
    InteractionManager manager = setupLinearLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    manager.updateCursor(Coordinates.getSwingXDip(screenView, textView.getDrawHeight() + textView.getDrawY() + 20),
                         Coordinates.getSwingYDip(screenView, textView.getCenterY()));
    Mockito.verify(surface).setCursor(null);
  }

  public void testLinearLayoutCursorHoverSceneHandle() {
    InteractionManager manager = setupLinearLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    Mockito.when(((NlDesignSurface)surface).hasCustomDevice()).thenReturn(true);
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    manager.updateCursor(screenView.getX() + screenView.getSize().width,
                         screenView.getY() + screenView.getSize().height);
    Mockito.verify(surface).setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
  }

  private InteractionManager setupLinearLayoutCursorTest() {
    SyncNlModel model = model("test.xml", component(LINEAR_LAYOUT)
      .withAttribute(ATTR_ORIENTATION, VALUE_VERTICAL)
      .withBounds(0, 0, 100, 100)
      .children(
        component(TEXT_VIEW)
          .withBounds(0, 0, 50, 50)
          .id("@+id/textView")
          .text("Hello World")
          .wrapContentWidth()
          .wrapContentHeight(),
        component(BUTTON)
          .id("@+id/button")
          .withBounds(50, 50, 50, 50)
          .text("Button")
          .wrapContentWidth()
          .wrapContentHeight()
        )).build();

    NlDesignSurface surface = (NlDesignSurface)model.getSurface();
    surface.getScene().buildDisplayList(new DisplayList(), 0);
    return createManager(surface);
  }

  public void testConstraintLayoutCursorHoverComponent() {
    InteractionManager manager = setupConstraintLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    manager.updateCursor(Coordinates.getSwingXDip(screenView, textView.getCenterX()),
                         Coordinates.getSwingYDip(screenView, textView.getCenterY()));
    Mockito.verify(surface).setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
  }

  public void testConstraintLayoutCursorHoverComponentHandle() {
    InteractionManager manager = setupConstraintLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    SelectionModel selectionModel = screenView.getSelectionModel();
    selectionModel.setSelection(ImmutableList.of(textView.getNlComponent()));
    selectionModel.getHandles(textView.getNlComponent());
    manager.updateCursor(Coordinates.getSwingXDip(screenView, textView.getDrawX() + textView.getDrawWidth()),
                         Coordinates.getSwingYDip(screenView, textView.getDrawY() + textView.getDrawHeight()));
    Mockito.verify(surface).setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
  }

  public void testConstraintLayoutCursorHoverRoot() {
    InteractionManager manager = setupConstraintLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    manager.updateCursor(Coordinates.getSwingXDip(screenView, textView.getDrawHeight() + textView.getDrawY() + 20),
                         Coordinates.getSwingYDip(screenView, textView.getCenterY()));
    Mockito.verify(surface).setCursor(null);
  }

  public void testConstraintLayoutCursorHoverSceneHandle() {
    InteractionManager manager = setupConstraintLayoutCursorTest();
    DesignSurface surface = manager.getSurface();
    Mockito.when(((NlDesignSurface)surface).hasCustomDevice()).thenReturn(true);
    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    manager.updateCursor(screenView.getX() + screenView.getSize().width,
                         screenView.getY() + screenView.getSize().height);
    Mockito.verify(surface).setCursor(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR));
  }

  private InteractionManager setupConstraintLayoutCursorTest() {
    SyncNlModel model = model("constraint.xml", component(CONSTRAINT_LAYOUT.defaultName())
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(TEXT_VIEW)
          .id("@+id/textView")
          .withBounds(0, 0, 100, 100)
          .wrapContentWidth()
          .wrapContentHeight())).build();

    NlDesignSurface surface = (NlDesignSurface)model.getSurface();
    Mockito.when(surface.getScale()).thenReturn(1.0);
    surface.getScene().buildDisplayList(new DisplayList(), 0);
    return createManager(surface);
  }

  private static class FakeImageViewHandler extends ImageViewHandler {
    @Override
    public boolean onCreate(@NotNull ViewEditor editor,
                            @Nullable NlComponent parent,
                            @NotNull NlComponent newChild,
                            @NotNull InsertType insertType) {
      if (insertType == InsertType.CREATE) { // NOT InsertType.CREATE_PREVIEW
        setSrcAttribute(newChild, "@android:drawable/selected_image");
      }
      else {
        setSrcAttribute(newChild, "@android:drawable/btn_star");
      }
      return true;
    }
  }
}