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
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import org.jetbrains.android.dom.navigation.NavigationSchema;

/**
 * Tests for the nav editor Scene.
 */
public class NavSceneTest extends NavigationTestCase {

  public void testDisplayList() {
    ComponentDescriptor root = component(NavigationSchema.TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment1")
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/subnav"),
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@id/activity")
          ),
        component(NavigationSchema.TAG_NAVIGATION).id("@+id/subnav")
          .unboundedChildren(
            component(NavigationSchema.TAG_FRAGMENT)
              .id("@+id/fragment2")
              .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2")
              .unboundedChildren(component(NavigationSchema.TAG_ACTION)
                                   .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@id/activity"))),
        component("activity").id("@+id/activity"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,0,0\n" +
                 "DrawNavScreen,51,51,199,332\n" +
                 "DrawComponentFrame,50,50,200,333,1,false\n" +
                 "DrawAction,NORMAL,50x50x200x333,50x440x140x34,NORMAL\n" +
                 "DrawAction,NORMAL,50x50x200x333,50x440x200x333,NORMAL\n" +
                 "DrawAction,NORMAL,-170x50x200x333,50x50x200x333,NORMAL\n" +
                 "DrawTextRegion,50,440,140,34,0,20,true,false,4,4,14,1.0,\"navigation\"\n" +
                 "DrawComponentFrame,50,440,140,34,1,true\n" +
                 "DrawAction,NORMAL,50x440x140x34,50x440x200x333,NORMAL\n" +
                 "DrawComponentFrame,50,440,200,333,1,false\n" +
                 "UNClip\n", list.serialize());
  }

  public void testAddComponent() {
    ComponentDescriptor root = component(NavigationSchema.TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment2")
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
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());

    root.addChild(component(NavigationSchema.TAG_FRAGMENT).id("@+id/fragment3"), null);
    modelBuilder.updateModel(model);
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,0,0\n" +
                 "DrawNavScreen,51,441,199,332\n" +
                 "DrawComponentFrame,50,440,200,333,1,false\n" +
                 "DrawAction,NORMAL,50x440x200x333,50x830x200x333,NORMAL\n" +
                 "DrawNavScreen,51,831,199,332\n" +
                 "DrawComponentFrame,50,830,200,333,1,false\n" +
                 "DrawAction,NORMAL,-170x830x200x333,50x830x200x333,NORMAL\n" +
                 "DrawComponentFrame,50,50,200,333,1,false\n" +
                 "UNClip\n", list.serialize());
  }

  public void testRemoveComponent() {
    ComponentDescriptor fragment2 = component(NavigationSchema.TAG_FRAGMENT)
      .id("@+id/fragment2")
      .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2");
    ComponentDescriptor root = component(NavigationSchema.TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment2")
      .unboundedChildren(
        component(NavigationSchema.TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")),
        fragment2);
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());

    root.removeChild(fragment2);
    modelBuilder.updateModel(model);
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,0,0\n" +
                 "DrawNavScreen,51,51,199,332\n" +
                 "DrawComponentFrame,50,50,200,333,1,false\n" +
                 "UNClip\n", list.serialize());
  }
}
