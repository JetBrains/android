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
import com.android.tools.idea.naveditor.NavTestCase;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.android.dom.navigation.NavigationSchema;

import static com.android.SdkConstants.ATTR_ID;
import static com.android.tools.idea.naveditor.NavModelBuilderUtil.fragmentComponent;
import static com.android.tools.idea.naveditor.NavModelBuilderUtil.rootComponent;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link ManualLayoutAlgorithm}
 */
public class ManualLayoutAlgorithmTest extends NavTestCase {

  public void testSimple() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root")
                                .unboundedChildren(
                                  fragmentComponent("fragment1"),
                                  fragmentComponent("fragment2"))).build();
    ManualLayoutAlgorithm.LayoutPositions rootPositions = new ManualLayoutAlgorithm.LayoutPositions();
    ManualLayoutAlgorithm.LayoutPositions positions = new ManualLayoutAlgorithm.LayoutPositions();
    rootPositions.put("nav.xml", positions);

    ManualLayoutAlgorithm.LayoutPositions newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    positions.put("root", newPositions);
    positions = newPositions;

    newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    newPositions.myPosition = new ManualLayoutAlgorithm.Point(123, 456);
    positions.put("fragment1", newPositions);

    newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    newPositions.myPosition = new ManualLayoutAlgorithm.Point(456, 789);
    positions.put("fragment2", newPositions);

    Scene scene = model.getSurface().getScene();
    NavSceneLayoutAlgorithm fallback = mock(NavSceneLayoutAlgorithm.class);
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(fallback, NavigationSchema.getOrCreateSchema(myFacet), rootPositions);
    scene.getRoot().flatten().forEach(algorithm::layout);
    verifyZeroInteractions(fallback);

    assertEquals(123, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(456, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(456, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(789, scene.getSceneComponent("fragment2").getDrawY());
  }

  public void testDifferentFiles() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root")
                                .unboundedChildren(
                                  fragmentComponent("fragment1"))).build();

    SyncNlModel model2 = model("nav2.xml",
                               rootComponent("root")
                                 .unboundedChildren(
                                   fragmentComponent("fragment1"))).build();

    ManualLayoutAlgorithm.LayoutPositions rootPositions = new ManualLayoutAlgorithm.LayoutPositions();
    ManualLayoutAlgorithm.LayoutPositions positions = new ManualLayoutAlgorithm.LayoutPositions();
    rootPositions.put("nav.xml", positions);

    ManualLayoutAlgorithm.LayoutPositions newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    positions.put("root", newPositions);
    positions = newPositions;

    newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    newPositions.myPosition = new ManualLayoutAlgorithm.Point(123, 456);
    positions.put("fragment1", newPositions);

    positions = new ManualLayoutAlgorithm.LayoutPositions();
    rootPositions.put("nav2.xml", positions);

    newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    positions.put("root", newPositions);
    positions = newPositions;

    newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    newPositions.myPosition = new ManualLayoutAlgorithm.Point(456, 789);
    positions.put("fragment1", newPositions);

    Scene scene = model.getSurface().getScene();
    NavSceneLayoutAlgorithm fallback = mock(NavSceneLayoutAlgorithm.class);
    ManualLayoutAlgorithm algorithm =
      new ManualLayoutAlgorithm(fallback, NavigationSchema.getOrCreateSchema(myFacet), rootPositions);
    scene.getRoot().flatten().forEach(algorithm::layout);

    Scene scene2 = model2.getSurface().getScene();
    scene2.getRoot().flatten().forEach(algorithm::layout);

    assertEquals(123, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(456, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(456, scene2.getSceneComponent("fragment1").getDrawX());
    assertEquals(789, scene2.getSceneComponent("fragment1").getDrawY());
  }

  public void testFallback() {
    SyncNlModel model = model("nav.xml",
                              rootComponent("root")
                                .unboundedChildren(
                                  fragmentComponent("fragment1"),
                                  fragmentComponent("fragment2"),
                                  fragmentComponent("fragment3"))).build();

    ManualLayoutAlgorithm.LayoutPositions rootPositions = new ManualLayoutAlgorithm.LayoutPositions();
    ManualLayoutAlgorithm.LayoutPositions positions = new ManualLayoutAlgorithm.LayoutPositions();
    rootPositions.put("nav.xml", positions);

    ManualLayoutAlgorithm.LayoutPositions newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    positions.put("root", newPositions);
    positions = newPositions;

    newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    newPositions.myPosition = new ManualLayoutAlgorithm.Point(60, 60);
    positions.put("fragment1", newPositions);

    newPositions = new ManualLayoutAlgorithm.LayoutPositions();
    newPositions.myPosition = new ManualLayoutAlgorithm.Point(200, 200);
    positions.put("fragment3", newPositions);


    Scene scene = model.getSurface().getScene();
    NavSceneLayoutAlgorithm fallback = mock(NavSceneLayoutAlgorithm.class);
    SceneComponent fragment2 = scene.getSceneComponent("fragment2");
    doAnswer(invocation -> {
      ((SceneComponent)invocation.getArgument(0)).setPosition(123, 456);
      return null;
    }).when(fallback).layout(fragment2);
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(fallback, NavigationSchema.getOrCreateSchema(myFacet), rootPositions);
    scene.getRoot().flatten().forEach(algorithm::layout);
    verify(fallback).layout(fragment2);
    verifyNoMoreInteractions(fallback);

    assertEquals(60, scene.getSceneComponent("fragment1").getDrawX());
    assertEquals(60, scene.getSceneComponent("fragment1").getDrawY());
    assertEquals(123, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(456, scene.getSceneComponent("fragment2").getDrawY());
    assertEquals(200, scene.getSceneComponent("fragment3").getDrawX());
    assertEquals(200, scene.getSceneComponent("fragment3").getDrawY());

    algorithm.layout(fragment2);
    verifyNoMoreInteractions(fallback);
    assertEquals(123, scene.getSceneComponent("fragment2").getDrawX());
    assertEquals(456, scene.getSceneComponent("fragment2").getDrawY());
  }

  public void testSave() throws Exception {
    SyncNlModel model = model("nav.xml",
                              rootComponent("nav").unboundedChildren(
                                fragmentComponent("fragment1"),
                                fragmentComponent("fragment2"))).build();
    NavDesignSurface surface = new NavDesignSurface(getProject(), myRootDisposable);
    surface.setModel(model);
    SceneComponent component = surface.getScene().getSceneComponent("fragment1");
    component.setPosition(100, 200);
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(model.getModule());
    algorithm.save(component);
    PlatformTestUtil.saveProject(getProject());

    // Tests always use file-based storage, not directory-based
    assertTrue(FileUtil.loadFile(VfsUtilCore.virtualToIoFile(getProject().getProjectFile())).contains("fragment1"));

    // Now create everything anew and verify the old position is restored
    model = model("nav.xml", rootComponent("nav").unboundedChildren(
      fragmentComponent("fragment1"),
      fragmentComponent("fragment2"))).build();
    surface = new NavDesignSurface(getProject(), myRootDisposable);
    surface.setModel(model);
    component = surface.getScene().getSceneComponent("fragment1");
    algorithm = new ManualLayoutAlgorithm(model.getModule());
    algorithm.layout(component);
    assertEquals(100, component.getDrawX());
    assertEquals(200, component.getDrawY());
  }

  public void testSaveWithError() {
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(myModule);
    SyncNlModel model = model("nav.xml",
                              rootComponent("nav").unboundedChildren(
                                fragmentComponent("fragment1"),
                                fragmentComponent("fragment2"))).build();
    NavDesignSurface surface = new NavDesignSurface(getProject(), getTestRootDisposable());
    surface.setModel(model);
    SceneComponent nullIdComponent = surface.getScene().getSceneComponent("fragment1");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> nullIdComponent.getNlComponent().setAndroidAttribute(ATTR_ID, null));
    nullIdComponent.setPosition(100, 200);
    algorithm.save(nullIdComponent);

    SceneComponent component = surface.getScene().getSceneComponent("fragment2");
    component.setPosition(400, 500);
    algorithm.save(component);
    PlatformTestUtil.saveProject(getProject());

    // Now create everything anew and verify the old position is restored
    model = model("nav.xml", rootComponent("nav").unboundedChildren(
      fragmentComponent("fragment1"),
      fragmentComponent("fragment2"))).build();
    surface = new NavDesignSurface(getProject(), getTestRootDisposable());
    surface.setModel(model);
    component = surface.getScene().getSceneComponent("fragment2");
    algorithm = new ManualLayoutAlgorithm(model.getModule());
    algorithm.layout(component);
    assertEquals(400, component.getDrawX());
    assertEquals(500, component.getDrawY());

    // don't need to test the null id component; behavior there is undefined.
  }
}
