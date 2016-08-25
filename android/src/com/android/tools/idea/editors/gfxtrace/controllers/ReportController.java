/*
 * Copyright (C) 2016 The Android Open Source Project
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

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.actions.ViewTextAction;
import com.android.tools.idea.editors.gfxtrace.models.ReportStream;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.MsgRef;
import com.android.tools.idea.editors.gfxtrace.service.Report;
import com.android.tools.idea.editors.gfxtrace.service.ReportItem;
import com.android.tools.idea.editors.gfxtrace.service.log.LogProtos;
import com.android.tools.idea.editors.gfxtrace.service.msg.Arg;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.android.tools.idea.editors.gfxtrace.service.path.ReportItemPath;
import com.android.tools.idea.editors.gfxtrace.service.stringtable.StringTable;
import com.android.tools.rpclib.binary.BinaryObject;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.speedSearch.ElementFilter;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.ui.treeStructure.filtered.FilteringTreeBuilder;
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTreeUI;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

// TODO: Check if there's a need of TreeController (probably some kind of ListController will satisfy this entity).
public class ReportController extends TreeController implements ReportStream.Listener {
  public static JComponent createUI(@NotNull GfxTraceEditor editor) {
    return new ReportController(editor).myPanel;
  }

  private static final @NotNull Logger LOG = Logger.getInstance(ReportController.class);
  private static final Map<String, BinaryObject> myMsgMap = new HashMap<>();

  // Wrapper needed for FilteringTreeStructure since it has final filter.
  public static class ElementFilterWrapper implements ElementFilter<Renderable> {
    private ElementFilter<Renderable> myFilter;

    public void setFilter(ElementFilter<Renderable> filter) {
      myFilter = filter;
    }

    @Override
    public boolean shouldBeShowing(Renderable value) {
      return myFilter.shouldBeShowing(value);
    }
  }

  public abstract static class Renderable extends NodeDescriptor {

    @NotNull protected final List<Renderable> myChildren;

    public Renderable(@Nullable Renderable parent) {
      super(null, parent);
      myChildren = new ArrayList<>();
    }

    public void addChild(ReportController.Renderable child) {
      myChildren.add(child);
    }

    @Override
    public boolean update() {
      return false;
    }

    @Override
    public Renderable getElement() {
      return this;
    }

    @Nullable
    @Override
    public Renderable getParentDescriptor() {
      return (Renderable)super.getParentDescriptor();
    }

    public abstract void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes);
  }

  // Node to be used as a representation for report item and its message.
  public static class Node extends Renderable {
    private static final int PREVIEW_LENGTH = 80;

    public final int index;

    private final @Nullable("instance represents ReportItem message") ReportItem myReportItem;
    private
    @Nullable String myMessage;

    private @Nullable("not made server request yet") Path followPath;
    // True by default and is false when GAPIS doesn't support ReportItemPath
    private boolean followable = true;

    public static Node createInstance(@NotNull Renderable parent,
                                      @NotNull Report report,
                                      @NotNull ReportItem item,
                                      int index) {
      Node node = new Node(parent, item, index);
      node.constructMessage(report);
      node.addChild(new Node(node, item.getMessage().toString()));
      return node;
    }

    private Node(@NotNull Renderable parent, @NotNull ReportItem item, int index) {
      super(parent);
      this.myReportItem = item;
      this.myMessage = null;
      this.index = index;
    }

    private Node(@NotNull Renderable parent, @NotNull String message) {
      super(parent);
      this.myReportItem = null;
      this.myMessage = message;
      this.index = -1;
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      Render.render(this, component);
    }

    public boolean containsReportItem() {
      return myReportItem != null;
    }

    public boolean isAtLeast(LogProtos.Severity severity) {
      // LogProtos.Severity enum increases from Emergency to Debug
      return severity.compareTo(getSeverity()) >= 0;
    }

    public boolean isParentAtLeast(LogProtos.Severity severity) {
      if (!(getParentDescriptor() instanceof Node)) {
        return false;
      }
      Node node = (Node)getParentDescriptor();
      return node.containsReportItem() && node.isAtLeast(severity);
    }

    public LogProtos.Severity getSeverity() {
      return myReportItem.getSeverity();
    }

    public long getAtomId() {
      return myReportItem.getAtom();
    }

    public String getMessage() {
      return (myMessage == null) ? myReportItem.getMessage().toString() : myMessage;
    }

    public String getMessagePreview() {
      int len = getMessage().length();
      if (len < PREVIEW_LENGTH) {
        return getMessage();
      }
      return getMessage().substring(0, PREVIEW_LENGTH) + "…";
    }

    @Nullable("if we have not made a server request yet")
    public Path getFollowPath() {
      return followPath;
    }

    public void setFollowPath(@NotNull Path followPath) {
      this.followPath = followPath;
    }

    public boolean isFollowable() {
      return followable;
    }

    public void setFollowable(boolean followable) {
      this.followable = followable;
    }

    public boolean isLeaf() {
      return myChildren.size() == 0;
    }

    private void constructMessage(@NotNull Report report) {
      MsgRef ref = myReportItem.getMessage();
      myMsgMap.clear();
      Arg.constructMap(report.getMsgArguments(), ref.getArguments(), myMsgMap);
      myMessage = StringTable.getMessage(report.getMsgIdentifiers()[ref.getIdentifier()], myMsgMap);
    }
  }

  /**
   * Renderer used for Nodes to allow them compose another component on the right.
   * See: {@link com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRenderer}.
   */
  public static class NodeCellRenderer extends ColoredTreeCellRenderer {
    private static Method myGetRowXMethod = null;

    /**
     * Workaround to get access to protected method from BasicTreeUI.
     * See: {@link com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRenderer#getRowX(BasicTreeUI, int, int)}.
     */
    private static int getRowX(BasicTreeUI ui, int row, int depth) {
      if (myGetRowXMethod == null) {
        try {
          myGetRowXMethod = BasicTreeUI.class.getDeclaredMethod("getRowX", int.class, int.class);
          myGetRowXMethod.setAccessible(true);
        }
        catch (NoSuchMethodException e) {
          LOG.error(e);
        }
      }
      if (myGetRowXMethod != null) {
        try {
          return (Integer)myGetRowXMethod.invoke(ui, row, depth);
        }
        catch (Exception e) {
          LOG.error(e);
        }
      }
      return 0;
    }

    private final RightCellRenderer myRightComponent = new RightCellRenderer();
    private int myRightComponentOffset;
    private boolean myRightComponentShow;

    public NodeCellRenderer() {
      super();
      setSupportFontFallback(true);
      getIpad().right = 0;
      myRightComponent.getIpad().left = 0;
    }

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      myRightComponentShow = false;
      myRightComponent.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
      if (value instanceof DefaultMutableTreeNode) {
        final Renderable renderable = ReportTreeBuilder.getRenderable(value);
        if (renderable != null) {
          // Append main content to `this`
          renderable.render(this, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          if (renderable instanceof Node && ((Node)renderable).isLeaf()) {
            final Rectangle treeVisibleRect = tree.getVisibleRect();
            // Check if our row is valid
            final TreePath treePath = tree.getPathForRow(row);
            // Use the hack instead of tree.getRowBounds(), since latter causes stack overflow sometimes
            int rowOffset = treePath != null ? getRowX((BasicTreeUI)tree.getUI(), row, treePath.getPathCount() - 1) : 0;
            if (super.getPreferredSize().width + rowOffset > treeVisibleRect.x + treeVisibleRect.width) {
              myRightComponent.append("…View", SimpleTextAttributes.GRAY_ATTRIBUTES, Render.REPORT_MESSAGE_VIEW_TAG);
              myRightComponentShow = true;
              myRightComponentOffset =
                treeVisibleRect.x + treeVisibleRect.width - myRightComponent.getPreferredSize().width - rowOffset;
            }
          }
        }
        // May be "loading..."
        else if (((DefaultMutableTreeNode)value).getUserObject() instanceof String) {
          append(String.valueOf(((DefaultMutableTreeNode)value).getUserObject()));
        }
      }
      else {
        assert false : value;
      }
      // Don't expand if there's a link to show
      putClientProperty(ExpandableItemsHandler.RENDERER_DISABLED, myRightComponentShow);
    }

    @Override
    protected void doPaint(Graphics2D g) {
      if (myRightComponentShow) {
        Graphics2D textGraphics = (Graphics2D)g.create(0, 0, myRightComponentOffset, g.getClipBounds().height);
        try {
          super.doPaint(textGraphics);
        }
        finally {
          textGraphics.dispose();
        }
        g.translate(myRightComponentOffset, 0);
        myRightComponent.setHeight(getHeight());
        myRightComponent.invokeDoPaint(g);
        g.translate(-myRightComponentOffset, 0);
      }
      else {
        super.doPaint(g);
      }
    }

    @NotNull
    @Override
    public Dimension getPreferredSize() {
      Dimension size = super.getPreferredSize();
      if (myRightComponentShow) {
        size.width += myRightComponent.getPreferredSize().width;
      }
      return size;
    }

    @Override
    public int findFragmentAt(int x) {
      if (myRightComponentShow && x > myRightComponentOffset) {
        return myRightComponent.findFragmentAt(x - myRightComponentOffset);
      }
      return super.findFragmentAt(x);
    }

    @Nullable
    @Override
    public Object getFragmentTagAt(int x) {
      if (myRightComponentShow) {
        return myRightComponent.getFragmentTagAt(x - myRightComponentOffset);
      }
      return super.getFragmentTagAt(x);
    }

    @Nullable
    public Object getFragmentTag(int index, int x) {
      if (myRightComponentShow && x > myRightComponentOffset) {
        return myRightComponent.getFragmentTag(index);
      }
      // Synchronized call
      return super.getFragmentTag(index);
    }
  }

  private static class RightCellRenderer extends ColoredTreeCellRenderer {
    private int myHeight;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
    }

    public void invokeDoPaint(Graphics2D g) {
      super.doPaint(g);
    }

    /**
     * Allow customization of height. See
     * {@link com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRenderer.MyColoredTreeCellRenderer}.
     */
    public void setHeight(int height) {
      myHeight = height;
    }

    @Override
    public int getHeight() {
      return myHeight;
    }
  }

  private static class ReportTreeStructure extends AbstractTreeStructure {
    @NotNull final Renderable myRoot;

    public ReportTreeStructure(@NotNull Renderable root) {
      myRoot = root;
    }

    @Override
    public Object getRootElement() {
      return myRoot;
    }

    @Override
    public Object[] getChildElements(Object element) {
      return ((Renderable)element).myChildren.toArray();
    }

    @Nullable
    @Override
    public Object getParentElement(Object element) {
      return ((NodeDescriptor)element).getParentDescriptor();
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      return (NodeDescriptor)element;
    }

    @Override
    public void commit() {
    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }
  }

  // Extends FilteringTreeBuilder in order to remove auto expand behaviour
  private static class ReportTreeBuilder extends FilteringTreeBuilder {
    /**
     * Tries to get underlying Renderable from a tree node assuming it's an
     * instance of DefaultMutableTreeNode and getting delegate from FilteringNode.
     */
    @Nullable("unexpected tree structure")
    public static Renderable getRenderable(@NotNull Object treeNode) {
      if (!(treeNode instanceof DefaultMutableTreeNode)) {
        return null;
      }
      final Object userObject = ((DefaultMutableTreeNode)treeNode).getUserObject();
      if (!(userObject instanceof FilteringTreeStructure.FilteringNode)) {
        return null;
      }
      Object delegate = ((FilteringTreeStructure.FilteringNode)userObject).getDelegate();
      return delegate instanceof Renderable ? (Renderable)delegate : null;
    }

    public ReportTreeBuilder(Tree tree,
                             ElementFilter filter,
                             AbstractTreeStructure structure,
                             @Nullable Comparator<NodeDescriptor> comparator) {
      super(tree, filter, structure, comparator);
    }

    @Override
    public boolean isAutoExpandNode(NodeDescriptor nodeDescriptor) {
      return false;
    }
  }

  private final JComboBox mySeverityLevelCombo;
  @Nullable private ReportTreeBuilder myTreeBuilder;
  @NotNull private final ElementFilterWrapper myFilter;

  private ReportController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.LOADING_CAPTURE);
    myEditor.getReportStream().addListener(this);

    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));

    mySeverityLevelCombo = new ComboBox(new String[]{
      LogProtos.Severity.Emergency.name(),
      LogProtos.Severity.Alert.name(),
      LogProtos.Severity.Critical.name(),
      LogProtos.Severity.Error.name(),
      LogProtos.Severity.Warning.name(),
      LogProtos.Severity.Notice.name(),
      LogProtos.Severity.Info.name(),
      LogProtos.Severity.Debug.name()
    });

    mySeverityLevelCombo.setSelectedIndex(mySeverityLevelCombo.getItemCount() - 1);
    mySeverityLevelCombo.addActionListener(
      actionEvent -> {
        LogProtos.Severity severity = LogProtos.Severity.valueOf((String)mySeverityLevelCombo.getSelectedItem());
        applySeverityFilter(severity);
      });
    myPanel.add(mySeverityLevelCombo, BorderLayout.NORTH);

    MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent event) {
        final TreePath treePath = myTree.getPathForLocation(event.getX(), event.getY());
        if (treePath == null) {
          return;
        }
        final Renderable renderable = ReportTreeBuilder.getRenderable(treePath.getLastPathComponent());
        if (renderable == null) {
          return;
        }
        PathStore<Path> followPath = new PathStore<>();
        switch (getComponentTag(treePath, event.getX())) {
          case Render.REPORT_ITEM_ATOM_ID_TAG:
            onReportItemNodeHover((Node)renderable, followPath);
            break;
          case Render.REPORT_MESSAGE_VIEW_TAG:
            // Put stub data to path store in order to show hand hovering.
            followPath.update(getReportItemPath((Node)renderable.getParentDescriptor()));
            break;
          default:
        }
        hoverHand(myTree, myEditor.getReportStream().getPath(), followPath.getPath());
      }

      @Override
      public void mouseExited(MouseEvent event) {
        hoverHand(myTree, myEditor.getReportStream().getPath(), null);
      }

      @Override
      public void mouseClicked(MouseEvent event) {
        final TreePath treePath = myTree.getPathForLocation(event.getX(), event.getY());
        if (treePath == null) {
          return;
        }
        final Renderable renderable = ReportTreeBuilder.getRenderable(treePath.getLastPathComponent());
        if (renderable == null) {
          return;
        }
        switch (getComponentTag(treePath, event.getX())) {
          case Render.REPORT_ITEM_ATOM_ID_TAG:
            // Here and further we assume that renderable is instance of Node
            // since otherwise we wouldn't get such a tag (only if constants clash).
            onReportItemNodeClick((Node)renderable);
            break;
          case Render.REPORT_MESSAGE_VIEW_TAG:
            onLeafNodeClick((Node)renderable);
            break;
          default:
        }
      }

      private void onReportItemNodeHover(@NotNull Node node, @NotNull PathStore<Path> pathStore) {
        final Path followPath = node.getFollowPath();
        if (followPath == null) {
          // set empty path so we do not make any more calls to the server for this path
          node.setFollowPath(Path.EMPTY);
          Path path = getReportItemPath(node);
          Futures.addCallback(myEditor.getClient().follow(path), new FutureCallback<Path>() {
            @Override
            public void onSuccess(Path result) {
              node.setFollowPath(result);
              pathStore.update(result);
            }

            @Override
            public void onFailure(Throwable t) {
              LOG.warn("Error for path " + path, t);
            }
          });
        }
        else {
          // Put existing path to pathStore in order to handle the node properly in further code.
          pathStore.update(followPath);
        }
      }

      private void onReportItemNodeClick(@NotNull Node node) {
        Path path = node.getFollowPath();
        if (path != null && path != Path.EMPTY) {
          UsageTracker.getInstance().log(AndroidStudioStats.AndroidStudioEvent.newBuilder()
                                           .setCategory(AndroidStudioStats.AndroidStudioEvent.EventCategory.GPU_PROFILER)
                                           .setKind(AndroidStudioStats.AndroidStudioEvent.EventKind.GFX_TRACE_LINK_CLICKED)
                                           .setGfxTracingDetails(AndroidStudioStats.GfxTracingDetails.newBuilder()
                                                                   .setTracePath(path.toString())));
          myEditor.activatePath(path, ReportController.this);
        }
        else {
          // this can happen if the server takes too long to respond, or responds with a error
          LOG.warn("mouseClicked(), but we don't have a path");
        }
      }

      private void onLeafNodeClick(@NotNull Node node) {
        ViewTextAction.MyDialog dialog = new ViewTextAction.MyDialog(myEditor.getProject());
        Node parent = (Node)node.getParentDescriptor();
        dialog.setTitle(String.format("Message for %d: %s", parent.getAtomId(), parent.getSeverity()));
        dialog.setText(node.getMessage());
        dialog.show();
      }
    };

    myTree.addMouseListener(mouseAdapter);
    myTree.addMouseMotionListener(mouseAdapter);
    myFilter = new ElementFilterWrapper();
    myFilter.setFilter(input -> true);
  }

  private @NotNull Path getReportItemPath(@NotNull Node node) {
    ReportItemPath path = new ReportItemPath();
    path.setReport(myEditor.getReportStream().getPath());
    path.setIndex(node.index);
    return path;
  }

  @Override
  public void notifyPath(PathEvent event) {

  }

  @NotNull
  @Override
  protected TreeCellRenderer createRenderer() {
    return new NodeCellRenderer();
  }

  @NotNull
  @Override
  protected TreeModel createEmptyModel() {
    return new DefaultTreeModel(new DefaultMutableTreeNode());
  }

  @NotNull
  @Override
  public String[] getColumns(TreePath path) {
    Object object = path.getLastPathComponent();
    SimpleColoredComponent component = new SimpleColoredComponent();
    if (object instanceof ReportController.Node) {
      Node node = (ReportController.Node)object;
      node.render(component, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      return new String[]{node.getAtomId() + ":", node.getMessage()};
    }
    return new String[]{object.toString()};
  }

  @Override
  public void onReportLoadingStart(ReportStream reportStream) {
    myTree.getEmptyText().setText("");
    myLoadingPanel.startLoading();
  }

  @Override
  public void onReportLoadingFailure(ReportStream reportStream, String errorMessage) {
    // TODO: Display detailed empty view and/or error message
    myLoadingPanel.showLoadingError("Failed to load report");
  }

  @Override
  public void onReportLoadingSuccess(ReportStream reportStream) {
    if (reportStream.isLoaded()) {
      myLoadingPanel.stopLoading();
      buildTree(reportStream);
    }
    else {
      myLoadingPanel.showLoadingError("Failed to load report");
    }
  }

  @Override
  public void onReportItemSelected(ReportItem reportItem) {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode)myTree.getModel().getRoot();
    updateSelection(root, new TreePath(root), reportItem.getAtom());
  }

  private void updateSelection(DefaultMutableTreeNode root, TreePath path, long atomId) {
    // TODO: If server provides report items sorted by atom id, replace linear for binary search.
    // TODO: UPD: It does. Do replace.
    for (Enumeration it = root.children(); it.hasMoreElements(); ) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)it.nextElement();
      Object object = child.getUserObject();
      if (object instanceof FilteringTreeStructure.FilteringNode) {
        Object delegate = ((FilteringTreeStructure.FilteringNode)object).getDelegate();
        if (delegate instanceof Node) {
          Node node = (Node)delegate;
          if (!node.isLeaf() && node.getAtomId() == atomId) {
            updateSelection(path.pathByAddingChild(child));
            return;
          }
        }
      }
    }
  }

  private void updateSelection(TreePath path) {
    myTree.expandPath(path);
    myTree.setSelectionPath(path);

    int row = myTree.getRowForPath(path);
    if (row >= myTree.getRowCount()) {
      row = myTree.getRowCount() - 1;
    }
    myTree.scrollPathToVisible(myTree.getPathForRow(row));
    myTree.scrollPathToVisible(path);
  }

  private void buildTree(ReportStream reportStream) {
    final Renderable root = new Renderable(null) {
      @Override
      public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      }
    };
    int index = 0;
    final Report report = reportStream.getReport();
    for (ReportItem item : report.getItems()) {
      root.addChild(Node.createInstance(root, report, item, index++));
    }
    myTreeBuilder = new ReportTreeBuilder(myTree, myFilter, new ReportTreeStructure(root), null);
    Disposer.register(myEditor, myTreeBuilder);
    final ActionCallback updateCallback = myTreeBuilder.queueUpdate();
    // Filter could have been changed before tree was built.
    updateCallback.doWhenDone(() -> myTreeBuilder.refilter());
  }

  private void applySeverityFilter(@NotNull final LogProtos.Severity severity) {
    myFilter.setFilter(input -> {
      if (!(input instanceof Node)) {
        return true;
      }
      Node node = (Node)input;
      // ReportItem has proper severity level
      return ((node.containsReportItem() && node.isAtLeast(severity)) ||
              // Parent ReportItem has proper severity level
              (!node.containsReportItem() && node.isParentAtLeast(severity)));
    });
    if (myTreeBuilder != null) {
      myTreeBuilder.refilter();
    }
  }

  private int getComponentTag(@NotNull TreePath treePath, int mouseX) {
    Object lastPathComponent = treePath.getLastPathComponent();
    if (!(lastPathComponent instanceof DefaultMutableTreeNode)) {
      return Render.NO_TAG;
    }
    Renderable renderable = ReportTreeBuilder.getRenderable(lastPathComponent);
    if (renderable == null || !(renderable instanceof Node)) {
      return Render.NO_TAG;
    }
    Rectangle bounds = myTree.getPathBounds(treePath);
    assert bounds != null; // can't be null, as our path is valid
    NodeCellRenderer renderer = (NodeCellRenderer)myTree.getCellRenderer();
    if (renderer == null) {
      return Render.NO_TAG;
    }
    renderer.getTreeCellRendererComponent(myTree, lastPathComponent, myTree.isPathSelected(treePath), myTree.isExpanded(treePath),
                                          ((Node)renderable).isLeaf(), myTree.getRowForPath(treePath), myTree.hasFocus());
    return Render.getReportNodeFieldIndex(renderer, mouseX - bounds.x);
  }
}