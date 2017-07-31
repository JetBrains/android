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

import com.android.tools.idea.naveditor.NavigationTestCase;
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
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
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,720,420\n" +
                 "DrawNavScreen,23,311,51,191,319\n" +
                 "DrawComponentFrame,310,50,192,320,1,false\n" +
                 "DrawAction,21,NORMAL,310x50x192x320,570x50x100x25,NORMAL\n" +
                 "DrawAction,21,NORMAL,310x50x192x320,50x50x192x320,NORMAL\n" +
                 "DrawActionHandle,24,502,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,310,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n" +
                 "DrawAction,21,NORMAL,98x50x192x320,310x50x192x320,NORMAL\n\n" +
                 "DrawComponentBackground,570,50,100,25,1,true\n" +
                 "DrawTextRegion,570,50,100,25,0,17,true,false,4,4,30,0.5,\"navigation\"\n" +
                 "DrawComponentFrame,570,50,100,25,1,true\n" +
                 "DrawAction,21,NORMAL,570x50x100x25,50x50x192x320,NORMAL\n" +
                 "DrawActionHandle,24,670,62,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,570,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],subnav\n\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,24,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,50,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],activity\n\n" +
                 "UNClip\n", list.serialize());
  }

  public void testNegativePositions() {
    ComponentDescriptor root = rootComponent()
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main"),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main"),
        fragmentComponent("fragment3")
          .withLayoutAttribute("activity_main"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();

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
    assertEquals("Clip,0,0,792,820\n" +
                 "DrawNavScreen,23,251,51,191,319\n" +
                 "DrawComponentFrame,250,50,192,320,1,false\n" +
                 "DrawActionHandle,24,442,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,250,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n" +
                 "DrawAction,21,NORMAL,38x50x192x320,250x50x192x320,NORMAL\n\n" +
                 "DrawNavScreen,23,51,251,191,319\n" +
                 "DrawComponentFrame,50,250,192,320,1,false\n" +
                 "DrawActionHandle,24,242,410,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,50,244,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment2\n\n" +
                 "DrawNavScreen,23,551,451,191,319\n" +
                 "DrawComponentFrame,550,450,192,320,1,false\n" +
                 "DrawActionHandle,24,742,610,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,550,444,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment3\n\n" +
                 "UNClip\n", list.serialize());
  }

  public void testVeryPositivePositions() {
    ComponentDescriptor root = rootComponent()
      .withStartDestinationAttribute("fragment1")
      .unboundedChildren(
        fragmentComponent("fragment1")
          .withLayoutAttribute("activity_main"),
        fragmentComponent("fragment2")
          .withLayoutAttribute("activity_main"),
        fragmentComponent("fragment3")
          .withLayoutAttribute("activity_main"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();

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
    assertEquals("Clip,0,0,792,820\n" +
                 "DrawNavScreen,23,251,51,191,319\n" +
                 "DrawComponentFrame,250,50,192,320,1,false\n" +
                 "DrawActionHandle,24,442,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,250,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n" +
                 "DrawAction,21,NORMAL,38x50x192x320,250x50x192x320,NORMAL\n\n" +
                 "DrawNavScreen,23,51,251,191,319\n" +
                 "DrawComponentFrame,50,250,192,320,1,false\n" +
                 "DrawActionHandle,24,242,410,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,50,244,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment2\n\n" +
                 "DrawNavScreen,23,551,451,191,319\n" +
                 "DrawComponentFrame,550,450,192,320,1,false\n" +
                 "DrawActionHandle,24,742,610,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,550,444,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment3\n\n" +
                 "UNClip\n", list.serialize());
  }

  public void testAddComponent() {
    ComponentDescriptor root = rootComponent()
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
    scene.layout(0, SceneContext.get());

    root.addChild(fragmentComponent("fragment3"), null);
    modelBuilder.updateModel(model);
    model.notifyModified(NlModel.ChangeType.EDIT);
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,812,420\n" +
                 "DrawNavScreen,23,311,51,191,319\n" +
                 "DrawComponentFrame,310,50,192,320,1,false\n" +
                 "DrawAction,21,NORMAL,310x50x192x320,570x50x192x320,NORMAL\n" +
                 "DrawActionHandle,24,502,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,310,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n\n" +
                 "DrawNavScreen,23,571,51,191,319\n" +
                 "DrawComponentFrame,570,50,192,320,1,false\n" +
                 "DrawActionHandle,24,762,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,570,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment2\n" +
                 "DrawAction,21,NORMAL,358x50x192x320,570x50x192x320,NORMAL\n\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,24,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,50,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment3\n\n" +
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
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    FileEditor editor = new TestNlEditor(model.getFile().getVirtualFile(), getProject());

    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    model.delete(ImmutableList.of(model.find("fragment2")));

    scene.layout(0, SceneContext.get());
    list.clear();
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,292,420\n" +
                 "DrawNavScreen,23,51,51,191,319\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,24,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,50,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n\n" +
                 "UNClip\n", list.serialize());

    UndoManager undoManager = UndoManager.getInstance(getProject());
    undoManager.undo(editor);
    model.notifyModified(NlModel.ChangeType.EDIT);
    model.getSurface().getSceneManager().update();
    list.clear();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,552,420\n" +
                 "DrawNavScreen,23,311,51,191,319\n" +
                 "DrawComponentFrame,310,50,192,320,1,false\n" +
                 "DrawAction,21,NORMAL,310x50x192x320,50x50x192x320,NORMAL\n" +
                 "DrawActionHandle,24,502,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,310,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n\n" +
                 "DrawNavScreen,23,51,51,191,319\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,24,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,50,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment2\n" +
                 "DrawAction,21,NORMAL,-162x50x192x320,50x50x192x320,NORMAL\n\n" +
                 "UNClip\n", list.serialize());
  }

  private class TestNlEditor extends NlEditor implements DocumentReferenceProvider {
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
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    NavDesignSurface surface = new NavDesignSurface(getProject(), getTestRootDisposable());
    surface.setSize(1000, 1000);
    surface.setModel(model);
    surface.zoom(ZoomType.ACTUAL);
    if (SystemInfo.isMac && UIUtil.isRetina()) {
      surface.zoomIn();
      surface.zoomIn();
      surface.zoomIn();
      surface.zoomIn();
    }
    Scene scene = surface.getScene();
    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());

    NavView view = new NavView(surface, model);
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,1440,840\n" +
                 "DrawComponentFrame,620,100,384,640,1,false\n" +
                 "DrawAction,21,NORMAL,620x100x384x640,100x100x384x640,NORMAL\n" +
                 "DrawActionHandle,24,1004,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,620,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],fragment1\n\n" +
                 "DrawNavScreen,23,101,101,383,639\n" +
                 "DrawComponentFrame,100,100,384,640,1,false\n" +
                 "DrawActionHandle,24,484,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,100,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],fragment2\n" +
                 "DrawAction,21,NORMAL,-304x100x384x640,100x100x384x640,NORMAL\n\n" +
                 "DrawComponentBackground,1140,100,200,50,1,true\n" +
                 "DrawTextRegion,1140,100,200,50,0,35,true,false,4,4,30,1.0,\"navigation\"\n" +
                 "DrawComponentFrame,1140,100,200,50,1,true\n" +
                 "DrawAction,21,NORMAL,1140x100x200x50,620x100x384x640,NORMAL\n" +
                 "DrawActionHandle,24,1340,124,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,1140,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],subnav\n\n" +
                 "UNClip\n", list.serialize());
    list.clear();
    surface.setCurrentNavigation(model.find("subnav"));
    scene.layout(0, SceneContext.get(view));
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,1104,840\n" +
                 "DrawComponentFrame,620,100,384,640,1,false\n" +
                 "DrawAction,21,NORMAL,620x100x384x640,100x100x384x640,NORMAL\n" +
                 "DrawActionHandle,24,1004,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,620,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],fragment3\n\n" +
                 "DrawComponentFrame,100,100,384,640,1,false\n" +
                 "DrawActionHandle,24,484,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,100,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],fragment4\n\n" +
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

    assertEquals("Clip,0,0,292,420\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,24,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,22,50,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n\n" +
                 "UNClip\n", list.serialize());
  }

  public void testSelectedNlComponentSelectedInScene() throws Exception {
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
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    DesignSurface surface = model.getSurface();
    NlComponent rootComponent = model.getComponents().get(0);
    new WriteCommandAction(getProject(), "Add") {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        XmlTag tag = rootComponent.getTag().createChildTag("fragment", null, null, true);
        NlComponent newComponent = surface.getModel().createComponent(tag, rootComponent, null);
        model.getSelectionModel().setSelection(ImmutableList.of(newComponent));
        newComponent.assignId("myId");
      }
    }.execute();
    NavSceneManager manager = new NavSceneManager(model, (NavDesignSurface)model.getSurface());
    Scene scene = manager.build();

    assertTrue(scene.getSceneComponent("myId").isSelected());
  }
}
