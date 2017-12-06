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
package com.android.tools.idea.naveditor.actions;

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.surface.InteractionManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.model.NavCoordinate;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;

import java.awt.*;

import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;
import static java.awt.event.MouseEvent.BUTTON1;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

public class DragCreateActionTest extends NavTestCase {
  private static final String FRAGMENT1 = "fragment1";
  private static final String FRAGMENT2 = "fragment2";
  private static final String ACTION = "action1";

  public void testDragCreateToSelf() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root").unboundedChildren(
                                fragmentComponent(FRAGMENT1))).build();


    NavDesignSurface surface = initializeNavDesignSurface(model);
    Scene scene = initializeScene(surface);
    InteractionManager interactionManager = initializeInteractionManager(surface);

    SceneComponent component = scene.getSceneComponent(FRAGMENT1);

    dragFromActionHandle(interactionManager, component, component.getCenterX(), component.getCenterY(), surface.getCurrentSceneView());

    String expected = "NlComponent{tag=<navigation>, instance=0}\n" +
                      "    NlComponent{tag=<fragment>, instance=1}\n" +
                      "        NlComponent{tag=<action>, instance=2}";

    verifyModel(model, expected);
  }

  public void testDragCreateToOtherFragment() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root").unboundedChildren(
                                fragmentComponent(FRAGMENT1),
                                fragmentComponent(FRAGMENT2).unboundedChildren(
                                  actionComponent(ACTION).withDestinationAttribute(FRAGMENT1)))).build();

    NavDesignSurface surface = initializeNavDesignSurface(model);
    Scene scene = initializeScene(surface);
    InteractionManager interactionManager = initializeInteractionManager(surface);

    SceneComponent component = scene.getSceneComponent(FRAGMENT2);
    dragFromActionHandle(interactionManager, scene.getSceneComponent(FRAGMENT1), component.getCenterX(), component.getCenterY(),
                         surface.getCurrentSceneView());

    String expected = "NlComponent{tag=<navigation>, instance=0}\n" +
                      "    NlComponent{tag=<fragment>, instance=1}\n" +
                      "        NlComponent{tag=<action>, instance=2}\n" +
                      "    NlComponent{tag=<fragment>, instance=3}\n" +
                      "        NlComponent{tag=<action>, instance=4}";

    verifyModel(model, expected);
  }

  public void testDragCreateToInclude() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root").unboundedChildren(
                                fragmentComponent(FRAGMENT1),
                                includeComponent("navigation"))).build();

    NavDesignSurface surface = initializeNavDesignSurface(model);
    Scene scene = initializeScene(surface);
    InteractionManager interactionManager = initializeInteractionManager(surface);

    SceneComponent component = scene.getSceneComponent("nav");
    dragFromActionHandle(interactionManager, scene.getSceneComponent(FRAGMENT1), component.getCenterX(), component.getCenterY(),
                         surface.getCurrentSceneView());

    String expected = "NlComponent{tag=<navigation>, instance=0}\n" +
                      "    NlComponent{tag=<fragment>, instance=1}\n" +
                      "        NlComponent{tag=<action>, instance=2}\n" +
                      "    NlComponent{tag=<include>, instance=3}";

    verifyModel(model, expected);
  }

  public void testDragAbandon() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root").unboundedChildren(
                                fragmentComponent(FRAGMENT1))).build();

    NavDesignSurface surface = initializeNavDesignSurface(model);
    Scene scene = initializeScene(surface);
    InteractionManager interactionManager = initializeInteractionManager(surface);

    SceneComponent root = scene.getRoot();
    Rectangle rootRect = root.fillRect(null);

    SceneComponent component = scene.getSceneComponent(FRAGMENT1);
    Rectangle componentRect = component.fillRect(null);

    // make sure the top of the component is lower than the top of the root
    assertTrue(rootRect.y < componentRect.y);

    // drag release to a point over the root and verify no action is created
    dragFromActionHandle(interactionManager, component, component.getCenterX(), (rootRect.y + componentRect.y) / 2,
                         surface.getCurrentSceneView());

    String expected = "NlComponent{tag=<navigation>, instance=0}\n" +
                      "    NlComponent{tag=<fragment>, instance=1}";

    verifyModel(model, expected);
  }

  private static NavDesignSurface initializeNavDesignSurface(SyncNlModel model) {
    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    SceneView view = new NavView(surface, surface.getSceneManager());
    when(surface.getCurrentSceneView()).thenReturn(view);
    when(surface.getSceneView(anyInt(), anyInt())).thenReturn(view);
    return surface;
  }

  private static Scene initializeScene(NavDesignSurface surface) {
    Scene scene = surface.getScene();
    scene.layout(0, SceneContext.get());
    return scene;
  }

  private static InteractionManager initializeInteractionManager(NavDesignSurface surface) {
    InteractionManager interactionManager = new InteractionManager(surface);
    interactionManager.startListening();
    return interactionManager;
  }

  private static void dragFromActionHandle(InteractionManager interactionManager,
                                           SceneComponent component,
                                           @NavCoordinate int x,
                                           @NavCoordinate int y,
                                           SceneView view) {
    Rectangle rect = Coordinates.getSwingRect(view, component.fillRect(null));
    LayoutTestUtilities
      .pressMouse(interactionManager, BUTTON1, rect.x + rect.width, Coordinates.getSwingY(view, component.getCenterY()), 0);
    LayoutTestUtilities.releaseMouse(interactionManager, BUTTON1, Coordinates.getSwingX(view, x), Coordinates.getSwingY(view, y), 0);
  }

  private static void verifyModel(SyncNlModel model, String expected) {
    String tree = new NlTreeDumper().toTree(model.getComponents());
    assertEquals(expected, tree);
  }
}
