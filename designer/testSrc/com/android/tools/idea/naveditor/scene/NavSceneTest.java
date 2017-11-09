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

import com.android.tools.idea.avdmanager.DeviceManagerConnection;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.editor.NlEditor;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.android.tools.idea.common.scene.SceneComponent;
import com.android.tools.idea.common.scene.SceneContext;
import com.android.tools.idea.common.scene.draw.DisplayList;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.ZoomType;
import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.DocumentReferenceProvider;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static com.android.tools.idea.naveditor.NavModelBuilderUtil.*;

/**
 * Tests for the nav editor Scene.
 */
public class NavSceneTest extends NavigationTestCase {

  public void testDisplayList() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("subnav"),
            actionComponent("action2")
              .withDestinationAttribute("activity")
          ),
        navigationComponent("subnav")
          .unboundedChildren(
            fragmentComponent("fragment2")
              .withLayoutAttribute("activity_main2")
              .unboundedChildren(actionComponent("action3")
                                   .withDestinationAttribute("activity"))),
        activityComponent("activity"));
    SyncNlModel model = model("nav.xml", root).build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,1217,1028\n" +
                 "DrawComponentBackground,450,450,77,128,1\n" +
                 "DrawNavScreen,451,451,76,127\n" +
                 "DrawScreenFrame,450x450x77x128,false,false\n" +
                 "DrawAction,NORMAL,450x450x77x128,570x450x70x19,NORMAL\n" +
                 "DrawAction,NORMAL,450x450x77x128,690x450x77x128,NORMAL\n" +
                 "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
                 "DrawIcon,450x439x7x7,START_DESTINATION\n" +
                 "DrawScreenLabel,458,445,fragment1\n" +
                 "\n" +
                 "DrawNavigationBackground,570x450x70x19\n" +
                 "DrawTextRegion,570,450,70,19,0,17,true,false,4,4,30,0.5,\"subnav\"\n" +
                 "DrawNavigationFrame,570x450x70x19,false,false\n" +
                 "DrawAction,NORMAL,570x450x70x19,690x450x77x128,NORMAL\n" +
                 "DrawActionHandle,640,459,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,570,445,subnav\n" +
                 "\n" +
                 "DrawComponentBackground,690,450,77,128,1\n" +
                 "DrawScreenFrame,690x450x77x128,false,false\n" +
                 "DrawScreenLabel,690,445,activity\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testInclude() {
    ComponentDescriptor root = rootComponent("root")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("nav")),
        includeComponent("navigation"));
    SyncNlModel model = model("nav2.xml", root).build();

    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,1090,1028\n" +
                 "DrawComponentBackground,450,450,77,128,1\n" +
                 "DrawScreenFrame,450x450x77x128,false,false\n" +
                 "DrawAction,NORMAL,450x450x77x128,570x450x70x19,NORMAL\n" +
                 "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,450,445,fragment1\n" +
                 "\n" +
                 "DrawNavigationBackground,570x450x70x19\n" +
                 "DrawTextRegion,570,450,70,19,0,17,true,false,4,4,30,0.5,\"myCoolLabel\"\n" +
                 "DrawNavigationFrame,570x450x70x19,false,false\n" +
                 "DrawScreenLabel,570,445,nav\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testNegativePositions() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main"),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main"),
        fragmentComponent("fragment3")
          .withLayoutAttribute("activity_main"));
    SyncNlModel model = model("nav.xml", root).build();

    Scene scene = model.getSurface().getScene();
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(model.getModule());
    SceneComponent component = scene.getSceneComponent("fragment1");
    component.setPosition(-100, -200);
    algorithm.save(component);
    component = scene.getSceneComponent("fragment2");
    component.setPosition(-300, 0);
    algorithm.save(component);
    component = scene.getSceneComponent("fragment3");
    component.setPosition(200, 200);
    algorithm.save(component);

    DisplayList list = new DisplayList();
    model.getSurface().getSceneManager().update();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,1477,1428\n" +
                 "DrawComponentBackground,650,450,77,128,1\n" +
                 "DrawNavScreen,651,451,76,127\n" +
                 "DrawScreenFrame,650x450x77x128,false,false\n" +
                 "DrawActionHandle,727,514,0,0,FRAMES,0\n" +
                 "DrawIcon,650x439x7x7,START_DESTINATION\n" +
                 "DrawScreenLabel,658,445,fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,450,650,77,128,1\n" +
                 "DrawNavScreen,451,651,76,127\n" +
                 "DrawScreenFrame,450x650x77x128,false,false\n" +
                 "DrawActionHandle,527,714,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,450,645,fragment2\n" +
                 "\n" +
                 "DrawComponentBackground,950,850,77,128,1\n" +
                 "DrawNavScreen,951,851,76,127\n" +
                 "DrawScreenFrame,950x850x77x128,false,false\n" +
                 "DrawActionHandle,1027,914,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,950,845,fragment3\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testVeryPositivePositions() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main"),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main"),
        fragmentComponent("fragment3")
          .withLayoutAttribute("activity_main"));
    SyncNlModel model = model("nav.xml", root).build();

    Scene scene = model.getSurface().getScene();
    ManualLayoutAlgorithm algorithm = new ManualLayoutAlgorithm(model.getModule());
    SceneComponent component = scene.getSceneComponent("fragment1");
    component.setPosition(1900, 1800);
    algorithm.save(component);
    component = scene.getSceneComponent("fragment2");
    component.setPosition(1700, 2000);
    algorithm.save(component);
    component = scene.getSceneComponent("fragment3");
    component.setPosition(2200, 2200);
    algorithm.save(component);

    DisplayList list = new DisplayList();
    model.getSurface().getSceneManager().update();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,1477,1428\n" +
                 "DrawComponentBackground,650,450,77,128,1\n" +
                 "DrawNavScreen,651,451,76,127\n" +
                 "DrawScreenFrame,650x450x77x128,false,false\n" +
                 "DrawActionHandle,727,514,0,0,FRAMES,0\n" +
                 "DrawIcon,650x439x7x7,START_DESTINATION\n" +
                 "DrawScreenLabel,658,445,fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,450,650,77,128,1\n" +
                 "DrawNavScreen,451,651,76,127\n" +
                 "DrawScreenFrame,450x650x77x128,false,false\n" +
                 "DrawActionHandle,527,714,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,450,645,fragment2\n" +
                 "\n" +
                 "DrawComponentBackground,950,850,77,128,1\n" +
                 "DrawNavScreen,951,851,76,127\n" +
                 "DrawScreenFrame,950x850x77x128,false,false\n" +
                 "DrawActionHandle,1027,914,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,950,845,fragment3\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testAddComponent() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment2")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("fragment2")
          ),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main2"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));

    root.addChild(fragmentComponent("fragment3"), null);
    modelBuilder.updateModel(model);
    model.notifyModified(NlModel.ChangeType.EDIT);
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,1217,1028\n" +
                 "DrawComponentBackground,450,450,77,128,1\n" +
                 "DrawNavScreen,451,451,76,127\n" +
                 "DrawScreenFrame,450x450x77x128,false,false\n" +
                 "DrawAction,NORMAL,450x450x77x128,570x450x77x128,NORMAL\n" +
                 "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,450,445,fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,570,450,77,128,1\n" +
                 "DrawNavScreen,571,451,76,127\n" +
                 "DrawScreenFrame,570x450x77x128,false,false\n" +
                 "DrawActionHandle,647,514,0,0,FRAMES,0\n" +
                 "DrawIcon,570x439x7x7,START_DESTINATION\n" +
                 "DrawScreenLabel,578,445,fragment2\n" +
                 "\n" +
                 "DrawComponentBackground,690,450,77,128,1\n" +
                 "DrawScreenFrame,690x450x77x128,false,false\n" +
                 "DrawActionHandle,767,514,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,690,445,fragment3\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testRemoveComponent() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment2")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("fragment2")),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main2"));
    SyncNlModel model = model("nav.xml", root).build();
    FileEditor editor = new TestNlEditor(model.getFile().getVirtualFile(), getProject());

    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    model.delete(ImmutableList.of(model.find("fragment2")));

    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    list.clear();
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,977,1028\n" +
                 "DrawComponentBackground,450,450,77,128,1\n" +
                 "DrawNavScreen,451,451,76,127\n" +
                 "DrawScreenFrame,450x450x77x128,false,false\n" +
                 "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,450,445,fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());

    UndoManager undoManager = UndoManager.getInstance(getProject());
    undoManager.undo(editor);
    model.notifyModified(NlModel.ChangeType.EDIT);
    model.getSurface().getSceneManager().update();
    list.clear();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,1097,1028\n" +
                 "DrawComponentBackground,450,450,77,128,1\n" +
                 "DrawNavScreen,451,451,76,127\n" +
                 "DrawScreenFrame,450x450x77x128,false,false\n" +
                 "DrawAction,NORMAL,450x450x77x128,570x450x77x128,NORMAL\n" +
                 "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,450,445,fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,570,450,77,128,1\n" +
                 "DrawNavScreen,571,451,76,127\n" +
                 "DrawScreenFrame,570x450x77x128,false,false\n" +
                 "DrawActionHandle,647,514,0,0,FRAMES,0\n" +
                 "DrawIcon,570x439x7x7,START_DESTINATION\n" +
                 "DrawScreenLabel,578,445,fragment2\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  private static class TestNlEditor extends NlEditor implements DocumentReferenceProvider {
    private final VirtualFile myFile;

    public TestNlEditor(@NotNull VirtualFile file, @NotNull Project project) {
      super(file, project);
      myFile = file;
    }

    @Override
    public Collection<DocumentReference> getDocumentReferences() {
      return ImmutableList.of(DocumentReferenceManager.getInstance().create(myFile));
    }
  }

  public void testSubflow() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment2")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("fragment2")
          ),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main2")
          .unboundedChildren(
            actionComponent("action2")
              .withDestinationAttribute("fragment3")
          ),
        navigationComponent("subnav")
          .unboundedChildren(
            fragmentComponent("fragment3")
              .unboundedChildren(
                actionComponent("action3")
                  .withDestinationAttribute("fragment4")),
            fragmentComponent("fragment4")
              .unboundedChildren(
                actionComponent("action4")
                  .withDestinationAttribute("fragment1"))));
    SyncNlModel model = model("nav.xml", root).build();
    NavDesignSurface surface = new NavDesignSurface(getProject(), myRootDisposable);
    surface.setSize(1000, 1000);
    surface.setModel(model);
    surface.zoom(ZoomType.ACTUAL);
    if (!SystemInfo.isMac || !UIUtil.isRetina()) {
      surface.zoomOut();
      surface.zoomOut();
      surface.zoomOut();
      surface.zoomOut();
    }
    Scene scene = surface.getScene();
    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));

    NavView view = new NavView(surface, model);
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,210,28\n" +
                 "DrawComponentBackground,-50,-50,77,128,1\n" +
                 "DrawScreenFrame,-50x-50x77x128,false,false\n" +
                 "DrawAction,NORMAL,-50x-50x77x128,70x-50x77x128,NORMAL\n" +
                 "DrawActionHandle,27,14,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,-50,-55,fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,70,-50,77,128,1\n" +
                 "DrawNavScreen,71,-49,76,127\n" +
                 "DrawScreenFrame,70x-50x77x128,false,false\n" +
                 "DrawActionHandle,147,14,0,0,FRAMES,0\n" +
                 "DrawIcon,70x-61x7x7,START_DESTINATION\n" +
                 "DrawScreenLabel,78,-55,fragment2\n" +
                 "\n" +
                 "DrawNavigationBackground,190x-50x70x19\n" +
                 "DrawTextRegion,190,-50,70,19,0,17,true,false,4,4,30,0.5,\"subnav\"\n" +
                 "DrawNavigationFrame,190x-50x70x19,false,false\n" +
                 "DrawAction,NORMAL,190x-50x70x19,-50x-50x77x128,NORMAL\n" +
                 "DrawActionHandle,260,-41,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,190,-55,subnav\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
    list.clear();
    surface.setCurrentNavigation(model.find("subnav"));
    scene.layout(0, SceneContext.get(view));
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,97,28\n" +
                 "DrawComponentBackground,-50,-50,77,128,1\n" +
                 "DrawScreenFrame,-50x-50x77x128,false,false\n" +
                 "DrawAction,NORMAL,-50x-50x77x128,70x-50x77x128,NORMAL\n" +
                 "DrawActionHandle,27,14,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,-50,-55,fragment3\n" +
                 "\n" +
                 "DrawComponentBackground,70,-50,77,128,1\n" +
                 "DrawScreenFrame,70x-50x77x128,false,false\n" +
                 "DrawActionHandle,147,14,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,70,-55,fragment4\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testNonexistentLayout() {
    ComponentDescriptor root = rootComponent("root")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_nonexistent")
      );

    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));

    assertEquals("Clip,0,0,977,1028\n" +
                 "DrawComponentBackground,450,450,77,128,1\n" +
                 "DrawScreenFrame,450x450x77x128,false,false\n" +
                 "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,450,445,fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testSelectedNlComponentSelectedInScene() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("subnav"),
            actionComponent("action2")
              .withDestinationAttribute("activity")
          ));
    SyncNlModel model = model("nav.xml", root).build();
    DesignSurface surface = model.getSurface();
    NlComponent rootComponent = model.getComponents().get(0);
    new WriteCommandAction(getProject(), "Add") {
      @Override
      protected void run(@NotNull Result result) {
        XmlTag tag = rootComponent.getTag().createChildTag("fragment", null, null, true);
        NlComponent newComponent = surface.getModel().createComponent(tag, rootComponent, null);
        surface.getSelectionModel().setSelection(ImmutableList.of(newComponent));
        newComponent.assignId("myId");
      }
    }.execute();
    NavSceneManager manager = new NavSceneManager(model, (NavDesignSurface)model.getSurface());
    Scene scene = manager.getScene();

    assertTrue(scene.getSceneComponent("myId").isSelected());
  }

  public void testSelfAction() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("fragment1")
          ));

    SyncNlModel model = model("nav.xml", root).build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));

    assertEquals("Clip,0,0,977,1028\n" +
                 "DrawComponentBackground,450,450,77,128,1\n" +
                 "DrawNavScreen,451,451,76,127\n" +
                 "DrawScreenFrame,450x450x77x128,false,false\n" +
                 "DrawAction,SELF,450x450x77x128,450x450x77x128,NORMAL\n" +
                 "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
                 "DrawIcon,450x439x7x7,START_DESTINATION\n" +
                 "DrawScreenLabel,458,445,fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testDeepLinks() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main")
          .unboundedChildren(
            deepLinkComponent("https://www.android.com/")
          ));

    SyncNlModel model = model("nav.xml", root).build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));

    assertEquals("Clip,0,0,977,1028\n" +
                 "DrawComponentBackground,450,450,77,128,1\n" +
                 "DrawNavScreen,451,451,76,127\n" +
                 "DrawScreenFrame,450x450x77x128,false,false\n" +
                 "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
                 "DrawIcon,450x439x7x7,START_DESTINATION\n" +
                 "DrawScreenLabel,458,445,fragment1\n" +
                 "DrawIcon,520x439x7x7,DEEPLINK\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testSelectedComponent() {
    ComponentDescriptor root = rootComponent("root")
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1"),
        navigationComponent("subnav"));

    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    NlComponent fragment1 = model.find("fragment1");
    NlComponent subnav = model.find("subnav");

    model.getSurface().getSelectionModel().setSelection(ImmutableList.of(fragment1, subnav));

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));

    assertEquals("Clip,0,0,1090,1028\n" +
                 "DrawComponentBackground,450,450,77,128,3\n" +
                 "DrawScreenFrame,450x450x77x128,true,false\n" +
                 "DrawActionHandle,527,514,0,6,SELECTED_FRAMES,200\n" +
                 "DrawIcon,450x439x7x7,START_DESTINATION\n" +
                 "DrawScreenLabel,458,445,fragment1\n" +
                 "\n" +
                 "DrawNavigationBackground,570x450x70x19\n" +
                 "DrawTextRegion,570,450,70,19,0,17,true,false,4,4,30,0.5,\"subnav\"\n" +
                 "DrawNavigationFrame,570x450x70x19,true,false\n" +
                 "DrawActionHandle,640,459,0,6,SELECTED_FRAMES,200\n" +
                 "DrawScreenLabel,570,445,subnav\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testDevices() {
    SyncNlModel model = model("nav.xml", rootComponent("root")
      .unboundedChildren(
        fragmentComponent("fragment1"))).build();
    DisplayList list = new DisplayList();
    DesignSurface surface = model.getSurface();
    Scene scene = surface.getScene();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,977,1028\n" +
                 "DrawComponentBackground,450,450,77,128,1\n" +
                 "DrawScreenFrame,450x450x77x128,false,false\n" +
                 "DrawActionHandle,527,514,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,450,445,fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());

    list.clear();
    model.getConfiguration()
      .setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("wear_square", "Google"), false);
    surface.getSceneManager().update();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)surface, model));
    assertEquals("Clip,0,0,914,914\n" +
                 "DrawComponentBackground,425,425,64,64,1\n" +
                 "DrawScreenFrame,425x425x64x64,false,false\n" +
                 "DrawActionHandle,489,456,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,425,420,fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());

    list.clear();
    model.getConfiguration().setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("tv_1080p", "Google"), false);
    surface.getSceneManager().update();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)surface, model));
    assertEquals("Clip,0,0,1028,972\n" +
                 "DrawComponentBackground,450,450,128,72,1\n" +
                 "DrawScreenFrame,450x450x128x72,false,false\n" +
                 "DrawActionHandle,578,486,0,0,FRAMES,0\n" +
                 "DrawScreenLabel,450,445,fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }
}
