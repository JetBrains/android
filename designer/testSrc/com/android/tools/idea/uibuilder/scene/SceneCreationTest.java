/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.scene;

import com.android.AndroidXConstants;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.SelectionModel;
import com.android.tools.idea.common.model.DefaultSelectionModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneManager;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.uibuilder.NlModelBuilderUtil;
import com.android.tools.idea.uibuilder.model.NlComponentRegistrar;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.xml.XmlFile;
import org.jetbrains.annotations.NotNull;
import org.mockito.InOrder;

import java.util.List;

import static com.android.SdkConstants.*;
import static org.mockito.Mockito.*;

/**
 * Basic tests for creating and updating a Scene out of a NlModel
 */
public class SceneCreationTest extends SceneTest {

  public void testBasicScene() {
    myScreen.get("@id/button")
      .expectXml("<TextView\n" +
                 "    android:id=\"@id/button\"\n" +
                 "    android:layout_width=\"100dp\"\n" +
                 "    android:layout_height=\"20dp\"\n" +
                 "    tools:layout_editor_absoluteX=\"100dp\"\n" +
                 "    tools:layout_editor_absoluteY=\"200dp\"/>");

    assertTrue(myInteraction.getDisplayList().getCommands().size() > 0);
    SceneComponent component = myScene.getSceneComponent("button");
    assertEquals(component.getScene(), myScene);
    NlComponent nlComponent = component.getNlComponent();
    assertEquals(component, myScene.getSceneComponent(nlComponent));
  }

  public void testSceneCreation() {
    ModelBuilder builder = createModel();
    SyncNlModel model = builder.build();
    LayoutlibSceneManager sceneBuilder = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel(model);
    Scene scene = sceneBuilder.getScene();
    scene.setAnimated(false);
    assertEquals(scene.getRoot().getChildren().size(), 1);
    ComponentDescriptor parent = builder.findByPath(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName());
    ComponentDescriptor textView = builder.findByPath(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName(), TEXT_VIEW);
    ComponentDescriptor editText = parent.addChild(component(EDIT_TEXT)
                                                     .withBounds(220, 440, 400, 60)
                                                     .width("200dp")
                                                     .height("30dp"), textView);
    builder.updateModel(model);
    sceneBuilder.update();
    assertEquals(2, scene.getRoot().getChildren().size());
    List<SceneComponent> children = scene.getRoot().getChildren();
    // editText is added before textView.
    SceneComponent sceneEditText = children.get(0);
    assertEquals(110, sceneEditText.getDrawX());
    assertEquals(220, sceneEditText.getDrawY());
    assertEquals(200, sceneEditText.getDrawWidth());
    assertEquals(30, sceneEditText.getDrawHeight());
    SceneComponent sceneTextView = children.get(1);
    assertEquals(100, sceneTextView.getDrawX());
    assertEquals(200, sceneTextView.getDrawY());
    assertEquals(100, sceneTextView.getDrawWidth());
    assertEquals(20, sceneTextView.getDrawHeight());
    parent.removeChild(textView);
    builder.updateModel(model);
    sceneBuilder.update();
    assertEquals(1, scene.getRoot().getChildren().size());
    sceneTextView = scene.getRoot().getChildren().get(0);
    assertEquals(110, sceneTextView.getDrawX());
    assertEquals(220, sceneTextView.getDrawY());
    assertEquals(200, sceneTextView.getDrawWidth());
    assertEquals(30, sceneTextView.getDrawHeight());
  }

  public void testSceneDisposal() {
    SelectionModel selectionModel = spy(new DefaultSelectionModel());
    DesignSurface<?> surface = NlDesignSurface.builder(getProject(), getTestRootDisposable()).setSelectionModel(selectionModel).build();

    // Create a sample model
    XmlFile xmlFile = (XmlFile)myFixture.addFileToProject("sceneDisposedModel.xml", "<LinearLayout/>");
    SyncNlModel model = SyncNlModel.create(getTestRootDisposable(), NlComponentRegistrar.INSTANCE,
                                           null, myFacet, xmlFile.getVirtualFile());

    SceneManager manager = surface.addModelWithoutRender(model);
    Scene scene = manager.getScene();
    InOrder inOrder = inOrder(selectionModel);
    inOrder.verify(selectionModel).addListener(scene);
    inOrder.verify(selectionModel, never()).removeListener(scene);

    // Disposal of the SceneManager should remove the listeners from Scene.
    Disposer.dispose(manager);
    inOrder.verify(selectionModel).removeListener(scene);
    inOrder.verify(selectionModel, never()).addListener(scene);
  }

  public void testSceneReparenting() {
    ModelBuilder builder = createModel();
    SyncNlModel model = builder.build();
    LayoutlibSceneManager sceneBuilder = NlModelBuilderUtil.getSyncLayoutlibSceneManagerForModel(model);
    Scene scene = sceneBuilder.getScene();
    scene.setAnimated(false);
    assertEquals(scene.getRoot().getChildren().size(), 1);
    ComponentDescriptor parent = builder.findByPath(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName());
    parent.addChild(component(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
                      .id("@id/layout")
                      .withBounds(200, 300, 200, 200)
                      .width("200dp")
                      .height("200dp"), null);
    builder.updateModel(model);
    sceneBuilder.update();
    assertEquals(2, scene.getRoot().getChildren().size());

    NlComponent textView = scene.getRoot().getChild(0).getNlComponent();
    NlComponent container = scene.getRoot().getChild(1).getNlComponent();
    scene.getRoot().getNlComponent().removeChild(textView);
    container.addChild(textView);
    sceneBuilder.update();
    assertEquals(1, scene.getRoot().getChildCount());
    SceneComponent layout = scene.getSceneComponent("layout");
    assertEquals(1, layout.getChildCount());
  }

  public static int pxToDp(@AndroidCoordinate int px, float dpiFactor) {
    return (int)(0.5f + px / dpiFactor);
  }

  public void testDeviceChange() {
    ModelBuilder builder = createModel();
    SyncNlModel model = builder.build();
    Configuration config = model.getConfiguration();
    config.setDevice(config.getConfigurationManager().getDeviceById("Nexus 6P"), false);

    SyncLayoutlibSceneManager manager = new SyncLayoutlibSceneManager((DesignSurface<LayoutlibSceneManager>)model.getSurface(), model);
    manager.setIgnoreRenderRequests(true);
    Scene scene = manager.getScene();
    scene.setAnimated(false);

    SceneComponent sceneTextView = scene.getRoot().getChildren().get(0);

    float dpiFactor =  560 / 160f;
    assertEquals(pxToDp(200, dpiFactor), sceneTextView.getDrawX());
    assertEquals(pxToDp(400, dpiFactor), sceneTextView.getDrawY());
    assertEquals(pxToDp(200, dpiFactor), sceneTextView.getDrawWidth());
    assertEquals(pxToDp(40, dpiFactor), sceneTextView.getDrawHeight());

    config.setDevice(config.getConfigurationManager().getDeviceById("Nexus S"), false);
    dpiFactor = 240 / 160f;

    // Allow 1dp difference for rounding
    assertEquals(pxToDp(200, dpiFactor), sceneTextView.getDrawX(), 1);
    assertEquals(pxToDp(400, dpiFactor), sceneTextView.getDrawY(), 1);
    assertEquals(pxToDp(200, dpiFactor), sceneTextView.getDrawWidth(), 1);
    assertEquals(pxToDp(40, dpiFactor), sceneTextView.getDrawHeight(), 1);
  }


  @Override
  @NotNull
  public ModelBuilder createModel() {
    ModelBuilder builder = model("constraint.xml",
                                 component(AndroidXConstants.CONSTRAINT_LAYOUT.defaultName())
                                   .id("@id/root")
                                   .withBounds(0, 0, 2000, 2000)
                                   .width("1000dp")
                                   .height("1000dp")
                                   .withAttribute("android:padding", "20dp")
                                   .children(
                                     component(TEXT_VIEW)
                                       .id("@id/button")
                                       .withBounds(200, 400, 200, 40)
                                       .width("100dp")
                                       .height("20dp")
                                       .withAttribute("tools:layout_editor_absoluteX", "100dp")
                                       .withAttribute("tools:layout_editor_absoluteY", "200dp")
                                   ));
    return builder;
  }
}