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
import com.android.tools.idea.uibuilder.LayoutTestUtilities;
import com.android.tools.idea.common.SyncNlModel;
import com.android.tools.idea.uibuilder.fixtures.DropTargetDropEventBuilder;
import com.android.tools.idea.common.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.handlers.constraint.ConstraintHelperHandler;
import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.uibuilder.surface.NlDesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.android.tools.idea.uibuilder.util.JavaDocViewer;
import com.android.tools.idea.common.util.NlTreeDumper;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.psi.PsiClass;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

import static com.android.SdkConstants.*;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.createScreen;
import static com.android.tools.idea.uibuilder.LayoutTestUtilities.findActionForKey;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

public class NlComponentTreeTest extends LayoutTestCase {
  @Mock
  private NlDesignSurface mySurface;
  @Mock
  private ScreenView myScreen;
  @Mock
  private CopyPasteManager myCopyPasteManager;
  @Mock
  private JavaDocViewer myJavaDocViewer;
  private SyncNlModel myModel;
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
    myScreen = createScreen(myModel);
    when(mySurface.getCurrentSceneView()).thenReturn(myScreen);
    when(mySurface.getProject()).thenReturn(getProject());
    myTree = new NlComponentTree(getProject(), mySurface, myCopyPasteManager);
    registerApplicationComponent(JavaDocViewer.class, myJavaDocViewer);

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

  @Override
  public void tearDown() throws Exception {
    try {
      Disposer.dispose(myModel);
      // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
      // tests through IJ)
      myCopyPasteManager = null;
      myJavaDocViewer = null;
      myRelativeLayout = null;
      myLinearLayout = null;
      myButton = null;
      myTextView = null;
      myAbsoluteLayout = null;
      myScreen = null;
      mySurface = null;
      myModel = null;
      myTree = null;
    }
    finally {
      super.tearDown();
    }
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
    SyncNlModel model = createModelWithAppBar();
    myScreen = createScreen(model);
    when(mySurface.getCurrentSceneView()).thenReturn(myScreen);
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

    myTree.modelDerivedDataChanged(myModel);
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


  public void testDropOnChain() {
    myModel = createModelWithConstraintLayout();
    myScreen = createScreen(myModel);
    when(mySurface.getCurrentSceneView()).thenReturn(myScreen);
    when(mySurface.getProject()).thenReturn(getProject());
    myTree = new NlComponentTree(getProject(), mySurface, myCopyPasteManager);
    NlComponent chain = findFirst(CLASS_CONSTRAINT_LAYOUT_CHAIN);
    copy(myButton);
    DataContext context = mock(DataContext.class);
    myModel.getSelectionModel().toggle(chain);
    assertThat(myTree.isPasteEnabled(context)).isTrue();
    assertThat(myTree.isPastePossible(context)).isTrue();
    myTree.performPaste(context);
    assertThat(toTree()).isEqualTo("<android.support.constraint.ConstraintLayout>  [expanded]\n" +
                                   "    <Button>\n" +
                                   "    <Button>\n" +
                                   "    <Button>\n" +
                                   "    <android.support.constraint.Chain>  [selected]\n" +
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
    verifyZeroInteractions(myCopyPasteManager);
    assertThat(toTree()).isEqualTo("<RelativeLayout>  [expanded]\n" +
                                   "    <LinearLayout>  [expanded]\n" +
                                   "        <Button>\n" +
                                   "    <AbsoluteLayout>\n");
  }

  public void testShiftHelpOnComponentTree() throws Exception {
    AnAction action = findActionForKey(myTree, KeyEvent.VK_F1, InputEvent.SHIFT_MASK);

    assertThat(action).isNotNull();

    DataContext context = mock(DataContext.class);
    AnActionEvent event = mock(AnActionEvent.class);
    when(event.getDataContext()).thenReturn(context);
    ArgumentCaptor<PsiClass> psiClassCaptor = ArgumentCaptor.forClass(PsiClass.class);

    myModel.getSelectionModel().toggle(myTextView);
    action.actionPerformed(event);
    verify(myJavaDocViewer).showExternalJavaDoc(psiClassCaptor.capture(), eq(context));
    assertThat(psiClassCaptor.getValue().getQualifiedName()).isEqualTo("android.widget.TextView");
  }

  private void copy(@NotNull NlComponent... components) {
    myModel.getSelectionModel().setSelection(Arrays.asList(components));
    Transferable selection = myModel.getSelectionAsTransferable();
    when(myCopyPasteManager.getContents()).thenReturn(selection);
    myModel.getSelectionModel().clear();
  }

  private void cut(@NotNull NlComponent... components) {
    List<NlComponent> list = Arrays.asList(components);
    myModel.getSelectionModel().setSelection(list);
    Transferable selection = myModel.getSelectionAsTransferable();
    when(myCopyPasteManager.getContents()).thenReturn(selection);
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
  private SyncNlModel createModel() {
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
    final SyncNlModel model = builder.build();
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
  private SyncNlModel createModelWithAppBar() {
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

    final SyncNlModel model = builder.build();
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

  @NotNull
  private SyncNlModel createModelWithConstraintLayout() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button2")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button3")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button1")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(CLASS_CONSTRAINT_LAYOUT_CHAIN)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/chain")
                                       .wrapContentWidth()
                                       .wrapContentHeight()));
    final SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<android.support.constraint.ConstraintLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<android.support.constraint.Chain>, bounds=[0,0:200x200}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    return model;
  }

  public void testNonNlComponentDrop() {
    assertNull(myTree.getSelectionPaths());
    myModel = createModelWithBarriers();
    myScreen = createScreen(myModel);
    when(mySurface.getCurrentSceneView()).thenReturn(myScreen);
    when(mySurface.getProject()).thenReturn(getProject());
    myTree = new NlComponentTree(getProject(), mySurface, myCopyPasteManager);

    // Check initial state
    myTree.expandRow(3);
    TreePath pathForRow4 = myTree.getPathForRow(4);
    TreePath pathForRow5 = myTree.getPathForRow(5);
    assertThat(pathForRow4.getLastPathComponent()).isEqualTo("button2");
    assertThat(pathForRow5.getLastPathComponent()).isEqualTo("button3");

    myTree.setSelectionPath(pathForRow4);
    Rectangle bounds1 = myTree.getPathBounds(pathForRow4);
    Rectangle bounds2 = myTree.getPathBounds(pathForRow5);
    assertNotNull(bounds1);
    assertNotNull(bounds2);

    // Perform the drop
    DelegatedTreeEventHandler handler = NlTreeUtil.getSelectionTreeHandler(myTree);
    assertNotNull(handler);
    DropTargetDropEvent dropEvent =
      new DropTargetDropEventBuilder(LayoutTestUtilities.createDropTargetContext(), bounds2.x, (int)bounds2.getCenterY(),
                                     handler.getTransferable(myTree.getSelectionPaths())).build();

    // We directly crate and call the listener because we cannot setup the
    // drag and drop in BuildBot since there is no X11 server
    NlDropListener listener = new NlDropListener(myTree);
    listener.drop(dropEvent);

    // Check that the model is changed
    Object child1 = ((ConstraintHelperHandler)handler).getComponentTreeChild(myTree.getPathForRow(3).getLastPathComponent(), 0);
    Object child2 = ((ConstraintHelperHandler)handler).getComponentTreeChild(myTree.getPathForRow(3).getLastPathComponent(), 1);
    assertThat(child1).isEqualTo("button3");
    assertThat(child2).isEqualTo("button2");

    // We manually notify the model changed event to ensure that the paths are updated
    myTree.modelDerivedDataChanged(myModel);
    myTree.expandRow(3); // Ensure that the the barrier's children path are not collapsed

    // Check that button2 and button3 are swapped
    pathForRow4 = myTree.getPathForRow(4);
    pathForRow5 = myTree.getPathForRow(5);
    assertThat(pathForRow4.getLastPathComponent()).isEqualTo("button3");
    assertThat(pathForRow5.getLastPathComponent()).isEqualTo("button2");
  }

  @NotNull
  private SyncNlModel createModelWithBarriers() {
    ModelBuilder builder = model("constraint.xml",
                                 component(CONSTRAINT_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button2")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(BUTTON)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/button3")
                                       .wrapContentWidth()
                                       .wrapContentHeight(),
                                     component(CLASS_CONSTRAINT_LAYOUT_HELPER)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/barrier")
                                       .wrapContentWidth()
                                       .wrapContentHeight()
                                       .withAttribute(AUTO_URI, ATTR_BARRIER_DIRECTION, CONSTRAINT_BARRIER_END)
                                       .withAttribute(AUTO_URI, CONSTRAINT_REFERENCED_IDS, "button2,button3"),
                                     component(TEXT_VIEW)
                                       .withBounds(0, 0, 200, 200)
                                       .id("@+id/chain")
                                       .wrapContentWidth()
                                       .wrapContentHeight()
                                       .withAttribute(AUTO_URI, ATTR_LAYOUT_START_TO_END_OF, "@id/barrier")
                                   ));
    final SyncNlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<android.support.constraint.ConstraintLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<Button>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<android.support.constraint.ConstraintHelper>, bounds=[0,0:200x200}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[0,0:200x200}",
                 NlTreeDumper.dumpTree(model.getComponents()));
    return model;
  }
}
