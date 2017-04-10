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
package com.android.tools.idea.naveditor.scene.layout;

import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import org.jetbrains.android.dom.navigation.NavigationSchema;

import static com.android.SdkConstants.TOOLS_URI;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ManualLayoutAlgorithm}
 */
public class ManualLayoutAlgorithmTest extends NavigationTestCase {

  public void testSimple() throws Exception {
    SyncNlModel model = model("nav.xml",
                              component(NavigationSchema.TAG_NAVIGATION).unboundedChildren(
                                   component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment1")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_X, "123dp")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_Y, "456dp"),
                                   component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment2")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_X, "456dp")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_Y, "789dp"))).build();
    Scene scene = model.getSurface().getScene();
    NavSceneLayoutAlgorithm fallback = mock(NavSceneLayoutAlgorithm.class);
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(fallback, NavigationSchema.getOrCreateSchema(myAndroidFacet));
    scene.getRoot().flatten().forEach(algorithm::layout);
    verifyZeroInteractions(fallback);

    assertEquals(123, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(456, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(456, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(789, scene.getSceneComponent("fragment2").getDrawY());
  }

  public void testFallback() throws Exception {
    SyncNlModel model = model("nav.xml",
                              component(NavigationSchema.TAG_NAVIGATION).unboundedChildren(
                                   component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment1")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_X, "60dp")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_Y, "60dp"),
                                   component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment2"),
                                   component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment3")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_X, "200dp")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_Y, "200dp"))).build();
    Scene scene = model.getSurface().getScene();
    NavSceneLayoutAlgorithm fallback = mock(NavSceneLayoutAlgorithm.class);
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(fallback, NavigationSchema.getOrCreateSchema(myAndroidFacet));
    scene.getRoot().flatten().forEach(algorithm::layout);
    verify(fallback).layout(scene.getSceneComponent("fragment2"));
    verifyNoMoreInteractions(fallback);

    assertEquals(60, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(60, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(200, scene.getSceneComponent("fragment3").getDrawX());
    assertEquals(200, scene.getSceneComponent("fragment3").getDrawY());
  }

  public void testSave() throws Exception {
    SyncNlModel model = model("nav.xml",
                              component(NavigationSchema.TAG_NAVIGATION).unboundedChildren(
                                   component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment1")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_X, "60dp")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_Y, "60dp"),
                                   component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment2"),
                                   component(NavigationSchema.TAG_FRAGMENT).id("@id/fragment3")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_X, "200dp")
                                     .withAttribute(TOOLS_URI, ManualLayoutAlgorithm.ATTR_Y, "200dp"))).build();
    Scene scene = model.getSurface().getScene();
    NavSceneLayoutAlgorithm fallback = mock(NavSceneLayoutAlgorithm.class);
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(fallback, NavigationSchema.getOrCreateSchema(myAndroidFacet));
    scene.getRoot().flatten().forEach(algorithm::layout);
    SceneComponent fragment = scene.getSceneComponent("fragment2");
    fragment.setPosition(150, 160);
    algorithm.save(fragment);
    fragment = scene.getSceneComponent("fragment3");
    fragment.setPosition(250, 260);
    algorithm.save(fragment);

    assertEquals("<navigation xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                 "           xmlns:tools=\"http://schemas.android.com/tools\">\n" +
                 "\n" +
                 "  <fragment\n" +
                 "    android:id=\"@id/fragment1\"\n" +
                 "    tools:manual_x=\"60dp\"\n" +
                 "    tools:manual_y=\"60dp\"/>\n" +
                 "\n" +
                 "  <fragment\n" +
                 "    android:id=\"@id/fragment2\" tools:manual_x=\"150dp\" tools:manual_y=\"160dp\" />\n" +
                 "\n" +
                 "  <fragment\n" +
                 "    android:id=\"@id/fragment3\"\n" +
                 "    tools:manual_x=\"250dp\"\n" +
                 "    tools:manual_y=\"260dp\"/>\n" +
                 "\n" +
                 "</navigation>\n", model.getFile().getText());
  }
}
