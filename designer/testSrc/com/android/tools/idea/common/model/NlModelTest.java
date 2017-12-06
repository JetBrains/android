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
package com.android.tools.idea.common.model;

import com.android.ide.common.rendering.api.MergeCookie;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.ide.common.repository.GradleCoordinate;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.common.fixtures.ComponentDescriptor;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.gradle.dependencies.GradleDependencyManager;
import com.android.tools.idea.rendering.TagSnapshot;
import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.uibuilder.SyncLayoutlibSceneManager;
import com.android.tools.idea.uibuilder.api.InsertType;
import com.android.tools.idea.uibuilder.handlers.ViewEditorImpl;
import com.android.tools.idea.uibuilder.model.NlComponentHelperKt;
import com.android.tools.idea.uibuilder.model.NlModelHelperKt;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.XmlElementFactory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.createSurface;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;

/**
 * Model tests. This checks that when a model is updated, we correctly
 * handle bounds and hierarchy changes.
 * <p>
 * Most tests start with a current model hierarchy (which is pretty printed.)
 * The important thing to note is the {@code instance=} identity listed at the
 * end. The identity is tracked in a static map. This lets us check that
 * an {@link NlComponent} is really a specific instanceof an object, not just
 * something recreated with the same values.
 * <p>
 * Most tests then perform some sort of mutation on the tag/view info hierarchy,
 * then they update the model, and then pretty print the model again to verify
 * that it has been updated as expected.
 */
public class NlModelTest extends LayoutTestCase {
  private final NlTreeDumper myTreeDumper = new NlTreeDumper();

  @SuppressWarnings("ConstantConditions")
  public void testSync() throws Exception {
    // Whether we include id's in the components or not. Id's help
    // associate id's with before/after versions; they're conditionally
    // added such that we can test the model handler with and without this
    // aid.
    boolean includeIds = false;
    ModelBuilder modelBuilder = createDefaultModelBuilder(includeIds);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));


    modelBuilder.updateModel(model);

    // Everything should be identical
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testRemoveFirstChild() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Remove first child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    parent.removeChild(modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW));
    modelBuilder.updateModel(model);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testRemoveLastChild() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Remove last child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    parent.removeChild(modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON));
    modelBuilder.updateModel(model);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testTransposeChildren() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Remove last child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    ComponentDescriptor button = modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON);
    ComponentDescriptor textView = modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW);
    assertThat(parent).isNotNull();
    parent.removeChild(button);
    modelBuilder.updateModel(model);
    parent.addChild(button, textView);
    modelBuilder.updateModel(model);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=3}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testAddChild() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Add child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    parent.addChild(component(EDIT_TEXT)
      .withBounds(100, 100, 100, 100)
      .width("100dp")
      .height("100dp"), null);
    modelBuilder.updateModel(model);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}\n" +
                 "    NlComponent{tag=<EditText>, bounds=[100,100:100x100, instance=3}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testMoveInHierarchy() throws Exception {
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Move button to be child of the text view instead
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    ComponentDescriptor textView = modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW);
    assertThat(textView).isNotNull();
    ComponentDescriptor button = modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON);
    assertThat(button).isNotNull();
    parent.removeChild(button);
    textView.addChild(button, null);
    modelBuilder.updateModel(model);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "        NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  @SuppressWarnings("ConstantConditions")
  public void testChangedPropertiesWithIds() throws Exception {
    // We include id's in the tags here since (due to attribute
    // changes between the two elements
    boolean includeIds = true;
    ModelBuilder modelBuilder = createDefaultModelBuilder(includeIds);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Change some attributes; this means that our finger print comparison (which
    // hashes all tag names and attributes) won't work
    ComponentDescriptor button = modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON);
    assertThat(button).isNotNull();
    ComponentDescriptor textView = modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW);
    assertThat(textView).isNotNull();
    button.withAttribute("style", "@style/Foo");
    textView.withAttribute("style", "@style/Foo");

    modelBuilder.updateModel(model);

    // Everything should be identical
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  @SuppressWarnings("ConstantConditions")
  public void testChangeSingleProperty() throws Exception {
    boolean includeIds = false;

    ModelBuilder modelBuilder = createDefaultModelBuilder(includeIds);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Change a single attribute in an element without id's.
    ComponentDescriptor layout = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(layout).isNotNull();
    layout.withAttribute("style", "@style/Foo");

    modelBuilder.updateModel(model);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testAddRemove() throws Exception {
    // Test removing one child and adding another one. Check that we don't
    // preserve component identity across two separate tag names.
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    // Add child
    ComponentDescriptor parent = modelBuilder.findByPath(LINEAR_LAYOUT);
    assertThat(parent).isNotNull();
    ComponentDescriptor button = modelBuilder.findByPath(LINEAR_LAYOUT, BUTTON);
    assertThat(button).isNotNull();
    ComponentDescriptor textView = modelBuilder.findByPath(LINEAR_LAYOUT, TEXT_VIEW);
    assertThat(textView).isNotNull();
    parent.addChild(component(EDIT_TEXT)
                      .withBounds(100, 100, 100, 100)
                      .width("100dp")
                      .height("100dp"), textView);
    modelBuilder.updateModel(model);
    parent.removeChild(button);
    modelBuilder.updateModel(model);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<EditText>, bounds=[100,100:100x100, instance=3}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100, instance=1}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testCanAdd() throws Exception {
    NlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(FRAME_LAYOUT)
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp"),
        component(BUTTON)
          .withBounds(100, 200, 100, 100)
          .width("100dp")
          .height("100dp")
      )).build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<FrameLayout>, bounds=[100,100:100x100, instance=1}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    NlComponent linearLayout = model.getComponents().get(0);
    NlComponent frameLayout = linearLayout.getChild(0);
    NlComponent button = linearLayout.getChild(1);
    assertThat(linearLayout).isNotNull();
    assertThat(button).isNotNull();
    assertThat(frameLayout).isNotNull();

    assertThat(model.canAddComponents(Collections.singletonList(frameLayout), linearLayout, frameLayout)).isTrue();
    assertThat(model.canAddComponents(Collections.singletonList(button), frameLayout, null)).isTrue();
    assertThat(model.canAddComponents(Arrays.asList(frameLayout, button), linearLayout, frameLayout)).isTrue();

    assertThat(model.canAddComponents(Collections.singletonList(linearLayout), frameLayout, null)).isFalse();
    assertThat(model.canAddComponents(Collections.singletonList(linearLayout), linearLayout, null)).isFalse();
  }

  public void testCreateComponentWithDependencyCheck() {
    SyncNlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(FRAME_LAYOUT)
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
      ))
      .build();

    NlComponent linearLayout = model.getComponents().get(0);
    NlComponent frameLayout = linearLayout.getChild(0);
    assertThat(frameLayout).isNotNull();

    GradleDependencyManager gradleDependencyManager = mock(GradleDependencyManager.class);
    List<GradleCoordinate> expectedDependencies =
      Collections.singletonList(GradleCoordinate.parseCoordinateString(RECYCLER_VIEW_LIB_ARTIFACT + ":+"));
    when(gradleDependencyManager.userWantToAddDependencies(eq(myModule), eq(expectedDependencies))).thenReturn(true);
    when(gradleDependencyManager.findMissingDependencies(eq(myModule), eq(expectedDependencies))).thenReturn(expectedDependencies);
    when(gradleDependencyManager.addDependenciesAndSync(eq(myModule), eq(expectedDependencies), any())).thenReturn(true);
    registerProjectComponent(GradleDependencyManager.class, gradleDependencyManager);
    XmlTag recyclerViewTag =
      XmlElementFactory.getInstance(getProject()).createTagFromText("<" + RECYCLER_VIEW.defaultName() + " xmlns:android=\"" + NS_RESOURCES + "\"/>");

    WriteCommandAction.runWriteCommandAction(
      model.getProject(), null, null,
      () -> NlModelHelperKt.createComponent(model, new ViewEditorImpl(screen(model).getScreen()), recyclerViewTag, frameLayout, null, InsertType.CREATE
      ),
      model.getFile());
    model.notifyModified(NlModel.ChangeType.ADD_COMPONENTS);
    when(gradleDependencyManager.addDependenciesAndSync(eq(myModule), eq(expectedDependencies), isNull(Runnable.class)))
      .thenReturn(true);

    verify(gradleDependencyManager, atLeastOnce()).addDependenciesAndSync(eq(myModule), eq(expectedDependencies), isNull(Runnable.class));

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,100:768x1084, instance=0}\n" +
                 "    NlComponent{tag=<FrameLayout>, bounds=[0,100:200x200, instance=1}\n" +
                 "        NlComponent{tag=<android.support.v7.widget.RecyclerView>, bounds=[0,100:200x70, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testAddComponentsWithDependencyCheck() {
    SyncNlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(FRAME_LAYOUT)
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
      ))
      .build();

    NlComponent linearLayout = model.getComponents().get(0);
    NlComponent frameLayout = linearLayout.getChild(0);
    assertThat(frameLayout).isNotNull();

    GradleDependencyManager gradleDependencyManager = mock(GradleDependencyManager.class);
    registerProjectComponent(GradleDependencyManager.class, gradleDependencyManager);
    List<GradleCoordinate> expectedDependencies =
      Collections.singletonList(GradleCoordinate.parseCoordinateString(RECYCLER_VIEW_LIB_ARTIFACT + ":+"));
    when(gradleDependencyManager.userWantToAddDependencies(eq(myModule), eq(expectedDependencies))).thenReturn(true);
    when(gradleDependencyManager.findMissingDependencies(eq(myModule), eq(expectedDependencies))).thenReturn(expectedDependencies);
    when(gradleDependencyManager.addDependenciesAndSync(eq(myModule), eq(expectedDependencies), isNull(Runnable.class)))
      .thenReturn(true);

    XmlTag recyclerViewTag =
      XmlElementFactory.getInstance(getProject()).createTagFromText("<" + RECYCLER_VIEW.defaultName() + " xmlns:android=\"" + NS_RESOURCES + "\"/>");
    NlComponent recyclerView =
      NlModelHelperKt.createComponent(model, new ViewEditorImpl(screen(model).getScreen()), recyclerViewTag, null, null, InsertType.CREATE);
    model.addComponents(Collections.singletonList(recyclerView), frameLayout, null, InsertType.CREATE, new ViewEditorImpl(screen(model).getScreen()));

    verify(gradleDependencyManager).addDependenciesAndSync(eq(myModule), eq(expectedDependencies), isNull(Runnable.class));

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,100:768x1084, instance=0}\n" +
                 "    NlComponent{tag=<FrameLayout>, bounds=[0,100:200x200, instance=1}\n" +
                 "        NlComponent{tag=<android.support.v7.widget.RecyclerView>, bounds=[0,100:200x70, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testAddComponentsNoDependencyCheckOnMove() {
    SyncNlModel model = model("my_linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(FRAME_LAYOUT)
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp"),
        component(RECYCLER_VIEW.defaultName())
          .withBounds(100, 200, 100, 100)
          .width("100dp")
          .height("100dp")
      ))
      .build();

    LayoutTestUtilities.createScreen(model);

    NlComponent linearLayout = model.getComponents().get(0);
    NlComponent frameLayout = linearLayout.getChild(0);
    NlComponent recyclerView = linearLayout.getChild(1);
    assertThat(frameLayout).isNotNull();

    GradleDependencyManager gradleDependencyManager = mock(GradleDependencyManager.class);
    model.addComponents(Collections.singletonList(recyclerView), frameLayout, null, InsertType.MOVE_INTO, new ViewEditorImpl(screen(model).getScreen()));
    verifyZeroInteractions(gradleDependencyManager);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,100:768x1084, instance=0}\n" +
                 "    NlComponent{tag=<FrameLayout>, bounds=[0,100:200x200, instance=1}\n" +
                 "        NlComponent{tag=<android.support.v7.widget.RecyclerView>, bounds=[0,100:200x200, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testMoveInHierarchyWithWrongXmlTags() throws Exception {
    ModelBuilder modelBuilder = model("linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .withAttribute(ANDROID_URI, ATTR_ORIENTATION, VALUE_VERTICAL)
      .children(
        component(FRAME_LAYOUT)
          .withBounds(100, 100, 100, 100)
          .width("100dp")
          .height("100dp")
          .children(
            component(BUTTON)
              .withBounds(100, 100, 100, 100)
              .width("100dp")
              .height("100dp")
      )));

    NlModel model = modelBuilder.build();

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000, instance=0}\n" +
                 "    NlComponent{tag=<FrameLayout>, bounds=[100,100:100x100, instance=1}\n" +
                 "        NlComponent{tag=<Button>, bounds=[100,100:100x100, instance=2}",
                 myTreeDumper.toTree(model.getComponents()));

    XmlTag originalRoot = model.getFile().getRootTag();
    assertThat(originalRoot).isNotNull();
    XmlTag originalFrameLayout = originalRoot.getSubTags()[0];

    final Project project = model.getProject();
    WriteCommandAction.runWriteCommandAction(project, () -> {
      PsiDocumentManager manager = PsiDocumentManager.getInstance(project);
      Document document = manager.getDocument(model.getFile());
      assertThat(document).isNotNull();
      document.setText("<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
                       "    android:layout_width=\"match_parent\"\n" +
                       "    android:layout_height=\"match_parent\"\n" +
                       "    android:orientation=\"vertical\">\n" +
                       "    <Button\n" +
                       "        android:layout_width=\"100dp\"\n" +
                       "        android:layout_height=\"100dp\"\n" +
                       "        android:text=\"Button\" />\n" +
                       "    <FrameLayout\n" +
                       "        android:layout_width=\"100dp\"\n" +
                       "        android:layout_height=\"100dp\">\n" +
                       "\n" +
                       "    </FrameLayout>\n" +
                       "\n" +
                       "</LinearLayout>");
      manager.commitAllDocuments();
    });

    // Manually construct the view hierarchy
    // Assert that component identity is preserved
    List<ViewInfo> views = Lists.newArrayList();
    XmlTag newRoot = model.getFile().getRootTag();
    assertThat(newRoot).isNotNull();
    XmlTag[] newRootSubTags = newRoot.getSubTags();
    XmlTag newButton = newRootSubTags[0];

    assertThat(originalRoot).isSameAs(newRoot);

    assertThat(originalFrameLayout).isSameAs(newButton);

    TagSnapshot snapshot = TagSnapshot.createTagSnapshot(newRoot, null);
    ViewInfo viewInfo = new ViewInfo("android.widget.LinearLayout", snapshot, 0, 0, 500, 500);
    views.add(viewInfo);

    ViewInfo buttonInfo = new ViewInfo("android.widget.Button", snapshot.children.get(0), 0, 0, 500, 500);
    buttonInfo.setChildren(Collections.emptyList());
    ViewInfo frameViewInfo = new ViewInfo("android.widget.TextView", snapshot.children.get(1), 0, 0, 300, 300);
    frameViewInfo.setChildren(Collections.emptyList());
    viewInfo.setChildren(Arrays.asList(buttonInfo, frameViewInfo));

    LayoutlibSceneManager.updateHierarchy(views, model);

    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:500x500, instance=0}\n" +
                 // Make sure these instances are NOT reusing instances from before that
                 // mismatch, e.g. we should not get <Button> with instance=1 here
                 // since before the reparse instance=1 was associated with a <FrameLayout> !
                 "    NlComponent{tag=<Button>, bounds=[0,0:500x500, instance=2}\n" +
                 "    NlComponent{tag=<FrameLayout>, bounds=[0,0:300x300, instance=3}",
                 myTreeDumper.toTree(model.getComponents()));
  }

  public void testThemeSelection() {
    myFixture.addFileToProject("res/values/styles.xml",
                               "<resources>" +
                               "  <style name=\"Theme.MyTheme\"></style>" +
                               "</resources>");
    SyncNlModel model = createDefaultModelBuilder(true).build();
    SceneView sceneView = mock(SceneView.class);
    SelectionModel selectionModel = model.getSurface().getSelectionModel();
    when(sceneView.getSelectionModel()).thenReturn(selectionModel);
    when(sceneView.getSurface()).thenReturn(model.getSurface());
    Configuration configuration = model.getConfiguration();
    String defaultTheme = configuration.getTheme();
    assertNotNull(defaultTheme);

    // Set a valid theme
    configuration.setTheme("@style/Theme.MyTheme");
    model.deactivate(this);
    model.activate(this);
    assertEquals("@style/Theme.MyTheme", configuration.getTheme());

    // Set a valid framework theme
    configuration.setTheme("@android:style/Theme.Material");
    model.deactivate(this);
    model.activate(this);
    assertEquals("@android:style/Theme.Material", configuration.getTheme());

    // Check that if we try to select an invalid theme, NlModel will set back the default theme
    configuration.setTheme("@style/InvalidTheme");
    model.deactivate(this);
    model.activate(this);
    assertEquals(defaultTheme, configuration.getTheme());
    configuration.setTheme("@android:style/InvalidTheme");
    model.deactivate(this);
    model.activate(this);
    assertEquals(defaultTheme, configuration.getTheme());
  }

  public void testMergeTag() {
    XmlFile parentXml = (XmlFile)myFixture.addFileToProject("res/layout/parent.xml",
                                                            "<LinearLayout" +
                                                            "         xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
                                                            "         android:layout_width=\"match_parent\"" +
                                                            "         android:layout_height=\"match_parent\">" +
                                                            " <include layout=\"@layout/merge\" />" +
                                                            "</LinearLayout>");
    XmlFile mergeXml = (XmlFile)myFixture.addFileToProject("res/layout/merge.xml",
                                                  "<merge" +
                                                  "         xmlns:android=\"http://schemas.android.com/apk/res/android\">" +
                                                  "   <Button" +
                                                  "     android:layout_width=\"match_parent\"" +
                                                  "     android:layout_height=\"match_parent\" />" +
                                                  "   <TextView" +
                                                  "     android:layout_width=\"match_parent\"" +
                                                  "     android:layout_height=\"match_parent\" />" +
                                                  "</merge>");
    DesignSurface surface = createSurface(NlDesignSurface.class);
    NlModel model = SyncNlModel.create(surface, myFixture.getProject(), myFacet, mergeXml.getVirtualFile());
    when(surface.getModel()).thenReturn(model);

    XmlTag parentRoot = parentXml.getRootTag();
    TagSnapshot parentRootSnapshot = TagSnapshot.createTagSnapshot(parentRoot, null);

    ImmutableList<ViewInfo> list = ImmutableList.of(
      new ViewInfo("android.widget.Button", new MergeCookie(parentRootSnapshot), 0, 0, 50, 50),
      new ViewInfo("android.widget.TextView", new MergeCookie(parentRootSnapshot), 0, 50, 50, 100));

    LayoutlibSceneManager.updateHierarchy(list, model);

    NlComponent rootComponent = model.getComponents().get(0);
    assertNotNull(rootComponent);
    assertEquals(2, rootComponent.getChildCount());
    assertNull(NlComponentHelperKt.getViewInfo(rootComponent.getChild(0)));
    assertNull(NlComponentHelperKt.getViewInfo(rootComponent.getChild(1)));
  }

  /**
   * Tests that {@code ViewGroup} components like {@code SearchView} do not assign incorrectly the {@link NlComponent#viewInfo}.
   * In this particular test, SearchView has a "hidden" {@code LinerLayout} as a children. {@code SearchView} viewInfo must point to the
   * SearchView component and not to any of its children.
   */
  public void testChildComponentWithoutViewInfo() {
    XmlFile modelXml = (XmlFile)myFixture.addFileToProject("res/layout/model.xml",
                                                            "<LinearLayout" +
                                                            "         xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
                                                            "         android:layout_width=\"match_parent\"" +
                                                            "         android:layout_height=\"match_parent\">" +
                                                            "             <SearchView" +
                                                            "               android:layout_width=\"match_parent\"" +
                                                            "               android:layout_height=\"48dp\" />" +
                                                            "</LinearLayout>");
    DesignSurface surface = createSurface(NlDesignSurface.class);
    NlModel model = SyncNlModel.create(surface, myFixture.getProject(), myFacet, modelXml.getVirtualFile());
    when(surface.getModel()).thenReturn(model);

    TagSnapshot rootSnapshot = TagSnapshot.createTagSnapshot(modelXml.getRootTag(), null);
    ViewInfo rootViewInfo = new ViewInfo("android.widget.LinearLayout", rootSnapshot, 0, 0, 500, 500);
    ViewInfo searchViewInfo = new ViewInfo("android.widget.SearchView", rootSnapshot.children.get(0), 0, 0, 500, 500);
    searchViewInfo.setChildren(ImmutableList.of(new ViewInfo("android.widget.LinearLayout", rootSnapshot.children.get(0), 0, 0, 500, 500)));
    rootViewInfo.setChildren(ImmutableList.of(searchViewInfo));
    LayoutlibSceneManager.updateHierarchy(ImmutableList.of(rootViewInfo), model);

    //noinspection OptionalGetWithoutIsPresent
    NlComponent searchViewComponent = model.flattenComponents().filter(c -> c.getTagName().equals("SearchView")).findFirst().get();
    assertEquals("android.widget.SearchView", NlComponentHelperKt.getViewInfo(searchViewComponent).getClassName());
  }

  public void testLayoutListenersModifyListenerList() throws Exception {
    XmlFile modelXml = (XmlFile)myFixture.addFileToProject("res/layout/model.xml",
                                                           "<LinearLayout" +
                                                           "         xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
                                                           "         android:layout_width=\"match_parent\"" +
                                                           "         android:layout_height=\"match_parent\">" +
                                                           "</LinearLayout>");
    NlModel model = SyncNlModel.create(createSurface(NlDesignSurface.class), myFixture.getProject(), myFacet, modelXml.getVirtualFile());
    ModelListener listener1 = mock(ModelListener.class);
    ModelListener remove1 = mock(ModelListener.class, invocation -> {
      model.removeListener((ModelListener)invocation.getMock());
      return null;
    });
    ModelListener listener2 = mock(ModelListener.class);
    model.addListener(listener1);
    model.addListener(remove1);
    model.addListener(listener2);
    model.notifyListenersModelLayoutComplete(false);
    verify(listener1).modelChangedOnLayout(any(), anyBoolean());
    verify(remove1).modelChangedOnLayout(any(), anyBoolean());
    verify(listener2).modelChangedOnLayout(any(), anyBoolean());

    model.notifyListenersModelLayoutComplete(false);
    verify(listener1, times(2)).modelChangedOnLayout(any(), anyBoolean());
    verifyNoMoreInteractions(remove1);
    verify(listener2, times(2)).modelChangedOnLayout(any(), anyBoolean());
  }

  private static void notifyAndCheckListeners(@NotNull NlModel model,
                                              @NotNull Consumer<NlModel> notifyMethod,
                                              @NotNull Consumer<ModelListener> verifyMethod) throws Exception {
    ModelListener listener1 = mock(ModelListener.class);
    ModelListener remove1 = mock(ModelListener.class, invocation -> {
      model.removeListener((ModelListener)invocation.getMock());
      return null;
    });
    ModelListener listener2 = mock(ModelListener.class);
    model.addListener(listener1);
    model.addListener(remove1);
    model.addListener(listener2);
    notifyMethod.accept(model);
    verifyMethod.accept(verify(listener1));
    verifyMethod.accept(verify(remove1));
    verifyMethod.accept(verify(listener2));

    notifyMethod.accept(model);
    verifyMethod.accept(verify(listener1, times(2)));
    verifyNoMoreInteractions(remove1);
    verifyMethod.accept(verify(listener2, times(2)));
    model.removeListener(listener1);
    model.removeListener(listener2);
  }

  public void testListenersModifyListenerList() throws Exception {
    XmlFile modelXml = (XmlFile)myFixture.addFileToProject("res/layout/model.xml",
                                                           "<LinearLayout" +
                                                           "         xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
                                                           "         android:layout_width=\"match_parent\"" +
                                                           "         android:layout_height=\"match_parent\">" +
                                                           "</LinearLayout>");
    NlModel model = SyncNlModel.create(createSurface(NlDesignSurface.class), myFixture.getProject(), myFacet, modelXml.getVirtualFile());

    notifyAndCheckListeners(model, NlModel::notifyListenersModelUpdateComplete, listener -> listener.modelDerivedDataChanged(any()));
    notifyAndCheckListeners(model, m -> m.notifyModified(NlModel.ChangeType.EDIT), listener -> listener.modelChanged(any()));
  }

  public void testActivateDeactivateListeners() throws Exception {
    XmlFile modelXml = (XmlFile)myFixture.addFileToProject("res/layout/model.xml",
                                                           "<LinearLayout" +
                                                           "         xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
                                                           "         android:layout_width=\"match_parent\"" +
                                                           "         android:layout_height=\"match_parent\">" +
                                                           "</LinearLayout>");
    NlModel model = SyncNlModel.create(createSurface(NlDesignSurface.class), myFixture.getProject(), myFacet, modelXml.getVirtualFile());
    ModelListener listener1 = mock(ModelListener.class);
    ModelListener remove1 = mock(ModelListener.class, invocation -> {
      model.removeListener((ModelListener)invocation.getMock());
      return null;
    });
    ModelListener listener2 = mock(ModelListener.class);
    model.addListener(listener1);
    model.addListener(remove1);
    model.addListener(listener2);

    model.activate(this);
    verify(listener1).modelActivated(any());
    verify(remove1).modelActivated(any());
    verify(listener2).modelActivated(any());
    ModelListener remove2 = mock(ModelListener.class, invocation -> {
      model.removeListener((ModelListener)invocation.getMock());
      return null;
    });
    model.addListener(remove2);
    model.deactivate(this);
    verify(listener1).modelDeactivated(any());
    verify(listener2).modelDeactivated(any());
    verify(remove2).modelDeactivated(any());

    verifyNoMoreInteractions(remove1);
  }

  public void testMultiActivateDeactivateListeners() throws Exception {
    XmlFile modelXml = (XmlFile)myFixture.addFileToProject("res/layout/model.xml",
                                                           "<LinearLayout" +
                                                           "         xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
                                                           "         android:layout_width=\"match_parent\"" +
                                                           "         android:layout_height=\"match_parent\">" +
                                                           "</LinearLayout>");
    NlModel model = SyncNlModel.create(createSurface(NlDesignSurface.class), myFixture.getProject(), myFacet, modelXml.getVirtualFile());
    ModelListener listener1 = mock(ModelListener.class);
    model.addListener(listener1);

    Object sourceA = new Object();
    Object sourceB = new Object();
    model.activate(sourceA);
    model.activate(sourceB);
    verify(listener1).modelActivated(any());

    model.deactivate(sourceB);
    model.deactivate(sourceB);
    // Only one of the two sources was deactivated so do not expect any deactivate calls to the listeners
    verifyZeroInteractions(listener1);

    model.deactivate(sourceA);
    verify(listener1).modelDeactivated(any());
    verifyNoMoreInteractions(listener1);
  }

  public void testInvalidTag() {
    // Regression test for b/37324684
    ModelBuilder modelBuilder = createDefaultModelBuilder(false);
    SyncNlModel model = modelBuilder.build();
    SyncLayoutlibSceneManager manager = new SyncLayoutlibSceneManager(model);
    XmlFile file = model.getFile();
    XmlTag oldTag = file.getRootTag();

    // Force tag invalidation by deleting it
    WriteCommandAction.runWriteCommandAction(getProject(), oldTag::delete);
    LayoutlibSceneManager.updateHierarchy(oldTag, Collections.emptyList(), model);
  }

  public void testModelVersion() {
    XmlFile modelXml = (XmlFile)myFixture.addFileToProject("res/layout/model_version.xml",
                                                           "<RelativeLayout" +
                                                           "         xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
                                                           "         android:layout_width=\"match_parent\"" +
                                                           "         android:layout_height=\"match_parent\">" +
                                                           "</RelativeLayout>");
    NlModel model = SyncNlModel.create(createSurface(NlDesignSurface.class), myFixture.getProject(), myFacet, modelXml.getVirtualFile());

    long expectedModificationCount = model.getModificationCount();
    for (NlModel.ChangeType changeType : NlModel.ChangeType.values()) {
      model.notifyModified(changeType);
      expectedModificationCount += 1;
      assertEquals(expectedModificationCount, model.getModificationCount());
    }
  }

  @Override
  public void tearDown() throws Exception {
    super.tearDown();
  }

  private ModelBuilder createDefaultModelBuilder(boolean includeIds) {
    return model("linear.xml", component(LINEAR_LAYOUT)
      .withBounds(0, 0, 1000, 1000)
      .matchParentWidth()
      .matchParentHeight()
      .children(
        component(TEXT_VIEW)
          .withBounds(100, 100, 100, 100)
          .id(includeIds ? "@id/myText1" : "")
          .width("100dp")
          .height("100dp"),
        component(BUTTON)
          .withBounds(100, 200, 100, 100)
          .id(includeIds ? "@id/myText2" : "")
          .width("100dp")
          .height("100dp")
          .withAttribute("android:layout_weight", "1.0")
      ));
  }
}
