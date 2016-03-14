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
package com.android.tools.idea.editors.gfxtrace.controllers;

import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.models.GpuState;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.path.FieldPath;
import com.android.tools.idea.editors.gfxtrace.service.path.MapIndexPath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.snippets.KindredSnippets;
import com.android.tools.idea.editors.gfxtrace.service.snippets.SnippetObject;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Field;
import com.android.tools.rpclib.schema.Map;
import com.android.tools.rpclib.schema.Type;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class StateController extends TreeController implements GpuState.Listener {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new StateController(editor).myPanel;
  }

  @NotNull private static final Logger LOG = Logger.getInstance(StateController.class);
  @NotNull private static final TypedValue ROOT_TYPE = new TypedValue(null, SnippetObject.symbol("state"));

  private final StateTreeModel myModel = new StateTreeModel(new Node(ROOT_TYPE, null));

  private StateController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.SELECT_ATOM);
    myEditor.getGpuState().addListener(this);

    myPanel.setBorder(BorderFactory.createTitledBorder(myScrollPane.getBorder(), "GPU State"));
    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
    setModel(myModel);
  }

  @Override
  public void notifyPath(PathEvent event) {
  }

  @Override
  public void onStateLoadingStart(GpuState state) {
    myLoadingPanel.startLoading();
  }

  @Override
  public void onStateLoadingFailure(GpuState state, ErrDataUnavailable error) {
    myLoadingPanel.stopLoading();
    myTree.getEmptyText().setText(error.getMessage());
    clear();
  }

  @Override
  public void onStateLoadingSuccess(GpuState state) {
    myLoadingPanel.stopLoading();

    if (getModel() != myModel) {
      setModel(myModel);
    }
    myModel.setRoot(convert(ROOT_TYPE, new TypedValue(null, SnippetObject.root(state.getState(), getSnippets(state)))));
  }

  @Override
  public void onStateSelection(GpuState state, Path path) {
    SnippetObject[] selection = getStatePath(path);
    Node node = (Node)myModel.getRoot();
    TreePath treePath = new TreePath(node);
    for (int i = 0; i < selection.length && !node.isLeaf(); i++) {
      node = node.findChild(selection[i]);
      if (node == null) break;
      treePath = treePath.pathByAddingChild(node);
    }
    final TreePath expandPath = (node == null || node.isLeaf()) ? treePath.getParentPath() : treePath;

    myTree.expandPath(expandPath);
    myTree.setSelectionPath(treePath);
    // Avoid making the user scroll to see the contents of the item they just selected.
    // First we scroll to the last child, then back to the selected item. After this,
    // the expanded item will either be at the top of the window or child count rows
    // above the bottom or the selected item is at the bottom and the expanded item
    // is above the top of the window.
    int row = myTree.getRowForPath(expandPath) + (node == null ? 0 : node.getChildCount());
    if (row >= myTree.getRowCount()) {
      row = myTree.getRowCount() - 1;
    }
    myTree.scrollPathToVisible(myTree.getPathForRow(row));
    myTree.scrollPathToVisible(treePath);
  }

  private static SnippetObject[] getStatePath(Path path) {
    LinkedList<SnippetObject> result = Lists.newLinkedList();
    while (path != null) {
      if (path instanceof FieldPath) {
        result.add(0, SnippetObject.symbol(((FieldPath)path).getName()));
      }
      else if (path instanceof MapIndexPath) {
        result.add(0, SnippetObject.symbol(((MapIndexPath)path).getKey()));
      }
      else {
        break;
      }
      path = path.getParent();
    }
    return result.toArray(new SnippetObject[result.size()]);
  }

  private KindredSnippets[] getSnippets(GpuState state) {
    return KindredSnippets.fromMetadata(state.getState().klass().entity().getMetadata());
  }

  private static Node convert(TypedValue key, TypedValue value) {
    final Node result = new Node(key, value);
    final SnippetObject obj = value.value;
    final Object underlying = obj.getObject();
    if (underlying instanceof Dynamic || value.type instanceof Map) {
      if (underlying instanceof Dynamic) {
        final Dynamic dynamic = (Dynamic)underlying;
        for (int i = 0; i < dynamic.getFieldCount(); i++) {
          final Field field = dynamic.getFieldInfo(i);
          final SnippetObject fieldObj = obj.field(dynamic, i);
          result.addChild(convert(new TypedValue(null, SnippetObject.symbol(field.getDeclared())), new TypedValue(field.getType(), fieldObj)));
        }
      }
      else {
        final java.util.Map<Object, Object> map = (java.util.Map<Object, Object>)underlying;
        final Type keyType = ((Map)value.type).getKeyType(), valueType = ((Map)value.type).getValueType();
        for (java.util.Map.Entry<Object, Object> e : map.entrySet()) {
          result.addChild(convert(new TypedValue(keyType, obj.key(e)), new TypedValue(valueType, obj.elem(e))));
        }
      }
    }
    return result;
  }

  public static class TypedValue {
    public final Type type;
    public final SnippetObject value;

    public TypedValue(Type type, SnippetObject value) {
      this.type = type;
      this.value = value;
    }

    @Override
    public int hashCode() {
      return (type == null ? 0 : type.hashCode()) ^ value.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == this) {
        return true;
      } else if (!(obj instanceof TypedValue)) {
        return false;
      }
      TypedValue other = (TypedValue)obj;
      return Objects.equal(type, other.type) && Objects.equal(value, other.value);
    }
  }

  public static class Node {
    public final TypedValue key;
    public TypedValue value;
    private final List<Node> childrenByIndex = Lists.newArrayList();
    private final HashMap<TypedValue, Node> childrenByKey = Maps.newHashMap();

    public Node(TypedValue key, TypedValue value) {
      this.key = key;
      this.value = value;
    }

    public void addChild(Node node) {
      childrenByIndex.add(node);
      childrenByKey.put(node.key, node);
    }

    public void merge(Node other, List<Object> path, TreeModelListener listener) {
      IntArrayList changedIndecies = new IntArrayList(Math.max(getChildCount(), other.getChildCount()));
      List<Node> changedChildren = Lists.newArrayListWithCapacity(Math.max(getChildCount(), other.getChildCount()));
      path.add(this);

      // First handle deletions.
      for (int i = 0; i < childrenByIndex.size(); i++) {
        Node child = childrenByIndex.get(i);
        if (!other.childrenByKey.containsKey(child.key)) {
          changedIndecies.add(i + changedIndecies.size());
          changedChildren.add(child);
          childrenByIndex.remove(i);
          childrenByKey.remove(child.key);
          i--;
        }
      }
      if (!changedIndecies.isEmpty()) {
        listener.treeNodesRemoved(new TreeModelEvent(this, path.toArray(), changedIndecies.toArray(), changedChildren.toArray()));
        changedIndecies.clear();
        changedChildren.clear();
      }

      // Handle additions.
      for (int i = 0; i < other.childrenByIndex.size(); i++) {
        Node child = other.childrenByIndex.get(i);
        if (!childrenByKey.containsKey(child.key)) {
          changedIndecies.add(childrenByIndex.size());
          changedChildren.add(child);
          addChild(child);
        }
      }
      if (!changedIndecies.isEmpty()) {
        listener.treeNodesInserted(new TreeModelEvent(this, path.toArray(), changedIndecies.toArray(), changedChildren.toArray()));
        changedIndecies.clear();
        changedChildren.clear();
      }

      // Process the children.
      for (Node child : childrenByIndex) {
        child.merge(other.childrenByKey.get(child.key), path, listener);
      }

      // Fire event if we're a leaf that has changed.
      if (isLeaf() && !Objects.equal(value, other.value)) {
        this.value = other.value;
        listener.treeNodesChanged(new TreeModelEvent(this, path.toArray()));
      }
      else {
        this.value = other.value;
      }
      path.remove(path.size() - 1);
    }

    public int getChildCount() {
      return childrenByIndex.size();
    }

    public Node getChild(int index) {
      return childrenByIndex.get(index);
    }

    public int getChildIndex(Object child) {
      return childrenByIndex.indexOf(child);
    }

    public boolean isLeaf() {
      return childrenByIndex.isEmpty();
    }

    public Node findChild(Object key) {
      for (Node child : childrenByIndex) {
        if (Objects.equal(key, child.key.value)) {
          return child;
        }
      }
      return null;
    }
  }

  private static class StateTreeModel implements TreeModel {
    private final Listeners listeners = new Listeners();
    private Node root;

    public StateTreeModel(Node root) {
      this.root = root;
    }

    public void setRoot(Node state) {
      if (root.isLeaf()) {
        root = state;
        listeners.treeStructureChanged(new TreeModelEvent(this, new Object[] { root }));
      }
      else {
        root.merge(state, Lists.newArrayList(), listeners);
      }
    }

    @Override
    public Object getRoot() {
      return root;
    }

    @Override
    public int getChildCount(Object parent) {
      return ((Node)parent).getChildCount();
    }

    @Override
    public Object getChild(Object parent, int index) {
      return ((Node)parent).getChild(index);
    }

    @Override
    public boolean isLeaf(Object node) {
      return ((Node)node).isLeaf();
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
      throw new UnsupportedOperationException();
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
      if (!(parent instanceof Node) || child == null) {
        return -1;
      }
      return ((Node)parent).getChildIndex(child);
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
      listeners.add(l);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
      listeners.remove(l);
    }

    private static class Listeners extends ArrayList<TreeModelListener> implements TreeModelListener {
      public Listeners() {
      }

      @Override
      public void treeNodesChanged(TreeModelEvent e) {
        for (TreeModelListener listener : toArray(new TreeModelListener[size()])) {
          listener.treeNodesChanged(e);
        }
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e) {
        for (TreeModelListener listener : toArray(new TreeModelListener[size()])) {
          listener.treeNodesInserted(e);
        }
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e) {
        for (TreeModelListener listener : toArray(new TreeModelListener[size()])) {
          listener.treeNodesRemoved(e);
        }
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e) {
        for (TreeModelListener listener : toArray(new TreeModelListener[size()])) {
          listener.treeStructureChanged(e);
        }
      }
    }
  }

  protected TreeCellRenderer getRenderer() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree, Object value,
                                        boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof StateController.Node) {
          Render.render((StateController.Node)value, this, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        else {
          assert false;
        }
      }
    };
  }
}
