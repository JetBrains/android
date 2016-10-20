/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.uibuilder.LayoutTestCase;
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.util.NlTreeDumper;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Mock;

import javax.swing.tree.TreePath;
import java.awt.datatransfer.Transferable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class NlComponentTreeTest extends LayoutTestCase {
  @Mock
  private DesignSurface mySurface;
  @Mock
  private ScreenView myScreen;
  @Mock
  private CopyPasteManager myCopyPasteManager;
  private NlModel myModel;
  private NlComponentTree myTree;
  private NlComponent myRelativeLayout;
  private NlComponent myLinearLayout;
  private NlComponent myButton;
  private NlComponent myTextView;
  private NlComponent myAbsoluteLayout;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myModel = createModel();
    when(myScreen.getModel()).thenReturn(myModel);
    when(myScreen.getSelectionModel()).thenReturn(myModel.getSelectionModel());
    when(mySurface.getCurrentScreenView()).thenReturn(myScreen);
    when(mySurface.getProject()).thenReturn(getProject());
    myTree = new NlComponentTree(mySurface, myCopyPasteManager);

    myRelativeLayout = findFirst(RELATIVE_LAYOUT);
    myLinearLayout = findFirst(LINEAR_LAYOUT);
    myButton = findFirst(BUTTON);
    myTextView = findFirst(TEXT_VIEW);
    myAbsoluteLayout = findFirst(ABSOLUTE_LAYOUT);
  }

  @NotNull
  private NlComponent findFirst(@NotNull String tagName) {
    NlComponent component = findFirst(tagName, myModel.getComponents());
    assert component != null;
    return component;
  }

  @Nullable
  private static NlComponent findFirst(@NotNull String tagName, @NotNull List<NlComponent> components) {
    for (NlComponent component : components) {
      if (component.getTagName().equals(tagName)) {
        return component;
      }
      NlComponent child = findFirst(tagName, component.getChildren());
      if (child != null) {
        return child;
      }
    }
    return null;
  }

  public void testTreeStructure() {
    UIUtil.dispatchAllInvocationEvents();
    assertEquals("<RelativeLayout>  [expanded]\n" +
                 "    <LinearLayout>  [expanded]\n" +
                 "        <Button>\n" +
                 "    <TextView>\n" +
                 "    <AbsoluteLayout>\n", toTree());
  }

  public void testTreeStructureOfAppBar() {
    NlModel model = createModelWithAppBar();
    when(myScreen.getModel()).thenReturn(model);
    when(myScreen.getSelectionModel()).thenReturn(model.getSelectionModel());
    myTree.setDesignSurface(mySurface);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals("<android.support.design.widget.CoordinatorLayout>  [expanded]\n" +
                 "    <android.support.design.widget.AppBarLayout>  [expanded]\n" +
                 "        <android.support.design.widget.CollapsingToolbarLayout>  [expanded]\n" +
                 "            <ImageView>\n" +
                 "            <android.support.v7.widget.Toolbar>\n" +
                 "    <android.support.v4.widget.NestedScrollView>  [expanded]\n" +
                 "        <TextView>\n", toTree());
  }

  public void testSelectionInTreeIsPropagatedToModel() {
    assertNull(myTree.getSelectionPaths());
    assertFalse(myModel.getSelectionModel().getSelection().iterator().hasNext());

    NlComponent root = (NlComponent)myTree.getModel().getRoot();
    NlComponent node1 = root.getChild(1);
    NlComponent node2 = root.getChild(2);
    myTree.addSelectionPath(new TreePath(new Object[]{root, node1}));
    myTree.addSelectionPath(new TreePath(new Object[]{root, node2}));

    Iterator<NlComponent> selected = myModel.getSelectionModel().getSelection().iterator();
    assertEquals(node1, selected.next());
    assertEquals(node2, selected.next());
    assertFalse(selected.hasNext());
  }

  public void testSelectionInModelIsShownInTree() {
    assertNull(myTree.getSelectionPaths());
    assertFalse(myModel.getSelectionModel().getSelection().iterator().hasNext());

    myModel.getSelectionModel().toggle(myTextView);
    myModel.getSelectionModel().toggle(myButton);

    TreePath[] selection = myTree.getSelectionPaths();
    assertThat(selection).isNotNull();

    assertThat(selection.length).isEqualTo(2);
    assertThat(selection[0].getLastPathComponent()).isSameAs(myTextView);
    assertThat(selection[1].getLastPathComponent()).isSameAs(myButton);
  }

  @SuppressWarnings("UnnecessaryLocalVariable")
  public void testHierarchyUpdate() {
    // Extract xml
    XmlTag tagLinearLayout = myLinearLayout.getTag();
    XmlTag tagTextView = myTextView.getTag();
    XmlTag tagAbsoluteLayout = myAbsoluteLayout.getTag();

    // Mix the component references
    myRelativeLayout.children = null;
    myLinearLayout.setTag(tagAbsoluteLayout);
    myLinearLayout.setSnapshot(null);
    myLinearLayout.children = null;
    myTextView.setTag(tagLinearLayout);
    myTextView.setSnapshot(null);
    myTextView.children = null;
    myAbsoluteLayout.setTag(tagTextView);
    myAbsoluteLayout.setSnapshot(null);
    myAbsoluteLayout.children = null;

    NlComponent newRelativeLayout = myRelativeLayout;
    NlComponent newLinearLayout = myTextView;
    NlComponent newButton = myButton;
    NlComponent newTextView = myAbsoluteLayout;
    NlComponent newAbsoluteLayout = myLinearLayout;
    newRelativeLayout.addChild(newLinearLayout);
    newRelativeLayout.addChild(newTextView);
    newRelativeLayout.addChild(newAbsoluteLayout);

    assert newButton != null;
    newLinearLayout.addChild(newButton);

    myTree.modelChanged(myModel);
    UIUtil.dispatchAllInvocationEvents();
    assertEquals("<RelativeLayout>  [expanded]\n" +
                 "    <LinearLayout>\n" +
                 "        <Button>\n" +
                 "    <TextView>\n" +
                 "    <AbsoluteLayout>\n", toTree());
  }

  public void testCopyIsNotAvailableWhenNothingIsSelected() {
    DataContext context = mock(DataContext.class);
    assertThat(myTree.isCopyVisible(context)).isTrue();
    assertThat(myTree.isCopyEnabled(context)).isFalse();
    myTree.performCopy(context);
    verifyZeroInteractions(myCopyPasteManager);
  }

  public void testCopyWithOneComponentSelected() {
    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().toggle(myTextView);
    assertThat(myTree.isCopyVisible(context)).isTrue();
    assertThat(myTree.isCopyEnabled(context)).isTrue();
    myTree.performCopy(context);
    verify(myCopyPasteManager).setContents(notNull(Transferable.class));
  }

  public void testPasteIsNotPossibleWhenMultipleComponentsAreSelected() {
    copy(myTextView);

    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().toggle(myLinearLayout);
    myModel.getSelectionModel().toggle(myAbsoluteLayout);
    assertThat(myTree.isPasteEnabled(context)).isTrue();
    assertThat(myTree.isPastePossible(context)).isFalse();
    myTree.performPaste(context);
    assertThat(toTree()).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                   "    <LinearLayout>  [expanded]  [selected]\n" +
                                   "        <Button>\n" +
                                   "    <TextView>\n" +
                                   "    <AbsoluteLayout>  [selected]\n");
  }

  public void testCopyIntoRootWhenNothingIsSelected() {
    copy(myTextView);

    DataContext context = mock(DataContext.class);
    assertThat(myTree.isPasteEnabled(context)).isTrue();
    assertThat(myTree.isPastePossible(context)).isTrue();
    myTree.performPaste(context);
    assertThat(toTree()).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                   "    <TextView>\n" +
                                   "    <LinearLayout>  [expanded]\n" +
                                   "        <Button>\n" +
                                   "    <TextView>\n" +
                                   "    <AbsoluteLayout>\n");
  }

  public void testPasteIntoLayoutAsFirstChild() {
    copy(myTextView);

    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().toggle(myLinearLayout);
    assertThat(myTree.isPasteEnabled(context)).isTrue();
    assertThat(myTree.isPastePossible(context)).isTrue();
    myTree.performPaste(context);
    assertThat(toTree()).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                   "    <LinearLayout>  [expanded]  [selected]\n" +
                                   "        <TextView>\n" +
                                   "        <Button>\n" +
                                   "    <TextView>\n" +
                                   "    <AbsoluteLayout>\n");
  }

  public void testPasteIntoParentAfterButton() {
    copy(myTextView);

    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().toggle(myButton);
    assertThat(myTree.isPasteEnabled(context)).isTrue();
    assertThat(myTree.isPastePossible(context)).isTrue();
    myTree.performPaste(context);
    assertThat(toTree()).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                   "    <LinearLayout>  [expanded]\n" +
                                   "        <Button>  [selected]\n" +
                                   "        <TextView>\n" +
                                   "    <TextView>\n" +
                                   "    <AbsoluteLayout>\n");
  }

  public void testCopyMultiple() {
    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().toggle(myTextView);
    myModel.getSelectionModel().toggle(myButton);
    assertThat(myTree.isCopyVisible(context)).isTrue();
    assertThat(myTree.isCopyEnabled(context)).isTrue();
    myTree.performCopy(context);
    verify(myCopyPasteManager).setContents(notNull(Transferable.class));
  }

  public void testPasteMultipleIntoLayout() {
    copy(myTextView, myButton);

    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().toggle(myAbsoluteLayout);
    assertThat(myTree.isPasteEnabled(context)).isTrue();
    assertThat(myTree.isPastePossible(context)).isTrue();
    myTree.performPaste(context);
    assertThat(toTree()).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                   "    <LinearLayout>  [expanded]\n" +
                                   "        <Button>\n" +
                                   "    <TextView>\n" +
                                   "    <AbsoluteLayout>  [selected]\n" +
                                   "        <TextView>\n" +
                                   "        <Button>\n");
  }

  public void testCutRemovesComponents() {
    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().toggle(myTextView);
    assertThat(myTree.isCutVisible(context)).isTrue();
    assertThat(myTree.isCutEnabled(context)).isTrue();
    myTree.performCut(context);
    verify(myCopyPasteManager).setContents(notNull(Transferable.class));
    assertThat(toTree()).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                   "    <LinearLayout>  [expanded]\n" +
                                   "        <Button>\n" +
                                   "    <AbsoluteLayout>\n");
  }

  public void testPasteAfterCut() {
    cut(myTextView);

    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().clear();
    myModel.getSelectionModel().toggle(myButton);
    assertThat(myTree.isPasteEnabled(context)).isTrue();
    assertThat(myTree.isPastePossible(context)).isTrue();
    myTree.performPaste(context);
    assertThat(toTree()).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                   "    <LinearLayout>  [expanded]\n" +
                                   "        <Button>  [selected]\n" +
                                   "        <TextView>\n" +
                                   "    <AbsoluteLayout>\n");
  }

  public void testDelete() {
    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().toggle(myTextView);
    assertThat(myTree.canDeleteElement(context)).isTrue();
    myTree.deleteElement(context);
    assertThat(CopyPasteManager.getInstance().getContents()).isNull();
    assertThat(toTree()).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                   "    <LinearLayout>  [expanded]\n" +
                                   "        <Button>\n" +
                                   "    <AbsoluteLayout>\n");
  }

  private void copy(@NotNull NlComponent... components) {
    myModel.getSelectionModel().setSelection(Arrays.asList(components));
    when(myCopyPasteManager.getContents()).thenReturn(myModel.getSelectionAsTransferable());
    myModel.getSelectionModel().clear();
  }

  private void cut(@NotNull NlComponent... components) {
    List<NlComponent> list = Arrays.asList(components);
    myModel.getSelectionModel().setSelection(list);
    when(myCopyPasteManager.getContents()).thenReturn(myModel.getSelectionAsTransferable());
    myModel.delete(list);
    myModel.getSelectionModel().clear();
  }

  private String toTree() {
    StringBuilder sb = new StringBuilder();
    describe(sb, new TreePath(myTree.getModel().getRoot()), 0);

    return sb.toString();
  }

  private void describe(StringBuilder sb, TreePath path, int depth) {
    for (int i = 0; i < depth; i++) {
      sb.append("    ");
    }

    NlComponent component = (NlComponent)path.getLastPathComponent();
    sb.append("<").append(component.getTagName())
      .append(">");
    if (myTree.isExpanded(path)) {
      sb.append("  [expanded]");
    }
    if (myTree.isPathSelected(path)) {
      sb.append("  [selected]");
    }
    sb.append("\n");
    for (Object subNodeAsObject : component.getChildren()) {
      TreePath subPath = path.pathByAddingChild(subNodeAsObject);
      describe(sb, subPath, depth + 1);
    }
  }

  @NotNull
  private NlModel createModel() {
    ModelBuilder builder = model("relative.xml",
                                 component(RELATIVE_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(LINEAR_LAYOUT)
                                       .withBounds(0, 0, 200, 200)
                                       .wrapContentWidth()
                                       .wrapContentHeight()
                                       .children(
                                         component(BUTTON)
                                           .withBounds(0, 0, 100, 100)
                                           .id("@+id/myButton")
                                           .width("100dp")
                                           .height("100dp")),
                                     component(TEXT_VIEW)
                                       .withBounds(0, 200, 100, 100)
                                       .id("@+id/myText")
                                       .width("100dp")
                                       .height("100dp"),
                                     component(ABSOLUTE_LAYOUT)
                                       .withBounds(0, 300, 400, 500)
                                       .width("400dp")
                                       .height("500dp")));
    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<RelativeLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<LinearLayout>, bounds=[0,0:200x200}\n" +
                 "        NlComponent{tag=<Button>, bounds=[0,0:100x100}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,200:100x100}\n" +
                 "    NlComponent{tag=<AbsoluteLayout>, bounds=[0,300:400x500}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    return model;
  }

  @NotNull
  private NlModel createModelWithAppBar() {
    ModelBuilder builder = model("coordinator.xml",
                                 component(COORDINATOR_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(APP_BAR_LAYOUT)
                                       .withBounds(0, 0, 1000, 192).matchParentWidth().height("192dp").children(
                                       component(COLLAPSING_TOOLBAR_LAYOUT).withBounds(0, 0, 1000, 192).matchParentHeight()
                                         .matchParentWidth()
                                         .children(component(IMAGE_VIEW).withBounds(0, 0, 1000, 192).matchParentWidth().matchParentHeight(),
                                                   component("android.support.v7.widget.Toolbar").withBounds(0, 0, 1000, 18)
                                                     .height("?attr/actionBarSize").matchParentWidth())),
                                     component(CLASS_NESTED_SCROLL_VIEW).withBounds(0, 192, 1000, 808).matchParentWidth()
                                       .matchParentHeight().withAttribute(AUTO_URI, "layout_behavior",
                                                                          "android.support.design.widget.AppBarLayout$ScrollingViewBehavior")
                                       .children(component(TEXT_VIEW).withBounds(0, 192, 1000, 808).matchParentWidth().wrapContentHeight()
                                                   .text("@string/stuff"))));

    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<android.support.design.widget.CoordinatorLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<android.support.design.widget.AppBarLayout>, bounds=[0,0:1000x192}\n" +
                 "        NlComponent{tag=<android.support.design.widget.CollapsingToolbarLayout>, bounds=[0,0:1000x192}\n" +
                 "            NlComponent{tag=<ImageView>, bounds=[0,0:1000x192}\n" +
                 "            NlComponent{tag=<android.support.v7.widget.Toolbar>, bounds=[0,0:1000x18}\n" +
                 "    NlComponent{tag=<android.support.v4.widget.NestedScrollView>, bounds=[0,192:1000x808}\n" +
                 "        NlComponent{tag=<TextView>, bounds=[0,192:1000x808}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    return model;
  }
}