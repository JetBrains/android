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
import com.android.tools.idea.naveditor.scene.layout.ManualLayoutAlgorithm;
import com.android.tools.idea.naveditor.surface.NavDesignSurface;
import com.android.tools.idea.naveditor.surface.NavView;
import com.android.tools.idea.uibuilder.SyncNlModel;
import com.android.tools.idea.uibuilder.editor.NlEditor;
import com.android.tools.idea.uibuilder.fixtures.ComponentDescriptor;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.scene.Scene;
import com.android.tools.idea.uibuilder.scene.SceneComponent;
import com.android.tools.idea.uibuilder.scene.SceneContext;
import com.android.tools.idea.uibuilder.scene.draw.DisplayList;
import com.android.tools.idea.uibuilder.surface.ZoomType;
import com.google.common.collect.ImmutableList;
import com.intellij.openapi.command.undo.DocumentReference;
import com.intellij.openapi.command.undo.DocumentReferenceManager;
import com.intellij.openapi.command.undo.DocumentReferenceProvider;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.dom.navigation.NavigationSchema;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

/**
 * Tests for the nav editor Scene.
 */
public class NavSceneTest extends NavigationTestCase {

  public void testDisplayList() {
    ComponentDescriptor root = component(TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment1")
      .unboundedChildren(
        component(TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/subnav"),
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@id/activity")
          ),
        component(TAG_NAVIGATION).id("@+id/subnav")
          .unboundedChildren(
            component(TAG_FRAGMENT)
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
    assertEquals("Clip,0,0,720,420\n" +
                 "DrawNavScreen,311,51,191,319\n" +
                 "DrawComponentFrame,310,50,192,320,1,false\n" +
                 "DrawAction,NORMAL,310x50x192x320,570x50x100x25,NORMAL\n" +
                 "DrawAction,NORMAL,310x50x192x320,50x50x192x320,NORMAL\n" +
                 "DrawActionHandle,502,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,310,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n" +
                 "DrawAction,NORMAL,98x50x192x320,310x50x192x320,NORMAL\n" +
                 "DrawTextRegion,570,50,100,25,0,20,true,false,4,4,14,1.0,\"navigation\"\n" +
                 "DrawComponentFrame,570,50,100,25,1,true\n" +
                 "DrawAction,NORMAL,570x50x100x25,50x50x192x320,NORMAL\n" +
                 "DrawActionHandle,670,62,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,570,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],subnav\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,50,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],activity\n" +
                 "UNClip\n", list.serialize());
  }

  public void testNegativePositions() {
    ComponentDescriptor root = component(TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment1")
      .unboundedChildren(
        component(TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main"),
        component(TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main"),
        component(TAG_FRAGMENT)
          .id("@+id/fragment3")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();

    Scene scene = model.getSurface().getScene();
    ManualLayoutAlgorithm algorithm = ManualLayoutAlgorithm.getInstance(model.getFacet());
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
                 "DrawNavScreen,251,51,191,319\n" +
                 "DrawComponentFrame,250,50,192,320,1,false\n" +
                 "DrawActionHandle,442,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,250,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n" +
                 "DrawAction,NORMAL,38x50x192x320,250x50x192x320,NORMAL\n" +
                 "DrawNavScreen,51,251,191,319\n" +
                 "DrawComponentFrame,50,250,192,320,1,false\n" +
                 "DrawActionHandle,242,410,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,50,244,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment2\n" +
                 "DrawNavScreen,551,451,191,319\n" +
                 "DrawComponentFrame,550,450,192,320,1,false\n" +
                 "DrawActionHandle,742,610,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,550,444,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment3\n" +
                 "UNClip\n", list.serialize());
  }

  public void testVeryPositivePositions() {
    ComponentDescriptor root = component(TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment1")
      .unboundedChildren(
        component(TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main"),
        component(TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main"),
        component(TAG_FRAGMENT)
          .id("@+id/fragment3")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();

    Scene scene = model.getSurface().getScene();
    ManualLayoutAlgorithm algorithm = ManualLayoutAlgorithm.getInstance(model.getFacet());
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
                 "DrawNavScreen,251,51,191,319\n" +
                 "DrawComponentFrame,250,50,192,320,1,false\n" +
                 "DrawActionHandle,442,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,250,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n" +
                 "DrawAction,NORMAL,38x50x192x320,250x50x192x320,NORMAL\n" +
                 "DrawNavScreen,51,251,191,319\n" +
                 "DrawComponentFrame,50,250,192,320,1,false\n" +
                 "DrawActionHandle,242,410,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,50,244,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment2\n" +
                 "DrawNavScreen,551,451,191,319\n" +
                 "DrawComponentFrame,550,450,192,320,1,false\n" +
                 "DrawActionHandle,742,610,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,550,444,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment3\n" +
                 "UNClip\n", list.serialize());
  }

  public void testAddComponent() {
    ComponentDescriptor root = component(TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment2")
      .unboundedChildren(
        component(TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")
          ),
        component(TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2"));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());

    root.addChild(component(TAG_FRAGMENT).id("@+id/fragment3"), null);
    modelBuilder.updateModel(model);
    model.notifyModified(NlModel.ChangeType.EDIT);
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,812,420\n" +
                 "DrawNavScreen,311,51,191,319\n" +
                 "DrawComponentFrame,310,50,192,320,1,false\n" +
                 "DrawAction,NORMAL,310x50x192x320,570x50x192x320,NORMAL\n" +
                 "DrawActionHandle,502,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,310,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n" +
                 "DrawNavScreen,571,51,191,319\n" +
                 "DrawComponentFrame,570,50,192,320,1,false\n" +
                 "DrawActionHandle,762,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,570,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment2\n" +
                 "DrawAction,NORMAL,358x50x192x320,570x50x192x320,NORMAL\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,50,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment3\n" +
                 "UNClip\n", list.serialize());
  }

  public void testRemoveComponent() {
    ComponentDescriptor root = component(TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment2")
      .unboundedChildren(
        component(TAG_FRAGMENT)
          .id("@+id/fragment1")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")),
        component(TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2"));
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
                 "DrawNavScreen,51,51,191,319\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,50,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n" +
                 "UNClip\n", list.serialize());

    UndoManager undoManager = UndoManager.getInstance(getProject());
    undoManager.undo(editor);
    model.notifyModified(NlModel.ChangeType.EDIT);
    model.getSurface().getSceneManager().update();
    list.clear();
    scene.layout(0, SceneContext.get());
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,552,420\n" +
                 "DrawNavScreen,311,51,191,319\n" +
                 "DrawComponentFrame,310,50,192,320,1,false\n" +
                 "DrawAction,NORMAL,310x50x192x320,50x50x192x320,NORMAL\n" +
                 "DrawActionHandle,502,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,310,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment1\n" +
                 "DrawNavScreen,51,51,191,319\n" +
                 "DrawComponentFrame,50,50,192,320,1,false\n" +
                 "DrawActionHandle,242,210,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,50,44,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=12],fragment2\n" +
                 "DrawAction,NORMAL,-162x50x192x320,50x50x192x320,NORMAL\n" +
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
    ComponentDescriptor root = component(TAG_NAVIGATION)
      .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_START_DESTINATION, "@id/fragment2")
      .unboundedChildren(
        component(TAG_FRAGMENT)
          .id("@+id/fragment1")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment2")
          ),
        component(TAG_FRAGMENT)
          .id("@+id/fragment2")
          .withAttribute(SdkConstants.TOOLS_URI, SdkConstants.ATTR_LAYOUT, "@layout/activity_main2")
          .unboundedChildren(
            component(NavigationSchema.TAG_ACTION)
              .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment3")
          ),
        component(TAG_NAVIGATION)
          .id("@+id/subnav")
          .unboundedChildren(
            component(TAG_FRAGMENT)
              .id("@+id/fragment3")
              .unboundedChildren(
                component(NavigationSchema.TAG_ACTION)
                  .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment4")),
            component(TAG_FRAGMENT)
              .id("@+id/fragment4")
              .unboundedChildren(
                component(NavigationSchema.TAG_ACTION)
                  .withAttribute(SdkConstants.AUTO_URI, NavigationSchema.ATTR_DESTINATION, "@+id/fragment1"))));
    ModelBuilder modelBuilder = model("nav.xml", root);
    SyncNlModel model = modelBuilder.build();
    NavDesignSurface surface = new NavDesignSurface(getProject(), getTestRootDisposable());
    surface.setSize(1000, 1000);
    surface.setModel(model);
    surface.zoom(ZoomType.ACTUAL);

    Scene scene = surface.getScene();
    DisplayList list = new DisplayList();
    scene.layout(0, SceneContext.get());

    NavView view = new NavView(surface, model);
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,1440,840\n" +
                 "DrawComponentFrame,620,100,384,640,1,false\n" +
                 "DrawAction,NORMAL,620x100x384x640,100x100x384x640,NORMAL\n" +
                 "DrawActionHandle,1004,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,620,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],fragment1\n" +
                 "DrawNavScreen,101,101,383,639\n" +
                 "DrawComponentFrame,100,100,384,640,1,false\n" +
                 "DrawActionHandle,484,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,100,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],fragment2\n" +
                 "DrawAction,NORMAL,-304x100x384x640,100x100x384x640,NORMAL\n" +
                 "DrawTextRegion,1140,100,200,50,0,20,true,false,4,4,14,1.0,\"navigation\"\n" +
                 "DrawComponentFrame,1140,100,200,50,1,true\n" +
                 "DrawAction,NORMAL,1140x100x200x50,620x100x384x640,NORMAL\n" +
                 "DrawActionHandle,1340,124,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,1140,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],subnav\n" +
                 "UNClip\n", list.serialize());
    list.clear();
    surface.setCurrentNavigation(model.find("subnav"));
    scene.layout(0, SceneContext.get(view));
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,1104,840\n" +
                 "DrawComponentFrame,620,100,384,640,1,false\n" +
                 "DrawAction,NORMAL,620x100x384x640,100x100x384x640,NORMAL\n" +
                 "DrawActionHandle,1004,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,620,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],fragment3\n" +
                 "DrawComponentFrame,100,100,384,640,1,false\n" +
                 "DrawActionHandle,484,420,0,0,ffc0c0c0,fafafa\n" +
                 "DrawScreenLabel,100,88,ffc0c0c0,java.awt.Font[family=Dialog,name=Default,style=plain,size=24],fragment4\n" +
                 "UNClip\n", list.serialize());
  }
}
