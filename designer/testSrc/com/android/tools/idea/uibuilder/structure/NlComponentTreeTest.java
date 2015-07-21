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
import org.jetbrains.annotations.NotNull;
import org.mockito.Mock;

import javax.swing.tree.DefaultMutableTreeNode;
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
    myTree = new NlComponentTree(mySurface);
  }

  public void testTreeStructure() {
    NlComponent expectedRoot = myModel.getComponents().get(0);
    DefaultMutableTreeNode hidden = (DefaultMutableTreeNode)myTree.getModel().getRoot();
    assertEquals(1, hidden.getChildCount());
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)hidden.getFirstChild();
    assertEquals("Unexpected root", expectedRoot, root.getUserObject());
    assertEquals(expectedRoot.getChildCount(), root.getChildCount());
    for (int i=0; i<root.getChildCount(); i++) {
      assertEquals(expectedRoot.getChild(i), ((DefaultMutableTreeNode)root.getChildAt(i)).getUserObject());
      assertEquals(0, root.getChildAt(i).getChildCount());
    }
  }

  public void testSelectionInTreeIsPropagatedToModel() {
    assertNull(myTree.getSelectionPaths());
    assertFalse(myModel.getSelectionModel().getSelection().iterator().hasNext());

    DefaultMutableTreeNode hidden = (DefaultMutableTreeNode)myTree.getModel().getRoot();
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)hidden.getFirstChild();
    DefaultMutableTreeNode node1 = (DefaultMutableTreeNode)root.getChildAt(1);
    DefaultMutableTreeNode node2 = (DefaultMutableTreeNode)root.getChildAt(2);
    myTree.addSelectionPath(new TreePath(node1.getPath()));
    myTree.addSelectionPath(new TreePath(node2.getPath()));

    Iterator<NlComponent> selected = myModel.getSelectionModel().getSelection().iterator();
    assertEquals(node1.getUserObject(), selected.next());
    assertEquals(node2.getUserObject(), selected.next());
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
    assertEquals(2, selection.length);
    assertEquals(text, ((DefaultMutableTreeNode)selection[0].getLastPathComponent()).getUserObject());
    assertEquals(button, ((DefaultMutableTreeNode)selection[1].getLastPathComponent()).getUserObject());
  }

  @NotNull
  private NlModel createModel() {
    ModelBuilder builder = model("linear.xml",
                                 component(LINEAR_LAYOUT)
                                   .withBounds(0, 0, 1000, 1000)
                                   .matchParentWidth()
                                   .matchParentHeight()
                                   .children(
                                     component(TEXT_VIEW)
                                       .withBounds(100, 100, 100, 100)
                                       .id("myText")
                                       .width("100dp")
                                       .height("100dp"),
                                     component(BUTTON)
                                       .withBounds(100, 200, 100, 100)
                                       .id("myButton")
                                       .width("100dp")
                                       .height("100dp"),
                                     component(RADIO_BUTTON)
                                       .withBounds(100, 100, 100, 100)
                                       .id("myRadioButton")
                                       .width("400dp")
                                       .height("100dp")
                                   ));
    final NlModel model = builder.build();
    assertEquals(1, model.getComponents().size());
    assertEquals("NlComponent{tag=<LinearLayout>, bounds=[0,0:1000x1000}\n" +
                 "    NlComponent{tag=<TextView>, bounds=[100,100:100x100}\n" +
                 "    NlComponent{tag=<Button>, bounds=[100,200:100x100}\n" +
                 "    NlComponent{tag=<RadioButton>, bounds=[100,100:100x100}",
                 NlComponent.toTree(model.getComponents()));
    return model;
  }
}