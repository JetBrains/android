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
import com.android.tools.idea.editors.gfxtrace.actions.ViewTextAction;
import com.android.tools.idea.editors.gfxtrace.models.GpuState;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.ErrDataUnavailable;
import com.android.tools.idea.editors.gfxtrace.service.memory.MemorySliceInfo;
import com.android.tools.idea.editors.gfxtrace.service.path.FieldPath;
import com.android.tools.idea.editors.gfxtrace.service.path.MapIndexPath;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.snippets.CanFollow;
import com.android.tools.idea.editors.gfxtrace.service.snippets.KindredSnippets;
import com.android.tools.idea.editors.gfxtrace.service.snippets.SnippetObject;
import com.android.tools.rpclib.schema.*;
import com.android.tools.rpclib.schema.Map;
import com.google.common.base.Objects;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.containers.IntArrayList;
import com.intellij.xml.breadcrumbs.BreadcrumbsComponent;
import com.intellij.xml.breadcrumbs.BreadcrumbsItem;
import com.intellij.xml.breadcrumbs.BreadcrumbsItemListener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.*;
import java.util.List;
import java.util.stream.Stream;

public class StateController extends TreeController implements GpuState.Listener {
  public static JComponent createUI(@NotNull GfxTraceEditor editor) {
    return new StateController(editor).myPanel;
  }

  private static final @NotNull Logger LOG = Logger.getInstance(StateController.class);
  private static final @NotNull TypedValue ROOT_TYPE = new TypedValue(null, SnippetObject.symbol("state"));

  private final @NotNull StateTreeModel myModel = new StateTreeModel(new Node(ROOT_TYPE, null));

  private @Nullable TreePath myLastSelectedBreadcrumb;

  private StateController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.SELECT_ATOM);
    myEditor.getGpuState().addListener(this);

    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
    setModel(myModel);

    MouseAdapter mouseHandler = new MouseAdapter() {
      private JPopupMenu popupMenu = new JPopupMenu();
      @Override
      public void mouseMoved(MouseEvent event) {
        TreePath treePath = getFollowAt(event.getX(), event.getY());
        Path followPath = null;
        if (treePath != null) {
          Node node = (Node)treePath.getLastPathComponent();
          followPath = node.getFollowPath();
          if (followPath == null) {
            // set empty path so we do not make any more calls to the server for this path
            node.setFollowPath(Path.EMPTY);
            Path path = getPath(treePath);
            Futures.addCallback(myEditor.getClient().follow(path), new FutureCallback<Path>() {
              @Override
              public void onSuccess(Path result) {
                node.setFollowPath(result);
              }

              @Override
              public void onFailure(Throwable t) {
                LOG.warn("Error: " + t + " for path " + path);
              }
            });
          }
        }

        hoverHand(myTree, myEditor.getGpuState().getPath(), followPath);
      }

      @Override
      public void mouseExited(MouseEvent e) {
        hoverHand(myTree, myEditor.getGpuState().getPath(), null);
      }

      @Override
      public void mouseClicked(MouseEvent event) {
        TreePath treePath = getFollowAt(event.getX(), event.getY());
        if (treePath != null) {
          Path path = ((Node)treePath.getLastPathComponent()).getFollowPath();
          if (path != null && path != Path.EMPTY) {
            myEditor.activatePath(path, StateController.this);
          }
          else {
            // this can happen if the server takes too long to respond, or responds with a error
            LOG.warn("click, but we don't have a path :(");
          }
        }
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
          TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
          if (path != null) {
            Node treeNode = (Node)path.getLastPathComponent();
            if (treeNode.value.type instanceof Primitive && ((Primitive)treeNode.value.type).getMethod() == Method.String) {
              String title = TreeController.getDisplayTextFor(myEditor.getGpuState().getPath(), getPath(path));
              ViewTextAction viewText = new ViewTextAction(myEditor.getProject(), title, treeNode.value.value.getObject());
              popupMenu.removeAll();
              popupMenu.add(viewText);
              popupMenu.show(e.getComponent(), e.getX(), e.getY());
            }
          }
        }
      }
    };
    myTree.addMouseListener(mouseHandler);
    myTree.addMouseMotionListener(mouseHandler);

    final BreadcrumbsComponent<PathBreadcrumbsItem> breadcrumb = new BreadcrumbsComponent<>();
    breadcrumb.addBreadcrumbsItemListener(new BreadcrumbsItemListener<PathBreadcrumbsItem>() {
      @Override
      public void itemSelected(@NotNull PathBreadcrumbsItem item, int modifiers) {
        myLastSelectedBreadcrumb = item.getTreePath();
        myEditor.activatePath(getPath(myLastSelectedBreadcrumb), StateController.this);
      }

      @Override
      public void itemHovered(@Nullable PathBreadcrumbsItem item) {
        hoverHand(breadcrumb, myEditor.getGpuState().getPath(), item == null ? null : getPath(item.getTreePath()));
      }
    });
    myPanel.add(breadcrumb, BorderLayout.NORTH);

    myTree.addTreeSelectionListener(e -> {
      if (e.isAddedPath()) {
        TreePath path = e.getPath();

        // if we have just selected a item that was selected from the breadcrumb, we don't need to update them
        if (!path.equals(myLastSelectedBreadcrumb)) {
          TreePath root = new TreePath(myTree.getModel().getRoot());
          List<PathBreadcrumbsItem> breadcrumbs = new ArrayList<>();
          while (path != null) {
            if (path.equals(root)) {
              // we have reached the root, we don't want to create a breadcrumb for it as it is hidden.
              break;
            }
            breadcrumbs.add(new PathBreadcrumbsItem(path));
            path = path.getParentPath();
          }
          Collections.reverse(breadcrumbs);
          breadcrumb.setItems(breadcrumbs);
          myLastSelectedBreadcrumb = null;
        }
      }
    });
  }

  @Nullable("nothing to follow at this location")
  private TreePath getFollowAt(int mouseX, int mouseY) {
    TreePath treePath = myTree.getPathForLocation(mouseX, mouseY);
    if (treePath == null) return null;
    Node node = (Node)treePath.getLastPathComponent();
    if (node.isLeaf() && !node.canFollow()) return null;
    Rectangle bounds = myTree.getPathBounds(treePath);
    assert bounds != null; // can't be null, as our path is valid
    int tag = Render.getNodeFieldIndex(myTree, node, mouseX - bounds.x, myTree.isExpanded(treePath));
    if (tag == Render.NO_TAG) {
      return null;
    }
    if (node.isLeaf() && tag == Render.STATE_VALUE_TAG) {
      // we already know we can follow this Node
      return treePath;
    }
    Node child = node.getChild(tag);
    return child.canFollow() ? new TreePath(Stream.concat(Arrays.stream(treePath.getPath()), Arrays.stream(new Object[] {child})).toArray()) : null;
  }

  private @NotNull Path getPath(@NotNull TreePath treePath) {
    Path parent = null;
    Object[] nodePath = treePath.getPath();
    for (Object node : nodePath) {
      if (node == nodePath[0]) {
        // the root
        assert myTree.getModel().getRoot().equals(node);
        assert ROOT_TYPE.equals(((Node)node).key);
        assert "state".equals(((Node)node).key.value.getObject());
        parent = myEditor.getGpuState().getPath();
      }
      else {
        parent = getPathSegmentFor(parent, (Node)node);
      }
    }
    assert parent != null; // we should at least have a root in this path
    return parent;
  }

  private static @NotNull Path getPathSegmentFor(@Nullable Path parent, @NotNull Node node) {
    Object obj = node.key.value.getObject();
    if (obj instanceof String) {
      FieldPath path = new FieldPath();
      path.setName((String)obj);
      path.setStruct(parent);
      return path;
    }
    if (obj instanceof Number || obj instanceof Dynamic) {
      MapIndexPath path = new MapIndexPath();
      path.setKey(obj);
      path.setMap(parent);
      return path;
    }
    throw new IllegalArgumentException("unknown type: " + obj.getClass().getSimpleName());
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
          addChildNode(result,null, SnippetObject.symbol(field.getDeclared()), field.getType(), fieldObj);
        }
      }
      else {
        final java.util.Map<Object, Object> map = (java.util.Map<Object, Object>)underlying;
        final Type keyType = ((Map)value.type).getKeyType(), valueType = ((Map)value.type).getValueType();
        for (java.util.Map.Entry<Object, Object> e : map.entrySet()) {
          addChildNode(result, keyType, obj.key(e), valueType, obj.elem(e));
        }
      }
    }
    return result;
  }

  private static void addChildNode(Node parent, Type keyType, SnippetObject keyValue, Type valueType, SnippetObject valueValue) {
    // we dont want to create child Nodes for MemorySliceInfo, as they are shown simply as a inline values
    if (!(valueValue.getObject() instanceof MemorySliceInfo)) {
      parent.addChild(convert(new TypedValue(keyType, keyValue), new TypedValue(valueType, valueValue)));
    }
  }

  private static class PathBreadcrumbsItem extends BreadcrumbsItem {
    // we keep the TreePath and Not Path, as the capture ID may change, and a Path object will still have the old one.
    private final @NotNull TreePath myPath;

    public PathBreadcrumbsItem(@NotNull TreePath path) {
      myPath = path;
    }

    public @NotNull TreePath getTreePath() {
      return myPath;
    }

    @Override
    public @NotNull String getDisplayText() {
      return getPathSegmentFor(null, (Node)myPath.getLastPathComponent()).getSegmentString();
    }
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

    @Override
    public String toString() {
      return "TypedValue{type=" + type + ", value=" + value + '}';
    }
  }

  public static class Node {
    public final TypedValue key;
    public TypedValue value;
    private final List<Node> childrenByIndex = Lists.newArrayList();
    private final HashMap<TypedValue, Node> childrenByKey = Maps.newHashMap();
    private @Nullable("not made server request yet") Path followPath;

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
      // if we are merging in another Node. we want to clear the cached server follow link response
      followPath = null;

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

    @Override
    public String toString() {
      return key + " = " + value;
    }

    public boolean canFollow() {
      return CanFollow.fromSnippets(value.value.getSnippets()) != null;
    }

    @Nullable("if we have not made a request to the server for this path yet")
    public Path getFollowPath() {
      return followPath;
    }

    public void setFollowPath(@NotNull Path followPath) {
      this.followPath = followPath;
    }

    public boolean canBeRenderedAsLeaf() {
      if (!isLeaf() && getChildCount() <= 4) {
        for (int c = 0; c < getChildCount(); c++) {
          Node child = getChild(c);
          if (!child.isLeaf()) {
            return false;
          }
        }
        return true;
      }
      return isLeaf();
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

  @Override
  @NotNull
  protected TreeCellRenderer getRenderer() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree, Object value,
                                        boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof StateController.Node) {
          Render.render((StateController.Node)value, this, SimpleTextAttributes.REGULAR_ATTRIBUTES, expanded);
        }
        else {
          assert false;
        }
      }
    };
  }

  @NotNull
  @Override
  public String[] getColumns(TreePath path) {
    Object object = path.getLastPathComponent();
    if (object instanceof StateController.Node) {
      Node node = (StateController.Node)object;
      String key = "";
      if (node.key.type != null) {
        SimpleColoredComponent component = new SimpleColoredComponent();
        Render.render(node.key.value, node.key.type, component, SimpleTextAttributes.REGULAR_ATTRIBUTES, 0);
        key = component.toString();
      }
      else {
        key = String.valueOf(node.key.value.getObject());
      }

      if (!node.isLeaf() || node.value == null || node.value.value == null || node.value.value.getObject() == null) {
        return new String[]{key};
      }

      SimpleColoredComponent component = new SimpleColoredComponent();
      Render.render(node.value.value, node.value.type, component, SimpleTextAttributes.REGULAR_ATTRIBUTES, 0);
      String value = component.toString();
      return new String[]{key, value};
    }
    return new String[]{object.toString()};
  }
}
