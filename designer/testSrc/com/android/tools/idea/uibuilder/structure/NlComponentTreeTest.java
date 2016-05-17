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
import com.android.tools.idea.uibuilder.fixtures.ModelBuilder;
import com.android.tools.idea.uibuilder.model.NlComponent;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import javax.swing.tree.TreePath;
import java.util.Iterator;

import static com.android.SdkConstants.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

public class NlComponentTreeTest extends LayoutTestCase {
  @Mock
  private DesignSurface mySurface;
  @Mock
  private ScreenView myScreen;
  private NlModel myModel;
  private NlComponentTree myTree;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    initMocks(this);
    myModel = createModel();
    when(myScreen.getModel()).thenReturn(myModel);
    when(myScreen.getSelectionModel()).thenReturn(myModel.getSelectionModel());
    when(mySurface.getCurrentScreenView()).thenReturn(myScreen);
    when(mySurface.getProject()).thenReturn(getProject());
    myTree = new NlComponentTree(mySurface);
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

    NlComponent layout = myModel.getComponents().get(0);
    NlComponent text = layout.getChild(0);
    NlComponent button = layout.getChild(1);
    assert text != null;
    assert button != null;
    myModel.getSelectionModel().toggle(text);
    myModel.getSelectionModel().toggle(button);

    TreePath[] selection = myTree.getSelectionPaths();
    assert selection != null;

    assertEquals(2, selection.length);
    assertEquals(text, selection[0].getLastPathComponent());
    assertEquals(button, selection[1].getLastPathComponent());
  }

  @SuppressWarnings("UnnecessaryLocalVariable")
  public void testHierarchyUpdate() {
    // Extract each of the components
    NlComponent oldRelativeLayout = myModel.getComponents().get(0);

    NlComponent oldLinearLayout = oldRelativeLayout.getChild(0);
    assert oldLinearLayout != null;

    NlComponent oldButton = oldLinearLayout.getChild(0);

    NlComponent oldTextView = oldRelativeLayout.getChild(1);
    assert oldTextView != null;

    NlComponent oldAbsoluteLayout = oldRelativeLayout.getChild(2);
    assert oldAbsoluteLayout != null;

    // Extract xml
    XmlTag tagLinearLayout = oldLinearLayout.getTag();
    XmlTag tagTextView = oldTextView.getTag();
    XmlTag tagAbsoluteLayout = oldAbsoluteLayout.getTag();

    // Mix the component references
    oldRelativeLayout.children = null;
    oldLinearLayout.setTag(tagAbsoluteLayout);
    oldLinearLayout.setSnapshot(null);
    oldLinearLayout.children = null;
    oldTextView.setTag(tagLinearLayout);
    oldTextView.setSnapshot(null);
    oldTextView.children = null;
    oldAbsoluteLayout.setTag(tagTextView);
    oldAbsoluteLayout.setSnapshot(null);
    oldAbsoluteLayout.children = null;

    NlComponent newRelativeLayout = oldRelativeLayout;
    NlComponent newLinearLayout = oldTextView;
    NlComponent newButton = oldButton;
    NlComponent newTextView = oldAbsoluteLayout;
    NlComponent newAbsoluteLayout = oldLinearLayout;
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
                 LayoutTestUtilities.toTree(model.getComponents()));
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
                 LayoutTestUtilities.toTree(model.getComponents()));
    return model;
  }
}