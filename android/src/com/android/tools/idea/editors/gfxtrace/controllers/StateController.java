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

import com.android.tools.idea.ddms.EdtExecutor;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.UiCallback;
import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.JBLoadingPanelWrapper;
import com.android.tools.idea.editors.gfxtrace.service.path.AtomPath;
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.android.tools.idea.editors.gfxtrace.service.path.StatePath;
import com.android.tools.rpclib.futures.SingleInFlight;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.android.tools.rpclib.schema.Dynamic;
import com.android.tools.rpclib.schema.Field;
import com.android.tools.rpclib.schema.Map;
import com.android.tools.rpclib.schema.Type;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.ListenableFuture;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.util.containers.IntArrayList;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class StateController extends TreeController {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new StateController(editor).myPanel;
  }

  @NotNull private static final Logger LOG = Logger.getInstance(StateController.class);
  @NotNull private static final TypedValue ROOT_TYPE = new TypedValue(null, "state");
  @NotNull private static final Object[] NO_SELECTION = new Object[0];

  private final PathStore<StatePath> myStatePath = new PathStore<StatePath>();
  private final StateTreeModel myModel = new StateTreeModel(new Node(ROOT_TYPE, null));
  private final SingleInFlight myStateRequestController = new SingleInFlight(new JBLoadingPanelWrapper(myLoadingPanel));
  private Object[] lastSelection = NO_SELECTION;

  private StateController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.SELECT_ATOM);
    myPanel.setBorder(BorderFactory.createTitledBorder(myScrollPane.getBorder(), "GPU State"));
    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
    setModel(myModel);
  }

  @Override
  public void notifyPath(PathEvent event) {
    boolean updateState = myStatePath.updateIfNotNull(AtomPath.stateAfter(event.findAtomPath()));

    if (updateState && myStatePath.getPath() != null) {
      ListenableFuture future = myEditor.getClient().get(myStatePath.getPath());
      Rpc.listen(future, LOG, myStateRequestController, new UiErrorCallback<Object, Node, String>() {
        @Override
        protected ResultOrError<Node, String> onRpcThread(Rpc.Result<Object> result) throws RpcException, ExecutionException {
          try {
            return success(convert(new TypedValue(null, "state"), new TypedValue(null, result.get())));
          }
          catch (ErrDataUnavailable e) {
            return error(e.getMessage());
          }
        }

        @Override
        protected void onUiThreadSuccess(Node root) {
          if (getModel() != myModel) {
            setModel(myModel);
          }

          myModel.setRoot(root);
          if (lastSelection.length > 0) {
            select(lastSelection);
            lastSelection = NO_SELECTION;
          }
        }

        @Override
        protected void onUiThreadError(String error) {
          myTree.getEmptyText().setText(error);
          clear();
        }
      });
    }

    if (event.findStatePath() != null) {
      Object[] selection = getStatePath(event.path);
      if (getModel() == null || ((Node)myModel.getRoot()).isLeaf()) {
        lastSelection = selection;
      }
      else {
        select(selection);
        lastSelection = NO_SELECTION;
      }
    }
  }

  private static Object[] getStatePath(Path path) {
    LinkedList<Object> result = Lists.newLinkedList();
    while (path != null) {
      if (path instanceof FieldPath) {
        result.add(0, ((FieldPath)path).getName());
      }
      else if (path instanceof MapIndexPath) {
        result.add(0, ((MapIndexPath)path).getKey());
      }
      else {
        break;
      }
      path = path.getParent();
    }
    return result.toArray(new Object[result.size()]);
  }

  private void select(Object[] nodePath) {
    Node node = (Node)myModel.getRoot();
    TreePath treePath = new TreePath(node);
    for (int i = 0; i < nodePath.length && !node.isLeaf(); i++) {
      node = node.findChild(nodePath[i]);
      if (node == null) break;
      treePath = treePath.pathByAddingChild(node);
    }
    myTree.expandPath((node == null || node.isLeaf()) ? treePath.getParentPath() : treePath);
    myTree.setSelectionPath(treePath);
    myTree.scrollPathToVisible(treePath);
  }

  private static Node convert(TypedValue key, TypedValue value) {
    Node result = new Node(key, value);
    if (value.value instanceof Dynamic || value.type instanceof Map) {
      if (value.value instanceof Dynamic) {
        Dynamic dynamic = (Dynamic)value.value;
        for (int i = 0; i < dynamic.getFieldCount(); i++) {
          Field field = dynamic.getFieldInfo(i);
          result.addChild(convert(new TypedValue(null, field.getDeclared()), new TypedValue(field.getType(), dynamic.getFieldValue(i))));
        }
      }
      else {
        java.util.Map<Object, Object> map = (java.util.Map<Object, Object>)value.value;
        Type keyType = ((Map)value.type).getKeyType(), valueType = ((Map)value.type).getValueType();
        for (java.util.Map.Entry<Object, Object> e : map.entrySet()) {
          result.addChild(convert(new TypedValue(keyType, e.getKey()), new TypedValue(valueType, e.getValue())));
        }
      }
    }
    return result;
  }

  public static class TypedValue {
    public final Type type;
    public final Object value;

    public TypedValue(Type type, Object value) {
      this.type = type;
      // Turn integers into longs, so they equal longs from paths.
      this.value = (value instanceof Integer) ? ((Integer)value).longValue() : value;
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
}
