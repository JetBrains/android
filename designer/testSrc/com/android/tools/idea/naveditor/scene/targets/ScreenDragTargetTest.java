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

import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;
import static java.awt.event.MouseEvent.BUTTON1;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ScreenDragTarget}
 */
public class ScreenDragTargetTest extends NavigationTestCase {

  public void testMove() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment2")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("fragment2")),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main2"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    SceneView view = new NavView(surface, model);
    when(surface.getCurrentSceneView()).thenReturn(view);
    when(surface.getSceneView(anyInt(), anyInt())).thenReturn(view);

    Scene scene = model.getSurface().getScene();
    scene.layout(0, SceneContext.get());

    SceneComponent component = scene.getSceneComponent("fragment1");
    InteractionManager interactionManager = new InteractionManager(surface);
    try {
      interactionManager.registerListeners();

      @NavCoordinate int x = component.getDrawX();
      @NavCoordinate int y = component.getDrawY();

      LayoutTestUtilities.pressMouse(interactionManager, BUTTON1, Coordinates.getSwingX(view, x + 10),
                                     Coordinates.getSwingY(view, y + 10), 0);
      LayoutTestUtilities.dragMouse(interactionManager, Coordinates.getSwingX(view, x), Coordinates.getSwingY(view, y),
                                    Coordinates.getSwingX(view, x + 30), Coordinates.getSwingY(view, y + 40), 0);
      LayoutTestUtilities.releaseMouse(interactionManager, BUTTON1, Coordinates.getSwingX(view, x + 30),
                                       Coordinates.getSwingY(view, y + 40), 0);

      assertEquals(x + 20, component.getDrawX());
      assertEquals(y + 30, component.getDrawY());
    }
    finally {
      interactionManager.unregisterListeners();
    }
  }
}
