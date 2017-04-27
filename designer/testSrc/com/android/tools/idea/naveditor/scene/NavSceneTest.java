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
package com.android.tools.idea.naveditor.scene;

import com.android.SdkConstants;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.testFramework.ThreadTracker;
import org.jetbrains.android.dom.navigation.NavigationSchema;

/**
 * Tests for the nav editor Scene.
 */
public class NavSceneTest extends NavigationTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    // temporarily disable this until we figure out why it's happening
    ThreadTracker.longRunningThreadCreated(ApplicationManager.getApplication(), "google-crash-pool");
  }

  public void testDisplayList() {
    SyncNlModel model = model("nav.xml", component(NavigationSchema.TAG_NAVIGATION)
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")
          ),
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2"))
    ).build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,0,0\n" +
                 "DrawNavScreen,51,441,199,332\n" +
                 "DrawComponentFrame,50,440,200,333,1\n" +
                 "DrawAction,NORMAL,50x440x200x333,50x50x200x333,NORMAL\n" +
                 "DrawNavScreen,51,51,199,332\n" +
                 "DrawComponentFrame,50,50,200,333,1\n" +
                 "UNClip\n", list.serialize());
  }
}
