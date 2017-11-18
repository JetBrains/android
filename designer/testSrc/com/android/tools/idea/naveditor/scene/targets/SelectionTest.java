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
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.surface.InteractionManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;

import java.awt.event.InputEvent;

import static com.android.tools.idea.naveditor.NavModelBuilderUtil.fragmentComponent;
import static com.android.tools.idea.naveditor.NavModelBuilderUtil.rootComponent;
import static java.awt.event.MouseEvent.BUTTON1;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Test to verify that components are selected when
 * mousePress event is received.
 */
public class SelectionTest extends NavigationTestCase {

  private InteractionManager myInteractionManager;
  private SceneView mySceneView;

  public void testSelection() {
    ComponentDescriptor root = rootComponent("root")
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

    @NavCoordinate int x1 = component1.getDrawX() + component1.getDrawWidth() / 2;
    @NavCoordinate int y1 = component1.getDrawY() + component1.getDrawHeight() / 2;
    @NavCoordinate int x2 = component2.getDrawX() + component2.getDrawWidth() / 2;
    @NavCoordinate int y2 = component2.getDrawY() + component2.getDrawHeight() / 2;

    checkSelection(x1, y1, x2, y2, component1, component2);

    x1 = component1.getDrawX() + component1.getDrawWidth();
    x2 = component2.getDrawX() + component2.getDrawWidth();

    checkSelection(x1, y1, x2, y2, component1, component2);

    myInteractionManager.unregisterListeners();
  }

  private void checkSelection(@NavCoordinate int x1,
                              @NavCoordinate int y1,
                              @NavCoordinate int x2,
                              @NavCoordinate int y2,
                              SceneComponent component1,
                              SceneComponent component2) {
    select(x1, y1, false);
    assertTrue(component1.isSelected());
    assertFalse(component2.isSelected());

    select(x2, y2, false);
    assertFalse(component1.isSelected());
    assertTrue(component2.isSelected());

    select(x1, y1, true);
    assertTrue(component1.isSelected());
    assertTrue(component2.isSelected());

    select(x1, y1, true);
    assertFalse(component1.isSelected());
    assertTrue(component2.isSelected());
  }

  private void select(@NavCoordinate int x, @NavCoordinate int y, boolean shiftKey) {
    int modifiers = (shiftKey) ? InputEvent.SHIFT_DOWN_MASK : 0;

    LayoutTestUtilities.pressMouse(myInteractionManager, BUTTON1, Coordinates.getSwingX(mySceneView, x),
                                   Coordinates.getSwingY(mySceneView, y), modifiers);

    LayoutTestUtilities.releaseMouse(myInteractionManager, BUTTON1, Coordinates.getSwingX(mySceneView, x),
                                     Coordinates.getSwingY(mySceneView, y), modifiers);
  }
}
