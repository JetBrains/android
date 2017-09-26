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

import com.android.sdklib.devices.DeviceManager;
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
    ComponentDescriptor root = rootComponent()
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
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,670,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawNavScreen,24,51,51,179,299\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawAction,21,NORMAL,50x50x180x300,310x50x100x25,NORMAL\n" +
                 "DrawAction,21,NORMAL,50x50x180x300,440x50x180x300,NORMAL\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawIcon,23,50x38x12x12,START_DESTINATION\n" +
                 "DrawScreenLabel,22,66,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "DrawNavigationBackground,20,310x50x100x25\n" +
                 "DrawTextRegion,310,50,100,25,0,17,true,false,4,4,30,0.5,\"subnav\"\n" +
                 "DrawNavigationFrame,20,310x50x100x25,false,false\n" +
                 "DrawAction,21,NORMAL,310x50x100x25,440x50x180x300,NORMAL\n" +
                 "DrawActionHandle,25,410,62,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,310,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],subnav\n" +
                 "\n" +
                 "DrawComponentBackground,440,50,180,300,1\n" +
                 "DrawScreenFrame,20,440x50x180x300,false,false\n" +
                 "DrawScreenLabel,22,440,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],activity\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testInclude() {
    ComponentDescriptor root = rootComponent()
      .unboundedChildren(
        fragmentComponent("fragment1")
          .unboundedChildren(
            actionComponent("action1")
              .withDestinationAttribute("nav")),
        includeComponent("navigation"));
    SyncNlModel model = model("nav2.xml", root).build();

    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,460,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawAction,21,NORMAL,50x50x180x300,310x50x100x25,NORMAL\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "DrawNavigationBackground,20,310x50x100x25\n" +
                 "DrawTextRegion,310,50,100,25,0,17,true,false,4,4,30,0.5,\"myCoolLabel\"\n" +
                 "DrawNavigationFrame,20,310x50x100x25,false,false\n" +
                 "DrawScreenLabel,22,310,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testNegativePositions() {
    ComponentDescriptor root = rootComponent()
      .withStartDestinationAttribute("fragment1")
      .id("@+id/root")
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
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,780,800\n" +
                 "DrawComponentBackground,250,50,180,300,1\n" +
                 "DrawNavScreen,24,251,51,179,299\n" +
                 "DrawScreenFrame,20,250x50x180x300,false,false\n" +
                 "DrawActionHandle,25,430,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawIcon,23,250x38x12x12,START_DESTINATION\n" +
                 "DrawScreenLabel,22,266,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,50,250,180,300,1\n" +
                 "DrawNavScreen,24,51,251,179,299\n" +
                 "DrawScreenFrame,20,50x250x180x300,false,false\n" +
                 "DrawActionHandle,25,230,400,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,246,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment2\n" +
                 "\n" +
                 "DrawComponentBackground,550,450,180,300,1\n" +
                 "DrawNavScreen,24,551,451,179,299\n" +
                 "DrawScreenFrame,20,550x450x180x300,false,false\n" +
                 "DrawActionHandle,25,730,600,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,550,446,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment3\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testVeryPositivePositions() {
    ComponentDescriptor root = rootComponent()
      .withStartDestinationAttribute("fragment1")
      .id("@+id/root")
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
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,780,800\n" +
                 "DrawComponentBackground,250,50,180,300,1\n" +
                 "DrawNavScreen,24,251,51,179,299\n" +
                 "DrawScreenFrame,20,250x50x180x300,false,false\n" +
                 "DrawActionHandle,25,430,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawIcon,23,250x38x12x12,START_DESTINATION\n" +
                 "DrawScreenLabel,22,266,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,50,250,180,300,1\n" +
                 "DrawNavScreen,24,51,251,179,299\n" +
                 "DrawScreenFrame,20,50x250x180x300,false,false\n" +
                 "DrawActionHandle,25,230,400,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,246,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment2\n" +
                 "\n" +
                 "DrawComponentBackground,550,450,180,300,1\n" +
                 "DrawNavScreen,24,551,451,179,299\n" +
                 "DrawScreenFrame,20,550x450x180x300,false,false\n" +
                 "DrawActionHandle,25,730,600,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,550,446,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment3\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testAddComponent() {
    ComponentDescriptor root = rootComponent()
      .withStartDestinationAttribute("fragment2")
      .id("@+id/root")
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
    scene.layout(0, SceneContext.get());

    root.addChild(fragmentComponent("fragment3"), null);
    modelBuilder.updateModel(model);
    model.notifyModified(NlModel.ChangeType.EDIT);
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,800,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawNavScreen,24,51,51,179,299\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawAction,21,NORMAL,50x50x180x300,310x50x180x300,NORMAL\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,310,50,180,300,1\n" +
                 "DrawNavScreen,24,311,51,179,299\n" +
                 "DrawScreenFrame,20,310x50x180x300,false,false\n" +
                 "DrawActionHandle,25,490,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawIcon,23,310x38x12x12,START_DESTINATION\n" +
                 "DrawScreenLabel,22,326,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment2\n" +
                 "\n" +
                 "DrawComponentBackground,570,50,180,300,1\n" +
                 "DrawScreenFrame,20,570x50x180x300,false,false\n" +
                 "DrawActionHandle,25,750,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,570,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment3\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testRemoveComponent() {
    ComponentDescriptor root = rootComponent()
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

    scene.layout(0, SceneContext.get());
    list.clear();
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,280,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawNavScreen,24,51,51,179,299\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());

    UndoManager undoManager = UndoManager.getInstance(getProject());
    undoManager.undo(editor);
    model.notifyModified(NlModel.ChangeType.EDIT);
    model.getSurface().getSceneManager().update();
    list.clear();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,540,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawNavScreen,24,51,51,179,299\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawAction,21,NORMAL,50x50x180x300,310x50x180x300,NORMAL\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,310,50,180,300,1\n" +
                 "DrawNavScreen,24,311,51,179,299\n" +
                 "DrawScreenFrame,20,310x50x180x300,false,false\n" +
                 "DrawActionHandle,25,490,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawIcon,23,310x38x12x12,START_DESTINATION\n" +
                 "DrawScreenLabel,22,326,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment2\n" +
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
    ComponentDescriptor root = rootComponent()
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
    scene.layout(0, SceneContext.get());

    NavView view = new NavView(surface, model);
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,670,400\n" +
                 "DrawComponentBackground,180,50,180,300,1\n" +
                 "DrawScreenFrame,20,180x50x180x300,false,false\n" +
                 "DrawAction,21,NORMAL,180x50x180x300,440x50x180x300,NORMAL\n" +
                 "DrawActionHandle,25,360,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,180,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "DrawComponentBackground,440,50,180,300,1\n" +
                 "DrawNavScreen,24,441,51,179,299\n" +
                 "DrawScreenFrame,20,440x50x180x300,false,false\n" +
                 "DrawActionHandle,25,620,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawIcon,23,440x38x12x12,START_DESTINATION\n" +
                 "DrawScreenLabel,22,456,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment2\n" +
                 "\n" +
                 "DrawNavigationBackground,20,50x50x100x25\n" +
                 "DrawTextRegion,50,50,100,25,0,17,true,false,4,4,30,0.5,\"subnav\"\n" +
                 "DrawNavigationFrame,20,50x50x100x25,false,false\n" +
                 "DrawAction,21,NORMAL,50x50x100x25,180x50x180x300,NORMAL\n" +
                 "DrawActionHandle,25,150,62,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],subnav\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
    list.clear();
    surface.setCurrentNavigation(model.find("subnav"));
    scene.layout(0, SceneContext.get(view));
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,540,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawAction,21,NORMAL,50x50x180x300,310x50x180x300,NORMAL\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment3\n" +
                 "\n" +
                 "DrawComponentBackground,310,50,180,300,1\n" +
                 "DrawScreenFrame,20,310x50x180x300,false,false\n" +
                 "DrawActionHandle,25,490,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,310,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment4\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testNonexistentLayout() {
    ComponentDescriptor root = rootComponent()
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_nonexistent")
      );

    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));

    assertEquals("Clip,0,0,280,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testSelectedNlComponentSelectedInScene() {
    ComponentDescriptor root = rootComponent()
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
    Scene scene = manager.build();

    assertTrue(scene.getSceneComponent("myId").isSelected());
  }

  public void testSelfAction() {
    ComponentDescriptor root = rootComponent()
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
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));

    assertEquals("Clip,0,0,280,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawNavScreen,24,51,51,179,299\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawAction,21,SELF,50x50x180x300,50x50x180x300,NORMAL\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawIcon,23,50x38x12x12,START_DESTINATION\n" +
                 "DrawScreenLabel,22,66,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testDeepLinks() {
    ComponentDescriptor root = rootComponent()
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
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));

    assertEquals("Clip,0,0,280,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawNavScreen,24,51,51,179,299\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawIcon,23,50x38x12x12,START_DESTINATION\n" +
                 "DrawScreenLabel,22,66,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "DrawIcon,23,218x38x12x12,DEEPLINK\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testSelectedComponent() {
    ComponentDescriptor root = rootComponent()
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
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));

    assertEquals("Clip,0,0,460,400\n" +
                 "DrawComponentBackground,50,50,180,300,3\n" +
                 "DrawScreenFrame,20,50x50x180x300,true,false\n" +
                 "DrawActionHandle,25,230,200,0,8,ff1886f7,fff5f5f5\n" +
                 "DrawIcon,23,50x38x12x12,START_DESTINATION\n" +
                 "DrawScreenLabel,22,66,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "DrawNavigationBackground,20,310x50x100x25\n" +
                 "DrawTextRegion,310,50,100,25,0,17,true,false,4,4,30,0.5,\"subnav\"\n" +
                 "DrawNavigationFrame,20,310x50x100x25,true,false\n" +
                 "DrawActionHandle,25,410,62,0,8,ff1886f7,fff5f5f5\n" +
                 "DrawScreenLabel,22,310,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],subnav\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  public void testDevices() {
    SyncNlModel model = model("nav.xml", rootComponent()
      .unboundedChildren(
        fragmentComponent("fragment1"))).build();
    DisplayList list = new DisplayList();
    DesignSurface surface = model.getSurface();
    Scene scene = surface.getScene();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,280,400\n" +
                 "DrawComponentBackground,50,50,180,300,1\n" +
                 "DrawScreenFrame,20,50x50x180x300,false,false\n" +
                 "DrawActionHandle,25,230,200,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());

    list.clear();
    model.getConfiguration().setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("wear_square", "Google"), false);
    surface.getSceneManager().update();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)surface, model));
    assertEquals("Clip,0,0,187,187\n" +
                 "DrawComponentBackground,37,37,112,112,1\n" +
                 "DrawScreenFrame,20,37x37x112x112,false,false\n" +
                 "DrawActionHandle,25,150,94,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,37,34,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=8],fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());

    list.clear();
    model.getConfiguration().setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("tv_1080p", "Google"), false);
    surface.getSceneManager().update();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)surface, model));
    assertEquals("Clip,0,0,400,268\n" +
                 "DrawComponentBackground,50,50,300,168,1\n" +
                 "DrawScreenFrame,20,50x50x300x168,false,false\n" +
                 "DrawActionHandle,25,350,134,0,0,ffa7a7a7,fff5f5f5\n" +
                 "DrawScreenLabel,22,50,46,ff000000,java.awt.Font[family=Dialog,name=Default,style=plain,size=11],fragment1\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }
}
