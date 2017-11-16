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

import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.naveditor.NavigationTestCase;
import org.jetbrains.android.dom.navigation.NavigationSchema;

import static com.android.tools.idea.naveditor.NavModelBuilderUtil.fragmentComponent;
import static com.android.tools.idea.naveditor.NavModelBuilderUtil.rootComponent;

/**
 * Tests for {@link DummyAlgorithm}
 */
public class DummyAlgorithmTest extends NavigationTestCase {

  /**
   * Just lay out some components using this algorithm. The basic layout will be:
   *
   * |---------|
   * | 1  2  3 |
   * | 4       |
   * |---------|
   */
  public void testSimple() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root").unboundedChildren(
                                fragmentComponent("fragment1"),
                                fragmentComponent("fragment2"),
                                fragmentComponent("fragment3"),
                                fragmentComponent("fragment4"))).build();
    model.getSurface().getSceneManager().update();
    Scene scene = model.getSurface().getScene();
    SceneComponent root = scene.getRoot();
    DummyAlgorithm algorithm = new DummyAlgorithm(NavigationSchema.getOrCreateSchema(myFacet));
    root.flatten().forEach(c -> c.setPosition(-500, -500));
    root.flatten().forEach(algorithm::layout);

    assertEquals(20, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(20, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(200, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(20, scene.getSceneComponent("fragment2").getDrawY());
    assertEquals(380, scene.getSceneComponent("fragment3").getDrawX());
    assertEquals(20, scene.getSceneComponent("fragment3").getDrawY());
    assertEquals(20, scene.getSceneComponent("fragment4").getDrawX());
    assertEquals(320, scene.getSceneComponent("fragment4").getDrawY());
  }

  /**
   * Test that we don't overlap manually-placed components. The layout will be:
   *
   * |------------|
   * | 22      33 |
   * | 22      33 |
   * | 22  mm  33 |
   * |     mm     |
   * | 44  mm  55 |
   * | 44      55 |
   * | 44  66  55 |
   * |     66     |
   * | 77  66     |
   * | 77         |
   * | 77         |
   * |------------|
   */
  public void testSkipOther() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root").unboundedChildren(
                                fragmentComponent("fragment1"),
                                fragmentComponent("fragment2"),
                                fragmentComponent("fragment3"),
                                fragmentComponent("fragment4"),
                                fragmentComponent("fragment5"),
                                fragmentComponent("fragment6"),
                                fragmentComponent("fragment7"))).build();
    model.getSurface().getSceneManager().update();
    Scene scene = model.getSurface().getScene();
    SceneComponent root = scene.getRoot();
    DummyAlgorithm algorithm = new DummyAlgorithm(NavigationSchema.getOrCreateSchema(myFacet));
    SceneComponent manual = scene.getSceneComponent("fragment1");
    root.flatten().forEach(c -> c.setPosition(-500, -500));
    manual.setPosition(190, 100);
    root.flatten().filter(c -> c != manual).forEach(algorithm::layout);

    assertEquals(190, manual.getDrawX());
    assertEquals(100, manual.getDrawY());

    assertEquals(20, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(20, scene.getSceneComponent("fragment2").getDrawY());
    assertEquals(380, scene.getSceneComponent("fragment3").getDrawX());
    assertEquals(20, scene.getSceneComponent("fragment3").getDrawY());
    assertEquals(20, scene.getSceneComponent("fragment4").getDrawX());
    assertEquals(320, scene.getSceneComponent("fragment4").getDrawY());
    assertEquals(380, scene.getSceneComponent("fragment5").getDrawX());
    assertEquals(320, scene.getSceneComponent("fragment5").getDrawY());
    assertEquals(200, scene.getSceneComponent("fragment6").getDrawX());
    assertEquals(380, scene.getSceneComponent("fragment6").getDrawY());
    assertEquals(20, scene.getSceneComponent("fragment7").getDrawX());
    assertEquals(620, scene.getSceneComponent("fragment7").getDrawY());
  }
}
