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
package com.android.tools.idea.uibuilder.structure;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.testing.AndroidProjectRule;
import com.google.common.collect.ImmutableList;
import com.intellij.designer.model.EmptyXmlTag;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.List;
import javax.swing.JTree;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@SuppressWarnings("ConstantConditions")
public class NlDropInsertionPickerTest {

  private static final int NODE_SIZE = 10;
  private static final int COMPONENT_NUMBER = 10;
  private static final int LAST_COMPONENT_Y_POSITION = COMPONENT_NUMBER * NODE_SIZE - NODE_SIZE / 2;

  @Rule
  public final AndroidProjectRule myRule = AndroidProjectRule.inMemory();

  @Mock
  private NlModel myModel;

  private FakeNlComponentGroup ourRoot;
  private FakeTreePath[] myTreePaths;
  private ImmutableList<NlComponent> myDragged;
  private NlDropInsertionPicker myPicker;

  @NotNull
  private NlDropInsertionPickerTest.FakeNlComponentGroup buildFakeComponentHierarchy() {
    return new FakeNlComponentGroup(0, myModel, false)
      .setChildren(
        new FakeNlComponent(1, myModel, true),
        new FakeNlComponent(2, myModel, true),
        new FakeNlComponentGroup(3, myModel, true)
          .setChildren(
            new FakeNlComponent(4, myModel, true),
            new FakeNlComponent(5, myModel, false)),
        new FakeNlComponentGroup(6, myModel, false)
          .setChildren(
            new FakeNlComponent(7, myModel, true),
            new FakeNlComponentGroup(8, myModel, true),
            new FakeNlComponent(9, myModel, false))
      );
  }

  /**
   * Build a list of tree path with mock coordinates using from the given component hierarchy as they would be in the {@link NlComponentTree}
   */
  @NotNull
  private static FakeTreePath[] buildFakeTreePathArray(@NotNull NlDropInsertionPickerTest.FakeNlComponent root) {

    ImmutableList.Builder<FakeTreePath> builder = ImmutableList.builder();
    buildFakeTreePathArray(builder, root, null, 0, 0);
    ImmutableList<FakeTreePath> pathsList = builder.build();
    return pathsList.toArray(new FakeTreePath[0]);
  }

  /**
   * Build a list of tree path with mock coordinates using from the given component hierarchy as they would be in the {@link NlComponentTree}
   *
   * @param parent       This component's parent's
   * @param builder      The builder used to build the list
   * @param current      The current {@link FakeNlComponent} to add to the list
   * @param currentRow   The row of the current component in the Tree
   * @param currentDepth The current depth of the component (used to compute the x coordinate of the component)
   * @return The row of the previously inserted {@link FakeTreePath}
   */
  private static int buildFakeTreePathArray(@NotNull ImmutableList.Builder<FakeTreePath> builder,
                                            @NotNull NlDropInsertionPickerTest.FakeNlComponent current,
                                            @Nullable TreePath parent,
                                            int currentRow,
                                            int currentDepth) {
    FakeTreePath path = new FakeTreePath(current, parent, currentDepth * NODE_SIZE);
    builder.add(path);
    int childRow = currentRow + 1;
    if (current instanceof FakeNlComponentGroup) {
      for (int i = 0; i < current.getChildCount(); i++) {
        childRow =
          buildFakeTreePathArray(builder, (FakeNlComponent)current.getChildren().get(i), path, childRow, currentDepth + 1);
      }
    }
    return childRow;
  }

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
    when(myModel.canAddComponents(anyList(), any(FakeNlComponent.class), any())).thenReturn(false);
    when(myModel.canAddComponents(anyList(), any(FakeNlComponent.class), any(), anyBoolean())).thenReturn(false);
    when(myModel.canAddComponents(anyList(), any(FakeNlComponentGroup.class), any())).thenReturn(true);
    when(myModel.canAddComponents(anyList(), any(FakeNlComponentGroup.class), any(), anyBoolean())).thenReturn(true);
    when(myModel.getProject()).thenReturn(myRule.getProject());

    ourRoot = buildFakeComponentHierarchy();
    myTreePaths = buildFakeTreePathArray(ourRoot);
    when(myModel.getComponents()).thenReturn(ImmutableList.of(ourRoot));

    myPicker = getDefaultPicker();
    myDragged = ImmutableList.of(new FakeNlComponent(-1, myModel, false));
  }

  @Test
  public void testFakeTree() {
    FakeTree tree = new FakeTree();
    assertEquals(myTreePaths[0], tree.getClosestPathForLocation(0, 0));
    assertEquals(ourRoot, tree.getClosestPathForLocation(0, 0).getLastPathComponent());
    assertEquals(myTreePaths[1], tree.getClosestPathForLocation(0, 10));
    assertEquals(ourRoot.getChild(0), tree.getClosestPathForLocation(0, 10).getLastPathComponent());
    assertEquals(ourRoot.getChild(1), tree.getClosestPathForLocation(0, 20).getLastPathComponent());
    assertEquals(ourRoot.getChild(3).getChild(2), tree.getClosestPathForLocation(0, 90).getLastPathComponent());
    assertEquals(myTreePaths[3], myTreePaths[4].getParentPath());
    assertTrue(myTreePaths[myTreePaths.length - 1].getLastPathComponent() instanceof FakeNlComponent);
    assertEquals(((FakeNlComponent)myTreePaths[myTreePaths.length - 1].getLastPathComponent()).myId, myTreePaths.length - 1);
  }

  @Test
  public void testInsertAtRoot() {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(0, 0), myDragged);
    assertEquals(ourRoot, result.receiver);
    assertEquals(ourRoot.getChild(0), result.nextComponent);
    assertEquals(1, result.depth);
    assertEquals(0, result.row);
  }

  @Test
  public void testInsertLast() {

    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(30, LAST_COMPONENT_Y_POSITION), myDragged);
    assertEquals(ourRoot.getChild(3), result.receiver);
    assertNull(result.nextComponent);
    assertEquals(0, result.depth);
    assertEquals(COMPONENT_NUMBER - 1, result.row);
  }

  @Test
  public void testInsertParentFromLast() {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(5, LAST_COMPONENT_Y_POSITION), myDragged);
    assertEquals(ourRoot, result.receiver);
    assertNull(result.nextComponent);
    assertEquals(-1, result.depth);
    assertEquals(COMPONENT_NUMBER - 1, result.row);
  }

  @Test
  public void testInsertInViewGroup() {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(15, 35), myDragged);
    assertEquals(ourRoot.getChild(2), result.receiver);
    assertEquals(ourRoot.getChild(2).getChild(0), result.nextComponent);
    assertEquals(1, result.depth);
    assertEquals(3, result.row);
  }

  @Test
  public void testInsertInEmptyViewGroup() {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(15, 85), myDragged);
    assertEquals(ourRoot.getChild(3).getChild(1), result.receiver);
    assertNull(result.nextComponent);
    assertEquals(1, result.depth);
    assertEquals(8, result.row);
  }

  @Test
  public void testInsertBetween() {
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(15, 15), myDragged);
    assertEquals(ourRoot, result.receiver);
    assertEquals(ourRoot.getChild(1), result.nextComponent);
    assertEquals(0, result.depth);
    assertEquals(1, result.row);
  }

  @Test
  public void testInsertInParentIfLastIsViewGroup() {
    FakeNlComponentGroup root =
      new FakeNlComponentGroup(0, myModel, false).setChildren(
        new FakeNlComponent(1, myModel, true),
        new FakeNlComponentGroup(2, myModel, false));

    myTreePaths = buildFakeTreePathArray(root);
    FakeTree tree = new FakeTree();
    tree.collapsePath(myTreePaths[myTreePaths.length - 1]);
    NlDropInsertionPicker picker = new NlDropInsertionPicker(tree);
    NlDropInsertionPicker.Result result = picker.findInsertionPointAt(new Point(5, 25), myDragged);
    assertEquals(root, result.receiver);
    assertNull(result.nextComponent);
    assertEquals(0, result.depth);
    assertEquals(2, result.row);
  }

  @Test
  public void testInsertRowIsAfterChildren() {
    NlComponent receiver = ourRoot.getChild(2);
    when(myModel.canAddComponents(eq(myDragged), eq(receiver), any())).thenReturn(false);
    when(myModel.canAddComponents(eq(myDragged), eq(receiver), any(), anyBoolean())).thenReturn(false);
    assertFalse(myModel.canAddComponents(myDragged, receiver, null));
    NlDropInsertionPicker.Result result = myPicker.findInsertionPointAt(new Point(15, 35), myDragged);
    assertEquals(ourRoot, result.receiver);
    assertEquals(ourRoot.getChild(3), result.nextComponent);
    assertEquals(0, result.depth);
    assertEquals(5, result.row);
  }

  @Test
  public void testInsertRowIsAfterGrandChildren() {
    FakeNlComponentGroup root =
      new FakeNlComponentGroup(0, myModel, false).setChildren(
        new FakeNlComponentGroup(1, myModel, false).setChildren(
          new FakeNlComponentGroup(2, myModel, false).setChildren(
            new FakeNlComponent(3, myModel, true),
            new FakeNlComponent(4, myModel, false))));

    myTreePaths = buildFakeTreePathArray(root);
    FakeTree tree = new FakeTree();
    NlComponent receiver = root.getChild(0);
    when(myModel.canAddComponents(eq(myDragged), eq(receiver), any())).thenReturn(false);
    when(myModel.canAddComponents(eq(myDragged), eq(receiver), any(), anyBoolean())).thenReturn(false);
    assertFalse(myModel.canAddComponents(myDragged, receiver, null));
    NlDropInsertionPicker picker = new NlDropInsertionPicker(tree);
    NlDropInsertionPicker.Result result = picker.findInsertionPointAt(new Point(15, 15), myDragged);
    assertEquals(root, result.receiver);
    assertNull(result.nextComponent);
    assertEquals(4, result.row);
    assertEquals(-1, result.depth);
  }

  @NotNull
  private NlDropInsertionPicker getDefaultPicker() {
    FakeTree tree = new FakeTree();
    return new NlDropInsertionPicker(tree);
  }

  /* ***********************************************************************/
  /* ********************** STUB/FAKE CLASSES ******************************/
  /* ***********************************************************************/

  /**
   * Fake JTree
   */
  private class FakeTree extends JTree {

    private FakeTree() {
      super(new NlComponentTreeModel(myModel));
      expandAllNodes(0, getRowCount());
    }

    private void expandAllNodes(int startingIndex, int rowCount) {
      for (int i = startingIndex; i < rowCount; ++i) {
        expandRow(i);
      }

      if (getRowCount() != rowCount) {
        expandAllNodes(rowCount, getRowCount());
      }
    }

    @Override
    public TreePath getClosestPathForLocation(int x, int y) {
      return myTreePaths[Math.max(0, Math.min(myTreePaths.length - 1, y / NODE_SIZE))];
    }

    @Override
    public int getRowForPath(@NotNull TreePath path) {
      for (int i = 0; i < myTreePaths.length; i++) {
        if (myTreePaths[i] == path) {
          return i;
        }
      }
      return -1;
    }

    @Nullable
    @Override
    public TreePath getPathForRow(int row) {
      if (row < 0 || row >= myTreePaths.length) {
        return null;
      }
      return myTreePaths[row];
    }

    @Nullable
    @Override
    public Rectangle getPathBounds(@NotNull TreePath path) {
      for (int i = 0; i < myTreePaths.length; i++) {
        if (myTreePaths[i] == path) {
          return new Rectangle(myTreePaths[i].myPosition, i * NODE_SIZE, NODE_SIZE, NODE_SIZE);
        }
      }
      return null;
    }

    @Override
    public int getRowCount() {
      return myTreePaths.length;
    }
  }

  /**
   * Placeholder/fake NlComponent
   */
  private static class FakeNlComponent extends NlComponent {
    private final boolean mySibling;
    private final int myId;
    @Nullable private NlDropInsertionPickerTest.FakeNlComponentGroup myFakeParent = null;

    private FakeNlComponent(int id, @NotNull NlModel model, boolean hasSibling) {
      //noinspection unchecked
      super(model, EmptyXmlTag.INSTANCE, mock(SmartPsiElementPointer.class));
      mySibling = hasSibling;
      myId = id;
    }

    @SuppressWarnings("deprecation")
    @Deprecated
    @NotNull
    @Override
    public XmlTag getTagDeprecated() {
      return EmptyXmlTag.INSTANCE;
    }

    @Nullable
    @Override
    public NlComponent getNextSibling() {
      if (!mySibling) {
        return null;
      }

      int indexInParent = myFakeParent.getChildren().indexOf(this);
      if (indexInParent < 0 || indexInParent + 1 > myFakeParent.getChildCount() - 1) {
        return null;
      }
      return myFakeParent.getChildren().get(indexInParent + 1);
    }

    @Nullable
    @Override
    public NlComponent getParent() {
      return myFakeParent;
    }

    public void setFakeParent(@Nullable NlDropInsertionPickerTest.FakeNlComponentGroup parent) {
      myFakeParent = parent;
    }

    @NotNull
    @Override
    public String toString() {
      return "#" + myId;
    }
  }

  /**
   * Placeholder/fake component to mock a ViewGroup
   */
  private static class FakeNlComponentGroup extends FakeNlComponent {
    private static final FakeNlComponent[] EMPTY_COMPONENTS = new FakeNlComponent[0];
    @NotNull private FakeNlComponent[] myChildren = EMPTY_COMPONENTS;

    private FakeNlComponentGroup(int i, @NotNull NlModel model, boolean hasSibling) {
      super(i, model, hasSibling);
    }

    @NotNull
    public NlDropInsertionPickerTest.FakeNlComponentGroup setChildren(@NotNull FakeNlComponent... children) {
      myChildren = children;
      for (FakeNlComponent aMyChildren : myChildren) {
        aMyChildren.setFakeParent(this);
      }
      return this;
    }

    @Override
    public int getChildCount() {
      return myChildren.length;
    }

    @Nullable
    @Override
    public NlComponent getChild(int index) {
      return index > myChildren.length - 1 ? null : myChildren[index];
    }

    @NotNull
    @Override
    public List<NlComponent> getChildren() {
      return Arrays.asList(myChildren);
    }
  }

  /**
   * Placeholder/fake TreePath
   */
  private static class FakeTreePath extends TreePath {

    private final int myPosition;

    /**
     * @param parent    the parent of the this {@link FakeTreePath}
     * @param xPosition Mock x coordinate of the node in the tree.
     *                  0 is the root, and we increment by 10 for each deeper level
     */
    private FakeTreePath(@NotNull NlComponent lastComponent, TreePath parent, int xPosition) {
      super(parent, lastComponent);
      myPosition = xPosition;
    }
  }
}
