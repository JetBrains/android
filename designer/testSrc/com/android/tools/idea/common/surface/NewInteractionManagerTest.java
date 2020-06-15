/*
 * Copyright (C) 2019 The Android Open Source Project
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

import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.flags.StudioFlags;
import com.android.tools.idea.uibuilder.surface.NlInteractionHandler;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.google.common.collect.ImmutableList;
import java.awt.Cursor;

/**
 * This class is used to test all tests in InteractionManagerTest with NELE_NEW_INTERACTION_INTERFACE enabled.
 * TODO: remove this class after StudioFlags.NELE_NEW_INTERACTION_INTERFACE is removed.
 */
public class NewInteractionManagerTest extends InteractionManagerTest {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    StudioFlags.NELE_NEW_INTERACTION_INTERFACE.override(true);
  }

  @Override
  protected void tearDown() throws Exception {
    try {
      StudioFlags.NELE_NEW_INTERACTION_INTERFACE.clearOverride();
    }
    finally {
      super.tearDown();
    }
  }

  @Override
  public void testDragAndDrop() throws Exception {
    super.testDragAndDrop();
  }

  @Override
  public void testDragAndDropWithOnCreate() throws Exception {
    super.testDragAndDropWithOnCreate();
  }

  @Override
  public void testSelectSingleComponent() {
    super.testSelectSingleComponent();
  }

  @Override
  public void testSelectDraggedComponent() {
    super.testSelectDraggedComponent();
  }

  @Override
  public void testMultiSelectComponent() {
    super.testMultiSelectComponent();
  }

  @Override
  public void testMarqueeSelect() {
    super.testMarqueeSelect();
  }

  @Override
  public void testDblClickComponentWithInvalidXmlTagInPreview() {
    super.testDblClickComponentWithInvalidXmlTagInPreview();
  }

  @Override
  public void testLinearLayoutCursorHoverComponent() {
    // TODO: replace this test to InteractionManagerTest when StudioFlags.NELE_NEW_INTERACTION_INTERFACE is removed.
    // When flag is on, it can check the return value of InteractionProvider directly rather than mocked DesignSurface.
    DesignSurface surface = setupLinearLayoutCursorTest().getSurface();
    InteractionHandler interactionHandler = new NlInteractionHandler(surface);

    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");

    int mouseX = Coordinates.getSwingXDip(screenView, textView.getCenterX());
    int mouseY = Coordinates.getSwingYDip(screenView, textView.getCenterY());
    int modifiersEx = 0;

    interactionHandler.hoverWhenNoInteraction(mouseX, mouseY, modifiersEx);
    assertEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                 interactionHandler.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx));
  }

  @Override
  public void testLinearLayoutCursorHoverComponentHandle() {
    // TODO: replace this test to InteractionManagerTest when StudioFlags.NELE_NEW_INTERACTION_INTERFACE is removed.
    // When flag is on, it can check the return value of InteractionProvider directly rather than mocked DesignSurface.
    DesignSurface surface = setupConstraintLayoutCursorTest().getSurface();
    InteractionHandler interactionHandler = new NlInteractionHandler(surface);

    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    SelectionModel selectionModel = screenView.getSelectionModel();
    selectionModel.setSelection(ImmutableList.of(textView.getNlComponent()));
    textView.layout(SceneContext.get(screenView), 0);

    int mouseX = Coordinates.getSwingXDip(screenView, textView.getDrawX() + textView.getDrawWidth());
    int mouseY = Coordinates.getSwingYDip(screenView, textView.getDrawY() + textView.getDrawHeight());
    int modifiersEx = 0;

    interactionHandler.hoverWhenNoInteraction(mouseX, mouseY, modifiersEx);
    assertEquals(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR),
                 interactionHandler.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx));
  }

  @Override
  public void testLinearLayoutCursorHoverRoot() {
    super.testLinearLayoutCursorHoverRoot();
  }

  @Override
  public void testLinearLayoutCursorHoverSceneHandle() {
    super.testLinearLayoutCursorHoverSceneHandle();
  }

  @Override
  public void testConstraintLayoutCursorHoverComponent() {
    // TODO: replace this test to InteractionManagerTest when StudioFlags.NELE_NEW_INTERACTION_INTERFACE is removed.
    // When flag is on, it can check the return value of InteractionProvider directly rather than mocked DesignSurface.
    DesignSurface surface = setupConstraintLayoutCursorTest().getSurface();
    InteractionHandler interactionHandler = new NlInteractionHandler(surface);

    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    int mouseX = Coordinates.getSwingXDip(screenView, textView.getCenterX());
    int mouseY = Coordinates.getSwingYDip(screenView, textView.getCenterY());
    int modifiersEx = 0;
    interactionHandler.hoverWhenNoInteraction(mouseX, mouseY, modifiersEx);
    assertEquals(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR),
                 interactionHandler.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx));
  }

  @Override
  public void testConstraintLayoutCursorHoverComponentHandle() {
    // TODO: replace this test to InteractionManagerTest when StudioFlags.NELE_NEW_INTERACTION_INTERFACE is removed.
    // When flag is on, it can check the return value of InteractionProvider directly rather than mocked DesignSurface.
    DesignSurface surface = setupConstraintLayoutCursorTest().getSurface();
    InteractionHandler interactionHandler = new NlInteractionHandler(surface);

    ScreenView screenView = (ScreenView)surface.getSceneView(0, 0);
    SceneComponent textView = screenView.getScene().getSceneComponent("textView");
    SelectionModel selectionModel = screenView.getSelectionModel();
    selectionModel.setSelection(ImmutableList.of(textView.getNlComponent()));
    textView.layout(SceneContext.get(screenView), 0);

    int mouseX = Coordinates.getSwingXDip(screenView, textView.getDrawX() + textView.getDrawWidth());
    int mouseY = Coordinates.getSwingYDip(screenView, textView.getDrawY() + textView.getDrawHeight());
    int modifiersEx = 0;

    interactionHandler.hoverWhenNoInteraction(mouseX, mouseY, modifiersEx);
    assertEquals(Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR),
                 interactionHandler.getCursorWhenNoInteraction(mouseX, mouseY, modifiersEx));
  }

  @Override
  public void testConstraintLayoutCursorHoverRoot() {
    super.testConstraintLayoutCursorHoverRoot();
  }

  @Override
  public void testConstraintLayoutCursorHoverSceneHandle() {
    super.testConstraintLayoutCursorHoverSceneHandle();
  }

  @Override
  public void testCursorChangeWhenSetPanningTrue() {
    super.testCursorChangeWhenSetPanningTrue();
  }

  @Override
  public void testInterceptPanOnModifiedKeyPressed() {
    super.testInterceptPanOnModifiedKeyPressed();
  }

  @Override
  public void testInterceptPanModifiedKeyReleased() {
    super.testInterceptPanModifiedKeyReleased();
  }

  @Override
  public void testIsPanningAfterMouseReleased() {
    super.testIsPanningAfterMouseReleased();
  }

  @Override
  public void testIsPanningAfterKeyReleased() {
    super.testIsPanningAfterKeyReleased();
  }

  @Override
  public void testReusingNlComponentWhenDraggingFromComponentTree() {
    super.testReusingNlComponentWhenDraggingFromComponentTree();
  }
}
