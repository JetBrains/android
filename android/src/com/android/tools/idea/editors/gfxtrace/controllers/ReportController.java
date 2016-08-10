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
import com.android.tools.idea.editors.gfxtrace.service.ReportItem;
import com.android.tools.idea.editors.gfxtrace.service.log.LogProtos;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.android.tools.idea.editors.gfxtrace.service.path.ReportItemPath;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Enumeration;

// TODO: Check if there's a need of TreeController (probably some kind of ListController will satisfy this entity).
public class ReportController extends TreeController implements ReportStream.Listener {
  public static JComponent createUI(@NotNull GfxTraceEditor editor) {
    return new ReportController(editor).myPanel;
  }

  private static final @NotNull Logger LOG = Logger.getInstance(ReportController.class);

  private interface Renderable {
    void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes);
  }

  public static class Node extends DefaultMutableTreeNode implements Renderable {
    private static final int PREVIEW_LENGTH = 80;

    public final int index;
    private @Nullable("not made server request yet") Path followPath;
    // True by default and is false when GAPIS doesn't support ReportItemPath
    private boolean followable = true;

    public static Node createInstance(ReportItem item, int index) {
      Node node = new Node(item, index);
      node.add(new Node(item.getMessage().toString()));
      return node;
    }

    private Node(ReportItem item, int index) {
      super(item);
      this.index = index;
    }

    private Node(String message) {
      super(message, false);
      this.index = -1;
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      Render.render(this, component);
    }

    public LogProtos.Severity getSeverity() {
      return ((ReportItem) userObject).getSeverity();
    }

    public long getAtomId() {
      return ((ReportItem) userObject).getAtom();
    }

    public String getMessage() {
      if (userObject instanceof ReportItem) {
        return ((ReportItem)userObject).getMessage().toString();
      }
      return (String)userObject;
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
  }

  /**
   * Renderer used for Nodes to allow them compose another component on the right.
   * See: {@link com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRenderer}.
   */
  public static class NodeCellRenderer extends ColoredTreeCellRenderer {
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
      if (value instanceof Renderable) {
        Renderable renderable = (Renderable)value;
        // Append main content to `this`
        renderable.render(this, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        if (renderable instanceof Node && ((Node)renderable).isLeaf()) {
          final Rectangle treeVisibleRect = tree.getVisibleRect();
          final int rowOffset = tree.getRowBounds(row).x;
          if (super.getPreferredSize().width + rowOffset > treeVisibleRect.x + treeVisibleRect.width) {
            myRightComponent.append("…View", SimpleTextAttributes.GRAY_ATTRIBUTES, Render.REPORT_MESSAGE_VIEW_TAG);
            myRightComponentShow = true;
            myRightComponentOffset = treeVisibleRect.x + treeVisibleRect.width - myRightComponent.getPreferredSize().width - rowOffset;
          }
        }
      }
      else if (value instanceof DefaultMutableTreeNode) {
        // Root of the report, no need to render.
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

  private ReportController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.LOADING_CAPTURE);
    myEditor.getReportStream().addListener(this);

    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));

    MouseAdapter mouseAdapter = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent event) {
        final TreePath treePath = myTree.getPathForLocation(event.getX(), event.getY());
        if (treePath == null) {
          return;
        }
        final Object currentComponent = treePath.getLastPathComponent();
        PathStore<Path> followPath = new PathStore<>();
        switch (getComponentTag(treePath, event.getX())) {
          case Render.REPORT_ITEM_ATOM_ID_TAG:
            onReportItemNodeHover((Node)currentComponent, followPath);
            break;
          case Render.REPORT_MESSAGE_VIEW_TAG:
            // Put stub data to path store in order to show hand hovering.
            followPath.update(getReportItemPath((Node)((Node)currentComponent).getParent()));
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
        final Object currentComponent = treePath.getLastPathComponent();
        switch (getComponentTag(treePath, event.getX())) {
          case Render.REPORT_ITEM_ATOM_ID_TAG:
            onReportItemNodeClick((Node)currentComponent);
            break;
          case Render.REPORT_MESSAGE_VIEW_TAG:
            onLeafNodeClick((Node)currentComponent);
            break;
          default:
        }
      }

      private void onReportItemNodeHover(@NotNull Node node, @NotNull PathStore<Path> pathStore) {
        final Path followPath = node.getFollowPath();
        if (followPath == null) {
          // set empty path so we do not make any more calls to the server for this path
          node.setFollowPath(Path.EMPTY);
          if (myEditor.getFeatures().hasReportItems()) {
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
            node.setFollowable(false);
          }
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
        else if (myEditor.getFeatures().hasReportItems()) {
          // this can happen if the server takes too long to respond, or responds with a error
          LOG.warn("mouseClicked(), but we don't have a path");
        }
        else {
          LOG.warn("mouseClicked(), but ReportItemPath are not supported in GAPIS");

        }
      }

      private void onLeafNodeClick(@NotNull Node node) {
        ViewTextAction.MyDialog dialog = new ViewTextAction.MyDialog(myEditor.getProject());
        Node parent = (Node)node.getParent();
        dialog.setTitle(String.format("Message for %d: %s", parent.getAtomId(), parent.getSeverity()));
        dialog.setText(node.getMessage());
        dialog.show();
      }
    };

    myTree.addMouseListener(mouseAdapter);
    myTree.addMouseMotionListener(mouseAdapter);
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
      updateTree(reportStream);
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
    for (Enumeration it = root.children(); it.hasMoreElements(); ) {
      DefaultMutableTreeNode child = (DefaultMutableTreeNode)it.nextElement();
      Object object = child.getUserObject();
      if (object instanceof ReportItem && atomId == (((ReportItem)object).getAtom())) {
        updateSelection(path.pathByAddingChild(child));
        return;
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

  private void updateTree(ReportStream reportStream) {
    final DefaultMutableTreeNode root = new DefaultMutableTreeNode("Report", true);
    int index = 0;
    for (ReportItem item : reportStream.getReport().getItems()) {
      root.add(Node.createInstance(item, index++));
    }
    setRoot(root);
  }

  private void setRoot(DefaultMutableTreeNode root) {
    setModel(new DefaultTreeModel(root));
  }

  private int getComponentTag(@NotNull TreePath treePath, int mouseX) {
    Node node = (Node)treePath.getLastPathComponent();
    Rectangle bounds = myTree.getPathBounds(treePath);
    assert bounds != null; // can't be null, as our path is valid
    NodeCellRenderer renderer = (NodeCellRenderer)myTree.getCellRenderer();
    if (renderer == null) {
      return Render.NO_TAG;
    }
    renderer.getTreeCellRendererComponent(myTree, node, myTree.isPathSelected(treePath), myTree.isExpanded(treePath),
                                          node.isLeaf(), myTree.getRowForPath(treePath), myTree.hasFocus());
    return Render.getReportNodeFieldIndex(renderer, mouseX - bounds.x);
  }
}