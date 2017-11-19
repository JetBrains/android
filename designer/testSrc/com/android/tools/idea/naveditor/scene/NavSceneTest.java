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
import com.intellij.psi.PsiDocumentManager;
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
    assertEquals("Clip,0,0,1050,928\n" +
                 "DrawRectangle,490x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,491,401,74,126\n" +
                 "DrawAction,NORMAL,490x400x76x128,580x400x70x19,NORMAL\n" +
                 "DrawAction,NORMAL,490x400x76x128,400x400x76x128,NORMAL\n" +
                 "DrawFilledCircle,6,568x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,568x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawIcon,490x389x7x7,START_DESTINATION\n" +
                 "DrawTruncatedText,3,fragment1,498x390x68x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawFilledRectangle,580x400x70x19,fffafafa,6\n" +
                 "DrawRectangle,579x399x72x21,ffa7a7a7,1,6\n" +
                 "DrawTruncatedText,3,Nested Graph,580x400x70x19,ffa7a7a7,Default:1:9,true\n" +
                 "DrawAction,NORMAL,580x400x70x19,400x400x76x128,NORMAL\n" +
                 "DrawFilledCircle,6,651x409,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,651x409,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,subnav,580x390x70x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawFilledRectangle,400x400x76x128,fffafafa,6\n" +
                 "DrawRectangle,400x400x76x128,ffa7a7a7,1,6\n" +
                 "DrawFilledRectangle,404x404x68x111,fffafafa,0\n" +
                 "DrawTruncatedText,3,Preview Unavailable,404x404x68x111,ffa7a7a7,Default:0:9,true\n" +
                 "DrawTruncatedText,3,Activity,400x515x76x13,ffa7a7a7,Default:1:9,true\n" +
                 "DrawTruncatedText,3,activity,400x390x76x5,ff656565,Default:0:9,false\n" +
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
    assertEquals("Clip,0,0,960,928\n" +
                 "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
                 "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
                 "DrawAction,NORMAL,400x400x76x128,490x400x70x19,NORMAL\n" +
                 "DrawFilledCircle,6,478x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,478x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawFilledRectangle,490x400x70x19,fffafafa,6\n" +
                 "DrawRectangle,489x399x72x21,ffa7a7a7,1,6\n" +
                 "DrawTruncatedText,3,navigation.xml,490x400x70x19,ffa7a7a7,Default:1:9,true\n" +
                 "DrawTruncatedText,3,nav,490x390x70x5,ff656565,Default:0:9,false\n" +
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
    assertEquals("Clip,0,0,1126,1128\n" +
                 "DrawRectangle,500x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,501,401,74,126\n" +
                 "DrawFilledCircle,6,578x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,578x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawIcon,500x389x7x7,START_DESTINATION\n" +
                 "DrawTruncatedText,3,fragment1,508x390x68x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawRectangle,400x500x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,401,501,74,126\n" +
                 "DrawFilledCircle,6,478x564,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,478x564,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment2,400x490x77x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawRectangle,650x600x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,651,601,74,126\n" +
                 "DrawFilledCircle,6,728x664,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,728x664,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment3,650x590x76x5,ff656565,Default:0:9,false\n" +
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
    assertEquals("Clip,0,0,1126,1128\n" +
                 "DrawRectangle,500x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,501,401,74,126\n" +
                 "DrawFilledCircle,6,578x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,578x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawIcon,500x389x7x7,START_DESTINATION\n" +
                 "DrawTruncatedText,3,fragment1,508x390x68x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawRectangle,400x500x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,401,501,74,126\n" +
                 "DrawFilledCircle,6,478x564,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,478x564,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment2,400x490x76x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawRectangle,650x600x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,651,601,74,126\n" +
                 "DrawFilledCircle,6,728x664,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,728x664,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment3,650x590x76x5,ff656565,Default:0:9,false\n" +
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
    assertEquals("Clip,0,0,1056,928\n" +
                 "DrawRectangle,490x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,491,401,74,126\n" +
                 "DrawAction,NORMAL,490x400x76x128,400x400x76x128,NORMAL\n" +
                 "DrawFilledCircle,6,568x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,568x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,401,401,74,126\n" +
                 "DrawFilledCircle,6,478x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,478x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawIcon,400x389x7x7,START_DESTINATION\n" +
                 "DrawTruncatedText,3,fragment2,408x390x68x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawRectangle,580x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawFilledRectangle,581x401x74x126,fffafafa,0\n" +
                 "DrawTruncatedText,3,Preview Unavailable,581x401x74x126,ffa7a7a7,Default:0:9,true\n" +
                 "DrawFilledCircle,6,658x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,658x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment3,580x390x76x5,ff656565,Default:0:9,false\n" +
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
    FileEditor editor = new TestNlEditor(model.getVirtualFile(), getProject());

    Scene scene = model.getSurface().getScene();

    DisplayList list = new DisplayList();
    model.delete(ImmutableList.of(model.find("fragment2")));

    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    list.clear();
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,876,928\n" +
                 "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,401,401,74,126\n" +
                 "DrawFilledCircle,6,478x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,478x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "UNClip\n", list.serialize());

    UndoManager undoManager = UndoManager.getInstance(getProject());
    undoManager.undo(editor);
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    model.notifyModified(NlModel.ChangeType.EDIT);
    model.getSurface().getSceneManager().update();
    list.clear();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,966,928\n" +
                 "DrawRectangle,490x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,491,401,74,126\n" +
                 "DrawAction,NORMAL,490x400x76x128,400x400x76x128,NORMAL\n" +
                 "DrawFilledCircle,6,568x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,568x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment1,490x390x76x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,401,401,74,126\n" +
                 "DrawFilledCircle,6,478x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,478x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawIcon,400x389x7x7,START_DESTINATION\n" +
                 "DrawTruncatedText,3,fragment2,408x390x68x5,ff656565,Default:0:9,false\n" +
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
    assertEquals("Clip,0,0,56,-72\n" +
                 "DrawRectangle,-10x-100x76x128,ffa7a7a7,1,0\n" +
                 "DrawFilledRectangle,-9x-99x74x126,fffafafa,0\n" +
                 "DrawTruncatedText,3,Preview Unavailable,-9x-99x74x126,ffa7a7a7,Default:0:9,true\n" +
                 "DrawAction,NORMAL,-10x-100x76x128,80x-100x76x128,NORMAL\n" +
                 "DrawFilledCircle,6,68x-36,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,68x-36,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment1,-10x-110x76x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawRectangle,80x-100x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,81,-99,74,126\n" +
                 "DrawFilledCircle,6,158x-36,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,158x-36,ffa7a7a7,2,0:0:0\n" +
                 "DrawIcon,80x-111x7x7,START_DESTINATION\n" +
                 "DrawTruncatedText,3,fragment2,88x-110x68x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawFilledRectangle,-100x-100x70x19,fffafafa,6\n" +
                 "DrawRectangle,-101x-101x72x21,ffa7a7a7,1,6\n" +
                 "DrawTruncatedText,3,Nested Graph,-100x-100x70x19,ffa7a7a7,Default:1:9,true\n" +
                 "DrawAction,NORMAL,-100x-100x70x19,-10x-100x76x128,NORMAL\n" +
                 "DrawFilledCircle,6,-29x-91,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,-29x-91,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,subnav,-100x-110x70x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
    list.clear();
    surface.setCurrentNavigation(model.find("subnav"));
    scene.layout(0, SceneContext.get(view));
    scene.buildDisplayList(list, 0, view);
    assertEquals("Clip,0,0,-33,-72\n" +
                 "DrawRectangle,-10x-100x76x128,ffa7a7a7,1,0\n" +
                 "DrawFilledRectangle,-9x-99x74x126,fffafafa,0\n" +
                 "DrawTruncatedText,3,Preview Unavailable,-9x-99x74x126,ffa7a7a7,Default:0:9,true\n" +
                 "DrawAction,NORMAL,-10x-100x76x128,-100x-100x76x128,NORMAL\n" +
                 "DrawFilledCircle,6,68x-36,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,68x-36,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment3,-10x-110x76x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawRectangle,-100x-100x76x128,ffa7a7a7,1,0\n" +
                 "DrawFilledRectangle,-99x-99x74x126,fffafafa,0\n" +
                 "DrawTruncatedText,3,Preview Unavailable,-99x-99x74x126,ffa7a7a7,Default:0:9,true\n" +
                 "DrawFilledCircle,6,-22x-36,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,-22x-36,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment4,-100x-110x76x5,ff656565,Default:0:9,false\n" +
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

    assertEquals("Clip,0,0,876,928\n" +
                 "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
                 "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
                 "DrawFilledCircle,6,478x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,478x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawTruncatedText,3,fragment1,400x390x76x5,ff656565,Default:0:9,false\n" +
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

    assertEquals("Clip,0,0,876,928\n" +
                 "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,401,401,74,126\n" +
                 "DrawAction,SELF,400x400x76x128,400x400x76x128,NORMAL\n" +
                 "DrawFilledCircle,6,478x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,478x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawIcon,400x389x7x7,START_DESTINATION\n" +
                 "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
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

    assertEquals("Clip,0,0,876,928\n" +
                 "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawNavScreen,401,401,74,126\n" +
                 "DrawFilledCircle,6,478x464,fff5f5f5,0:0:0\n" +
                 "DrawCircle,7,478x464,ffa7a7a7,2,0:0:0\n" +
                 "DrawIcon,400x389x7x7,START_DESTINATION\n" +
                 "DrawIcon,469x389x7x7,DEEPLINK\n" +
                 "DrawTruncatedText,3,fragment1,408x390x60x5,ff656565,Default:0:9,false\n" +
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

    assertEquals("Clip,0,0,960,928\n" +
                 "DrawRectangle,400x400x76x128,ffa7a7a7,1,0\n" +
                 "DrawFilledRectangle,401x401x74x126,fffafafa,0\n" +
                 "DrawTruncatedText,3,Preview Unavailable,401x401x74x126,ffa7a7a7,Default:0:9,true\n" +
                 "DrawRectangle,398x398x80x132,ff1886f7,1,2\n" +
                 "DrawFilledCircle,6,478x464,fff5f5f5,0:3:54\n" +
                 "DrawCircle,7,478x464,ff1886f7,2,0:2:54\n" +
                 "DrawIcon,400x389x7x7,START_DESTINATION\n" +
                 "DrawTruncatedText,3,fragment1,408x390x68x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "DrawFilledRectangle,490x400x70x19,fffafafa,6\n" +
                 "DrawRectangle,489x399x72x21,ff1886f7,1,6\n" +
                 "DrawTruncatedText,3,Nested Graph,490x400x70x19,ff1886f7,Default:1:9,true\n" +
                 "DrawFilledCircle,6,561x409,fff5f5f5,0:3:54\n" +
                 "DrawCircle,7,561x409,ff1886f7,2,0:2:54\n" +
                 "DrawTruncatedText,3,subnav,490x390x70x5,ff656565,Default:0:9,false\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }

  // TODO: this should test the different "Simulated Layouts", once that's implemented.
  public void disabledTestDevices() {
    SyncNlModel model = model("nav.xml", rootComponent("root")
      .unboundedChildren(
        fragmentComponent("fragment1"))).build();
    DisplayList list = new DisplayList();
    DesignSurface surface = model.getSurface();
    Scene scene = surface.getScene();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)model.getSurface(), model));
    assertEquals("Clip,0,0,977,1028\n" +
                 "DrawRectangle,450x450x77x128,FRAMES,1,0\n" +
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
                 "DrawRectangle,425x425x64x64,FRAMES,1,0\n" +
                 "DrawActionHandle,489,456,0,0,FRAMES,0\n" +
                 "DrawTruncatedText,3,fragment1,425x415x64x5,SUBDUED_TEXT,0,false\n" +
                 "\n" +
                 "UNClip\n", list.serialize());

    list.clear();
    model.getConfiguration().setDevice(DeviceManagerConnection.getDefaultDeviceManagerConnection().getDevice("tv_1080p", "Google"), false);
    surface.getSceneManager().update();
    scene.layout(0, SceneContext.get(model.getSurface().getCurrentSceneView()));
    scene.buildDisplayList(list, 0, new NavView((NavDesignSurface)surface, model));
    assertEquals("Clip,0,0,1028,972\n" +
                 "DrawRectangle,450x450x128x72,FRAMES,1,0\n" +
                 "DrawActionHandle,578,486,0,0,FRAMES,0\n" +
                 "DrawTruncatedText,3,fragment1,450x440x128x5,SUBDUED_TEXT,0,false\n" +
                 "\n" +
                 "UNClip\n", list.serialize());
  }
}
