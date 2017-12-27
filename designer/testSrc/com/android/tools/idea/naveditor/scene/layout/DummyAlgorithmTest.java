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
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import org.jetbrains.android.dom.navigation.NavigationSchema;

/**
 * Tests for {@link DummyAlgorithm}
 */
public class DummyAlgorithmTest extends NavigationTestCase {
  public void testSimple() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent().unboundedChildren(
                                fragmentComponent("fragment1"),
                                fragmentComponent("fragment2"),
                                fragmentComponent("fragment3")
                                  .unboundedChildren(actionComponent("action1")),
                                fragmentComponent("fragment4"))).build();
    Scene scene = model.getSurface().getScene();
    SceneComponent root = scene.getRoot();
    root.setSize(1000, 1000, false);
    DummyAlgorithm algorithm = new DummyAlgorithm(NavigationSchema.getOrCreateSchema(myAndroidFacet));
    root.flatten().forEach(c -> c.setPosition(0, 0));
    root.flatten().forEach(algorithm::layout);

    assertEquals(310, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(570, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment2").getDrawY());
    assertEquals(50, scene.getSceneComponent("fragment3").getDrawX());
    assertEquals(440, scene.getSceneComponent("fragment3").getDrawY());
    assertEquals(50, scene.getSceneComponent("fragment4").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment4").getDrawY());
  }

  public void testSkipOther() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent().unboundedChildren(
                                fragmentComponent("fragment1"),
                                fragmentComponent("fragment2"),
                                fragmentComponent("fragment3")
                                  .unboundedChildren(actionComponent("action2")),
                                fragmentComponent("fragment4"),
                                fragmentComponent("fragment5"),
                                fragmentComponent("fragment6"),
                                fragmentComponent("fragment7"))).build();
    Scene scene = model.getSurface().getScene();
    SceneComponent root = scene.getRoot();
    root.setSize(1000, 1000, false);
    DummyAlgorithm algorithm = new DummyAlgorithm(NavigationSchema.getOrCreateSchema(myAndroidFacet));
    SceneComponent manual = scene.getSceneComponent("fragment1");
    root.flatten().forEach(c -> c.setPosition(0, 0));
    manual.setPosition(400, 300);
    root.flatten().filter(c -> c != manual).forEach(algorithm::layout);

    assertEquals(400, manual.getDrawX());
    assertEquals(300, manual.getDrawY());

    assertEquals(50, scene.getSceneComponent("fragment7").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment7").getDrawY());
    assertEquals(700, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(50, scene.getSceneComponent("fragment2").getDrawY());
    assertEquals(50, scene.getSceneComponent("fragment3").getDrawX());
    assertEquals(440, scene.getSceneComponent("fragment3").getDrawY());
    assertEquals(700, scene.getSceneComponent("fragment4").getDrawX());
    assertEquals(440, scene.getSceneComponent("fragment4").getDrawY());
    assertEquals(310, scene.getSceneComponent("fragment5").getDrawX());
    assertEquals(700, scene.getSceneComponent("fragment5").getDrawY());
    assertEquals(50, scene.getSceneComponent("fragment6").getDrawX());
    assertEquals(830, scene.getSceneComponent("fragment6").getDrawY());
  }
}
