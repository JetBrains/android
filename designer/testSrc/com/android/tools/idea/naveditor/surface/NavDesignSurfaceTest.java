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
package com.android.tools.idea.naveditor.surface;

import com.android.SdkConstants;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.model.Coordinates;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.surface.InteractionManager;
import com.android.tools.idea.uibuilder.surface.SceneView;
import com.intellij.openapi.fileEditor.FileEditorManager;
import org.jetbrains.android.dom.navigation.NavigationSchema;

import javax.swing.*;
import java.awt.event.MouseEvent;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link NavDesignSurface}
 */
public class NavDesignSurfaceTest extends NavigationTestCase {

  public void testComponentActivated() throws Exception {
    NavDesignSurface surface = new NavDesignSurface(myAndroidFacet, getTestRootDisposable());
    SyncNlModel model = model("nav.xml", component(NavigationSchema.TAG_NAVIGATION)
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main"),
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2"))
    ).build();
    surface.setModel(model);
    surface.notifyComponentActivate(model.find("fragment1"));
    FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
    assertEquals("activity_main.xml", editorManager.getOpenFiles()[0].getName());
    editorManager.closeFile(editorManager.getOpenFiles()[0]);
    surface.notifyComponentActivate(model.find("fragment2"));
    assertEquals("activity_main2.xml", editorManager.getOpenFiles()[0].getName());
  }

  public void testDoubleClickFragment() throws Exception {
    SyncNlModel model = model("nav.xml", component(NavigationSchema.TAG_NAVIGATION)
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main"),
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2"))
    ).build();

    NavDesignSurface surface = (NavDesignSurface)model.getSurface();
    when(surface.getLayeredPane()).thenReturn(mock(JComponent.class));
    InteractionManager interactionManager = new InteractionManager(surface);
    interactionManager.registerListeners();

    SceneView view = new NavView(surface, model);
    when(surface.getSceneView(anyInt(), anyInt())).thenReturn(view);

    surface.getScene().layout(0, SceneContext.get(view));
    SceneComponent fragment = surface.getScene().getSceneComponent("fragment1");
    int x = Coordinates.getSwingXDip(view, fragment.getDrawX()) + 5;
    int y = Coordinates.getSwingXDip(view, fragment.getDrawY()) + 5;
    LayoutTestUtilities.clickMouse(interactionManager, MouseEvent.BUTTON1, 2, x, y, 0);

    verify(surface).notifyComponentActivate(eq(fragment.getNlComponent()), anyInt(), anyInt());
  }
}
