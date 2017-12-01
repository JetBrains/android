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
import com.android.tools.idea.common.model.Coordinates;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.surface.InteractionManager;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;

import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;
import static java.awt.event.MouseEvent.BUTTON1;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ActionTarget}
 */
public class ActionTargetTest extends NavTestCase {
  public void testSelect() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("fragment2")),
        fragmentComponent("fragment2"));
    SyncNlModel model = model("nav.xml", root).build();
    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    SceneView view = new NavView(surface, model);
    when(surface.getCurrentSceneView()).thenReturn(view);
    when(surface.getSceneView(anyInt(), anyInt())).thenReturn(view);

    Scene scene = model.getSurface().getScene();
    SceneComponent component = scene.getSceneComponent("fragment1");
    SceneComponent component2 = scene.getSceneComponent("fragment2");

    component.setPosition(0, 0);
    component2.setPosition(500, 0);

    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(new DisplayList(), 0, view);


    InteractionManager interactionManager = new InteractionManager(surface);
    interactionManager.registerListeners();

    LayoutTestUtilities.clickMouse(interactionManager, BUTTON1, 1, Coordinates.getSwingXDip(view, 300),
                                   Coordinates.getSwingYDip(view, component.getCenterY()), 0);

    assertEquals(model.find("action1"), surface.getSelectionModel().getPrimary());
    interactionManager.unregisterListeners();
  }
}
