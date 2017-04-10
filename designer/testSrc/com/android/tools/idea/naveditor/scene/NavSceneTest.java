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
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import org.jetbrains.android.dom.navigation.NavigationSchema;

/**
 * Tests for the nav editor Scene.
 */
public class NavSceneTest extends NavigationTestCase {

  public void testDisplayList() {
    SyncNlModel model = model("nav.xml", component(NavigationSchema.TAG_NAVIGATION)
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")
          ),
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment2"))
    ).build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.buildDisplayList(list, 0);
    assertEquals("Clip,0,0,0,0\n" +
                 "DrawComponentBackground,50,50,50,50,1\n" +
                 "DrawNavScreen,50,50,50,50,fragment1\n" +
                 "DrawComponentFrame,50,50,50,50,1\n" +
                 "DrawAction,NORMAL,50x50x50x50,50x180x50x50,NORMAL\n" +
                 "DrawComponentBackground,50,180,50,50,1\n" +
                 "DrawNavScreen,50,180,50,50,fragment2\n" +
                 "DrawComponentFrame,50,180,50,50,1\n" +
                 "UNClip\n", list.serialize());
  }
}
