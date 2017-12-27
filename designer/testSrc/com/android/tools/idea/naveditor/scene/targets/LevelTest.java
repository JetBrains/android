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
package com.android.tools.idea.naveditor.scene.targets;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.AndroidDpCoordinate;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.decorator.SceneDecorator;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.scene.draw.DrawCommand;
import com.android.tools.idea.common.surface.InteractionManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;

import java.util.ArrayList;

import static java.awt.event.MouseEvent.BUTTON1;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Test to verify that components are selected when
 * mousePress event is received.
 */
public class LevelTest extends NavigationTestCase {
  private static int DRAG = 30;

  private InteractionManager myInteractionManager;
  private SceneView mySceneView;

  public void testLevels() throws Exception {
    ComponentDescriptor root = rootComponent()
      .unboundedChildren(
        fragmentComponent("fragment1"),
        fragmentComponent("fragment2"));

    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    mySceneView = new NavView(surface, model);
    when(surface.getCurrentSceneView()).thenReturn(mySceneView);
    when(surface.getSceneView(anyInt(), anyInt())).thenReturn(mySceneView);

    Scene scene = model.getSurface().getScene();
    scene.layout(0, SceneContext.get());

    myInteractionManager = new InteractionManager(surface);
    myInteractionManager.registerListeners();

    SceneComponent component1 = scene.getSceneComponent("fragment1");
    SceneComponent component2 = scene.getSceneComponent("fragment2");

    @AndroidDpCoordinate int x1 = component1.getDrawX() + component1.getDrawWidth() / 2;
    @AndroidDpCoordinate int y1 = component1.getDrawY() + component1.getDrawHeight() / 2;

    @AndroidDpCoordinate int x2 = component2.getDrawX() + component2.getDrawWidth() / 2;
    @AndroidDpCoordinate int y2 = component2.getDrawY() + component2.getDrawHeight() / 2;

    checkSelections(x1, y1, x2, y2, component1, component2);

    x1 = component1.getDrawX() + component1.getDrawWidth();
    x2 = component2.getDrawX() + component2.getDrawWidth();

    checkSelections(x1, y1, x2, y2, component1, component2);

    myInteractionManager.unregisterListeners();
  }

  private void checkSelections(@AndroidDpCoordinate int x1,
                               @AndroidDpCoordinate int y1,
                               @AndroidDpCoordinate int x2,
                               @AndroidDpCoordinate int y2,
                               SceneComponent component1,
                               SceneComponent component2) {
    mouseDown(x1, y1);
    checkLevel(component2, component1);
    mouseUp(x1, y1);
    checkLevel(component2, component1);

    mouseDown(x2, y2);
    checkLevel(component1, component2);
    mouseUp(x2, y2);
    checkLevel(component1, component2);
  }

  private void mouseDown(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    LayoutTestUtilities.pressMouse(myInteractionManager, BUTTON1, Coordinates.getSwingXDip(mySceneView, x),
                                   Coordinates.getSwingYDip(mySceneView, y), 0);

    LayoutTestUtilities.dragMouse(myInteractionManager, x, y, x + DRAG, y + DRAG, 0);
    LayoutTestUtilities.dragMouse(myInteractionManager, x + DRAG, y + DRAG, x, y, 0);
  }

  private void mouseUp(@AndroidDpCoordinate int x, @AndroidDpCoordinate int y) {
    LayoutTestUtilities.releaseMouse(myInteractionManager, BUTTON1, Coordinates.getSwingXDip(mySceneView, x),
                                     Coordinates.getSwingYDip(mySceneView, y), 0);
  }

  private static void checkLevel(SceneComponent lower, SceneComponent higher) {
    int level1 = getLevel(lower);
    int level2 = getLevel(higher);
    assertTrue(level2 > level1);
  }

  private static int getLevel(SceneComponent component) {
    SceneDecorator decorator = component.getDecorator();
    DisplayList displayList = new DisplayList();
    decorator.buildList(displayList, 0, SceneContext.get(), component);

    ArrayList<DrawCommand> commands = displayList.getCommands();
    assertEquals(1, commands.size());
    return commands.get(0).getLevel();
  }
}
