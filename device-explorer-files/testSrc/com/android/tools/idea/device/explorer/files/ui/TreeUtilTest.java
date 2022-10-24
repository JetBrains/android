/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.tools.idea.device.explorer.files.ui;

import static com.google.common.truth.Truth.assertThat;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.LoadingNode;
import com.intellij.util.ui.tree.TreeModelAdapter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.swing.event.TreeModelEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Before;
import org.junit.Test;

public class TreeUtilTest {
  private DefaultTreeModel myModel;
  private MyTreeModelListener myModelListener;
  private List<Entry> myNewEntries;
  private EntryNode myDirNode;

  @Before
  public void setUp() throws Exception {
    Entry rootEntry = createTestTree();
    EntryNode rootNode = createTreeNode(rootEntry);
    myModel = new DefaultTreeModel(rootNode);
    myModelListener = new MyTreeModelListener();
    myModel.addTreeModelListener(myModelListener);
    myNewEntries = new ArrayList<>();
    myDirNode = getTreeNode(rootNode, "sub-dir1");
    myNewEntries = createEntriesForChildren(myDirNode);
  }

  @Test
  public void testFullToEmpty() {
    // Prepare
    myNewEntries.clear();

    // Act
    TreeUtil.updateChildrenNodes(myModel, myDirNode, myNewEntries, createDefaultOps());

    // Assert
    assertSameEntries(myDirNode, myNewEntries);
    assertThat(myModelListener.getStructureChangedCount()).isEqualTo(1);
    assertThat(myModelListener.getNodesChangedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesInsertedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesRemovedCount()).isEqualTo(0);
  }

  @Test
  public void testEmptyToFull() {
    // Prepare
    myDirNode.removeAllChildren();
    myNewEntries.clear();
    myNewEntries.add(new Entry("a"));
    myNewEntries.add(new Entry("b"));
    myNewEntries.add(new Entry("c"));

    // Act
    TreeUtil.updateChildrenNodes(myModel, myDirNode, myNewEntries, createDefaultOps());

    // Assert
    assertSameEntries(myDirNode, myNewEntries);
    assertThat(myModelListener.getStructureChangedCount()).isEqualTo(1);
    assertThat(myModelListener.getNodesChangedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesInsertedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesRemovedCount()).isEqualTo(0);
  }

  @Test
  public void testSingleInsertAtStartOfChildren() {
    // Prepare
    myNewEntries.add(new Entry("a"));
    myNewEntries.sort(Comparator.comparing(Entry::getText));

    // Act
    TreeUtil.updateChildrenNodes(myModel, myDirNode, myNewEntries, createDefaultOps());

    // Assert
    assertSameEntries(myDirNode, myNewEntries);
    assertThat(myModelListener.getStructureChangedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesChangedCount()).isEqualTo(myDirNode.getChildCount() - 1);
    assertThat(myModelListener.getNodesInsertedCount()).isEqualTo(1);
    assertThat(myModelListener.getNodesRemovedCount()).isEqualTo(0);
  }

  @Test
  public void testSingleInsertAtEndOfChildren() {
    // Prepare
    myNewEntries.add(new Entry("z"));
    myNewEntries.sort(Comparator.comparing(Entry::getText));

    // Act
    TreeUtil.updateChildrenNodes(myModel, myDirNode, myNewEntries, createDefaultOps());

    // Assert
    assertSameEntries(myDirNode, myNewEntries);
    assertThat(myModelListener.getStructureChangedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesChangedCount()).isEqualTo(myDirNode.getChildCount() - 1);
    assertThat(myModelListener.getNodesInsertedCount()).isEqualTo(1);
    assertThat(myModelListener.getNodesRemovedCount()).isEqualTo(0);
  }

  @Test
  public void testMultipleInserts() {
    // Prepare
    myNewEntries.add(new Entry("a"));
    myNewEntries.add(new Entry("file2-1.txt"));
    myNewEntries.add(new Entry("file2-2.txt"));
    myNewEntries.add(new Entry("z"));
    myNewEntries.sort(Comparator.comparing(Entry::getText));

    // Act
    TreeUtil.updateChildrenNodes(myModel, myDirNode, myNewEntries, createDefaultOps());

    // Assert
    assertSameEntries(myDirNode, myNewEntries);
    assertThat(myModelListener.getStructureChangedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesChangedCount()).isEqualTo(myDirNode.getChildCount() - 4);
    assertThat(myModelListener.getNodesInsertedCount()).isEqualTo(4);
    assertThat(myModelListener.getNodesRemovedCount()).isEqualTo(0);
  }

  @Test
  public void testSingleDeleteAtStartOfChildren() {
    // Prepare
    myNewEntries.remove(0);

    // Act
    TreeUtil.updateChildrenNodes(myModel, myDirNode, myNewEntries, createDefaultOps());

    // Assert
    assertSameEntries(myDirNode, myNewEntries);
    assertThat(myModelListener.getStructureChangedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesChangedCount()).isEqualTo(myDirNode.getChildCount());
    assertThat(myModelListener.getNodesInsertedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesRemovedCount()).isEqualTo(1);
  }

  @Test
  public void testSingleDeleteAtEndOfChildren() {
    // Prepare
    myNewEntries.remove(myNewEntries.size() - 1);

    // Act
    TreeUtil.updateChildrenNodes(myModel, myDirNode, myNewEntries, createDefaultOps());

    // Assert
    assertSameEntries(myDirNode, myNewEntries);
    assertThat(myModelListener.getStructureChangedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesChangedCount()).isEqualTo(myDirNode.getChildCount());
    assertThat(myModelListener.getNodesInsertedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesRemovedCount()).isEqualTo(1);
  }

  @Test
  public void testMultipleDeletes() {
    // Prepare
    myNewEntries.remove(0);
    myNewEntries.remove(3);

    // Act
    TreeUtil.updateChildrenNodes(myModel, myDirNode, myNewEntries, createDefaultOps());

    // Assert
    assertSameEntries(myDirNode, myNewEntries);
    assertThat(myModelListener.getStructureChangedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesChangedCount()).isEqualTo(myDirNode.getChildCount());
    assertThat(myModelListener.getNodesInsertedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesRemovedCount()).isEqualTo(2);
  }

  @Test
  public void testDeleteExtraNodes() {
    // Prepare
    myDirNode.insert(new LoadingNode("test"), 2);

    // Act
    TreeUtil.updateChildrenNodes(myModel, myDirNode, myNewEntries, createDefaultOps());

    // Assert
    assertSameEntries(myDirNode, myNewEntries);
    assertThat(myModelListener.getStructureChangedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesChangedCount()).isEqualTo(myDirNode.getChildCount());
    assertThat(myModelListener.getNodesInsertedCount()).isEqualTo(0);
    assertThat(myModelListener.getNodesRemovedCount()).isEqualTo(1);
  }

  private static void assertSameEntries(@NotNull EntryNode node, @NotNull List<Entry> entries) {
    assertThat(node.getChildCount()).isEqualTo(entries.size());
    for (int i = 0; i < node.getChildCount(); i++) {
      assertThat(node.getChildAt(i)).isInstanceOf(EntryNode.class);
      assertThat(((EntryNode)node.getChildAt(i)).getEntry().getText()).isEqualTo(entries.get(i).getText());
    }
  }

  @NotNull
  private static List<Entry> createEntriesForChildren(EntryNode node) {
    return node.getChildren().stream()
      .map(x -> new Entry(x.getEntry().getText()))
      .collect(Collectors.toList());
  }

  @NotNull
  public static Entry createTestTree() {
    Entry root = new Entry("root");

    Entry dir1 = root.addChild("sub-dir1");
    dir1.addChild("file1.txt");
    dir1.addChild("file2.txt");
    dir1.addChild("file3.txt");
    dir1.addChild("file4.txt");
    dir1.addChild("file5.txt");

    root.addChild("file.txt");
    Entry dir2 = root.addChild("sub-dir2");
    dir2.addChild("file1.txt");
    dir2.addChild("file2.txt");
    dir2.addChild("file3.txt");
    dir2.addChild("file4.txt");
    dir2.addChild("file5.txt");

    return root;
  }

  @NotNull
  public static EntryNode createTreeNode(@NotNull Entry root) {
    EntryNode rootNode = new EntryNode(root);
    root.getChildren().forEach(x -> rootNode.add(createTreeNode(x)));
    return rootNode;
  }

  @SuppressWarnings("SameParameterValue")
  @NotNull
  private static EntryNode getTreeNode(@NotNull EntryNode parent, @NotNull String text) {
    return parent.getChildren().stream().filter(x -> x.getEntry().getText().equals(text)).findFirst().orElse(null);
  }

  @NotNull
  private static TreeUtil.UpdateChildrenOps<EntryNode, Entry> createDefaultOps() {
    return new TreeUtil.UpdateChildrenOps<EntryNode, Entry>() {
      @Nullable
      @Override
      public EntryNode getChildNode(@NotNull EntryNode parentNode, int index) {
        TreeNode child = parentNode.getChildAt(index);
        if (child instanceof EntryNode) {
          return (EntryNode)child;
        }
        return null;
      }

      @NotNull
      @Override
      public EntryNode mapEntry(@NotNull Entry entry) {
        return new EntryNode(entry);
      }

      @Override
      public int compareNodeWithEntry(@NotNull EntryNode node, @NotNull Entry entry) {
        return StringUtil.compare(node.getEntry().getText(), entry.getText(), true);
      }

      @Override
      public void updateNode(@NotNull EntryNode node, @NotNull Entry entry) {
        node.setEntry(entry);
      }
    };
  }

  private static class MyTreeModelListener extends TreeModelAdapter {
    private int myStructureChangedCount;
    private int myNodesChangedCount;
    private int myNodesInsertedCount;
    private int myNodesRemovedCount;

    @Override
    public void treeStructureChanged(TreeModelEvent event) {
      super.treeStructureChanged(event);
      myStructureChangedCount++;
    }

    @Override
    public void treeNodesChanged(TreeModelEvent event) {
      super.treeNodesChanged(event);
      myNodesChangedCount++;
    }

    @Override
    public void treeNodesInserted(TreeModelEvent event) {
      super.treeNodesInserted(event);
      myNodesInsertedCount++;
    }

    @Override
    public void treeNodesRemoved(TreeModelEvent event) {
      super.treeNodesRemoved(event);
      myNodesRemovedCount++;
    }

    public int getStructureChangedCount() {
      return myStructureChangedCount;
    }

    public int getNodesChangedCount() {
      return myNodesChangedCount;
    }

    public int getNodesInsertedCount() {
      return myNodesInsertedCount;
    }

    public int getNodesRemovedCount() {
      return myNodesRemovedCount;
    }
  }

  public static class Entry {
    @NotNull private final String myText;
    @Nullable private String myData;
    @NotNull private final List<Entry> myChildren = new ArrayList<>();

    public Entry(@NotNull String text) {
      myText = text;
    }

    @Override
    public String toString() {
      return myText;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @NotNull
    public List<Entry> getChildren() {
      return myChildren;
    }

    @NotNull
    public Entry addChild(@NotNull String text) {
      assert myChildren.stream().noneMatch(x -> Objects.equals(text, x.getText()));

      Entry result = new Entry(text);
      myChildren.add(result);
      return result;
    }

    @Nullable
    public String getData() {
      return myData;
    }

    public void setData(@Nullable String data) {
      myData = data;
    }
  }

  public static class EntryNode extends DefaultMutableTreeNode {
    @NotNull private Entry myEntry;

    public EntryNode(@NotNull Entry entry) {
      myEntry = entry;
    }

    @Override
    public String toString() {
      return "node for " + myEntry.toString();
    }

    @NotNull
    public Entry getEntry() {
      return myEntry;
    }

    public void setEntry(@NotNull Entry entry) {
      myEntry = entry;
    }

    @NotNull
    public List<EntryNode> getChildren() {
      List<EntryNode> result = new ArrayList<>();
      for(int i = 0; i < getChildCount(); i++) {
        Object child = getChildAt(i);
        if (child instanceof EntryNode) {
          result.add((EntryNode)child);
        }
      }
      return result;
    }
  }
}
