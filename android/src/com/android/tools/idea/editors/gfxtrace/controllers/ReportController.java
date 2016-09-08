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
import com.android.tools.idea.editors.gfxtrace.service.*;
import com.android.tools.idea.editors.gfxtrace.service.log.LogProtos;
import com.android.tools.idea.editors.gfxtrace.service.msg.Arg;
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.service.path.PathStore;
import com.android.tools.idea.editors.gfxtrace.service.path.ReportItemPath;
import com.android.tools.idea.editors.gfxtrace.service.path.ReportPath;
import com.android.tools.idea.editors.gfxtrace.service.stringtable.StringTable;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.utils.SparseArray;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

// TODO: Check if there's a need of TreeController (probably some kind of ListController will satisfy this entity).
public class ReportController extends TreeController implements ReportStream.Listener {
  public static JComponent createUI(@NotNull GfxTraceEditor editor) {
    return new ReportController(editor).myPanel;
  }

  private static final @NotNull Logger LOG = Logger.getInstance(ReportController.class);
  private static final Map<String, BinaryObject> myMsgMap = new HashMap<>();

  public abstract static class Renderable {

    protected static final int CACHE_SIZE = 100;

    protected SparseArray<Reference<Renderable>> mySoftChildren;
    protected Context myContext;
    protected boolean myViewInvalid;
    @NotNull protected final Report myReport;

    public final int myChildIndex;

    public Renderable(@NotNull Report report, int childIndex) {
      myReport = report;
      myChildIndex = childIndex;
    }

    public void setContext(Context context) {
      if (context != myContext) {
        myContext = context;
        myViewInvalid = true;
      }
    }

    public Renderable getChild(int childIndex) {
      setupLazy();

      Reference<Renderable> ref = mySoftChildren.get(childIndex);
      Renderable child;
      if (ref != null) {
        child = ref.get();
        if (child != null) {
          return child;
        }
      }

      // TODO: React to context selection
      child = createChild(childIndex);
      child.setContext(myContext);
      mySoftChildren.put(childIndex, new SoftReference<>(child));
      return child;
    }

    public abstract void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes);

    public abstract TreePath pathTo(ReportItem item, TreePath root);

    public abstract boolean isLeaf();

    public abstract int getChildCount();

    protected abstract void setupLazy();

    protected abstract Renderable createChild(int childIndex);
  }

  public static class ReportNode extends Renderable {

    public ReportNode(@NotNull Report report) {
      super(report, 0);
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
    }

    @Override
    public TreePath pathTo(ReportItem item, TreePath root) {
      for (int i = 0; i < getChildCount(); ++i) {
        if (myReport.getGroups()[i].containsItemForAtom(item.getAtom(), myReport)) {
          Group child = (Group)getChild(i);
          return child.pathTo(item, root.pathByAddingChild(child));
        }
      }
      return null;
    }

    @Override
    public boolean isLeaf() {
      return myReport.getChildCount() == 0;
    }

    @Override
    public int getChildCount() {
      return myReport.getChildCount();
    }

    @Override
    protected void setupLazy() {
      if (myViewInvalid) {
        mySoftChildren = new SparseArray<>(Math.min(myReport.getChildCount(), CACHE_SIZE));
        myViewInvalid = false;
      }
    }

    @Override
    protected Renderable createChild(int childIndex) {
      return Group.createInstance(myReport, myReport.getGroups()[childIndex], childIndex);
    }
  }

  public static class Group extends Renderable {

    public static Group createInstance(@NotNull Report report,
                                       @NotNull ReportGroup reportGroup,
                                       int childIndex) {
      Group group = new Group(report, reportGroup, childIndex);
      group.constructName(report);
      return group;
    }

    @NotNull private final ReportGroup myReportGroup;
    @Nullable private String myName;

    public Group(@NotNull Report report, @NotNull ReportGroup group, int childIndex) {
      super(report, childIndex);
      myReportGroup = group;
    }

    public void constructName(@NotNull Report report) {
      MsgRef ref = myReportGroup.getName();
      myMsgMap.clear();
      Arg.constructMap(report.getMsgArguments(), ref.getArguments(), myMsgMap);
      myName = StringTable.getMessage(report.getMsgIdentifiers()[ref.getIdentifier()], myMsgMap);
    }

    @Nullable
    public String getName() {
      return myName;
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      Render.render(this, component);
    }

    @Override
    public TreePath pathTo(ReportItem item, TreePath root) {
      final int foundIndex = myReportGroup.findItem(myReport, item);
      return foundIndex == -1 ? null : root.pathByAddingChild(getChild(foundIndex));
    }

    @Override
    public boolean isLeaf() {
      return false;
    }

    @Override
    public int getChildCount() {
      return myReportGroup.getItemCount();
    }

    @Override
    protected void setupLazy() {
      if (myViewInvalid) {
        mySoftChildren = new SparseArray<>(Math.min(myReportGroup.getItemCount(), CACHE_SIZE));
        myViewInvalid = false;
      }
    }

    @Override
    protected Renderable createChild(int childIndex) {
      int itemIndex = myReportGroup.getItems()[childIndex];
      return new Node(myReport, myReport.getItems()[itemIndex], childIndex, itemIndex);
    }
  }

  // Node to be used as a representation for report item and its message.
  public static class Node extends Renderable {
    public final int index;
    @NotNull private final ReportItem myReportItem;
    @Nullable("not made server request yet") private Path followPath;

    public Node(@NotNull Report report, @NotNull ReportItem item, int childIndex, int index) {
      super(report, childIndex);
      this.myReportItem = item;
      this.index = index;
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      Render.render(this, component);
    }

    @Override
    public TreePath pathTo(ReportItem item, TreePath root) {
      throw new UnsupportedOperationException();
    }

    public boolean isAtLeast(LogProtos.Severity severity) {
      // LogProtos.Severity enum increases from Emergency to Debug
      return severity.compareTo(getSeverity()) >= 0;
    }

    public LogProtos.Severity getSeverity() {
      return myReportItem.getSeverity();
    }

    public long getAtomId() {
      return myReportItem.getAtom();
    }

    @Nullable("if we have not made a server request yet")
    public Path getFollowPath() {
      return followPath;
    }

    public void setFollowPath(@NotNull Path followPath) {
      this.followPath = followPath;
    }

    @Override
    public boolean isLeaf() {
      return true;
    }

    @Override
    public Renderable getChild(int index) {
      return null;
    }

    @Override
    public int getChildCount() {
      return 0;
    }

    @Override
    protected void setupLazy() {

    }

    @Override
    protected Renderable createChild(int childIndex) {
      return null;
    }

  }

  private static class ReportTreeModel implements TreeModel {

    static ReportTreeModel createEmpty() {
      return new ReportTreeModel(new Object());
    }

    @NotNull private final Object myRoot;

    public ReportTreeModel(@NotNull Object root) {
      myRoot = root;
    }

    @NotNull
    @Override
    public Object getRoot() {
      return myRoot;
    }

    @Override
    public Object getChild(Object element, int index) {
      if (element instanceof Renderable) {
        return ((Renderable)element).getChild(index);
      }
      return null;
    }

    @Override
    public int getChildCount(Object element) {
      if (element instanceof Renderable) {
        return ((Renderable)element).getChildCount();
      }
      return 0;
    }

    @Override
    public boolean isLeaf(Object element) {
      if (element instanceof Renderable) {
        return ((Renderable)element).isLeaf();
      }
      return false;
    }

    @Override
    public void valueForPathChanged(TreePath treePath, Object o) {
      throw new UnsupportedOperationException(getClass().getName() + " does not support editing");
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
      if (child instanceof Renderable) {
        return ((Renderable)child).myChildIndex;
      }
      return 0;
    }

    @Override
    public void addTreeModelListener(TreeModelListener treeModelListener) {
      // No changes - no events.
    }

    @Override
    public void removeTreeModelListener(TreeModelListener treeModelListener) {
      // No changes - no events.
    }
  }

  private final JComboBox mySeverityLevelCombo;

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
        final Object obj = treePath.getLastPathComponent();
        if (!(obj instanceof Renderable)) {
          return;
        }
        final Renderable renderable = (Renderable)obj;
        PathStore<Path> followPath = new PathStore<>();
        switch (getComponentTag(treePath, event.getX())) {
          case Render.REPORT_ITEM_ATOM_ID_TAG:
            onReportItemNodeHover((Node)renderable, followPath);
            break;
          case Render.REPORT_MESSAGE_VIEW_TAG:
            // Put stub data to path store in order to show hand hovering.
            followPath.update(new ReportPath());
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
        final Object obj = treePath.getLastPathComponent();
        if (!(obj instanceof Renderable)) {
          return;
        }
        final Renderable renderable = (Renderable)obj;
        switch (getComponentTag(treePath, event.getX())) {
          case Render.REPORT_ITEM_ATOM_ID_TAG:
            // Here and further we assume that renderable is instance of Node
            // since otherwise we wouldn't get such a tag (only if constants clash).
            onReportItemNodeClick((Node)renderable);
            break;
          case Render.REPORT_MESSAGE_VIEW_TAG:
            onGroupClick((Group)renderable);
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

      /**
       * Currently handles only message expansion.
       */
      private void onGroupClick(@NotNull Group group) {
        ViewTextAction.MyDialog dialog = new ViewTextAction.MyDialog(myEditor.getProject());
        dialog.setTitle("Message");
        dialog.setText(group.getName());
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
    return new CompositeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        myRightComponentShow = false;
        if (value instanceof Renderable) {
          final Renderable renderable = (Renderable)value;
          if (!(renderable instanceof ReportNode)) {
            // Append main content to `this`
            renderable.render(this, SimpleTextAttributes.REGULAR_ATTRIBUTES);
            if (renderable instanceof Group) {
              final Rectangle treeVisibleRect = tree.getVisibleRect();
              // Check if our row is valid
              final TreePath treePath = tree.getPathForRow(row);
              int rowOffset = treePath != null ? tree.getPathBounds(treePath).x : 0;
              if (super.getPreferredSize().width + rowOffset > treeVisibleRect.x + treeVisibleRect.width) {
                myRightComponent.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                myRightComponent.append("…View", SimpleTextAttributes.GRAY_ATTRIBUTES, Render.REPORT_MESSAGE_VIEW_TAG);
                myRightComponent.append(" " + renderable.getChildCount(), SimpleTextAttributes.SYNTHETIC_ATTRIBUTES,
                                        Render.NO_TAG);
                myRightComponentShow = true;
                myRightComponentOffset =
                  treeVisibleRect.x + treeVisibleRect.width - myRightComponent.getPreferredSize().width - rowOffset;
              }
            }
          }
        }
        else {
          // Tree is not built yet.
          return;
        }
        // Don't expand if there's a link to show
        putClientProperty(ExpandableItemsHandler.RENDERER_DISABLED, myRightComponentShow);
      }
    };
  }

  @NotNull
  @Override
  protected TreeModel createEmptyModel() {
    return ReportTreeModel.createEmpty();
  }

  @NotNull
  @Override
  public String[] getColumns(TreePath path) {
    Object object = path.getLastPathComponent();
    SimpleColoredComponent component = new SimpleColoredComponent();
    if (object instanceof ReportController.Node) {
      Node node = (ReportController.Node)object;
      node.render(component, SimpleTextAttributes.REGULAR_ATTRIBUTES);
      return new String[]{node.getSeverity().toString() + node.getAtomId()};
    }
    return new String[]{object.toString()};
  }

  @Override
  public void onReportLoadingStart(ReportStream reportStream) {
    myTree.getEmptyText().setText("");
    mySeverityLevelCombo.setVisible(true);
    myLoadingPanel.startLoading();
  }

  @Override
  public void onReportLoadingFailure(ReportStream reportStream, String errorMessage) {
    mySeverityLevelCombo.setVisible(false);
    // TODO: Display detailed empty view and/or error message
    myLoadingPanel.showLoadingError("Failed to load report");
  }

  @Override
  public void onReportLoadingSuccess(ReportStream reportStream) {
    if (reportStream.isLoaded()) {
      myLoadingPanel.stopLoading();
      myTree.getEmptyText().setText("Report is empty");
      buildTree(reportStream);
    }
    else {
      myLoadingPanel.showLoadingError("Failed to load report");
    }
  }

  @Override
  public void onReportItemSelected(ReportItem reportItem) {
    Renderable root = (Renderable)myTree.getModel().getRoot();
    updateSelection(root, new TreePath(root), reportItem);
  }

  private void updateSelection(Renderable root, TreePath path, final ReportItem reportItem) {
    TreePath treePath = root.pathTo(reportItem, path);
    if (treePath != null) {
      updateSelection(treePath);
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
    final Report report = reportStream.getReport();
    if (report == null) {
      return;
    }
    final Renderable root = new ReportNode(report);
    root.setContext(Context.ALL);
    if (report.getChildCount() > 0) {
      setModel(new ReportTreeModel(root));
    }
    else {
      mySeverityLevelCombo.setVisible(false);
    }
  }

  private void applySeverityFilter(@NotNull final LogProtos.Severity severity) {
    // TODO: Implement filtering in model.
  }

  private int getComponentTag(@NotNull TreePath treePath, int mouseX) {
    Object lastPathComponent = treePath.getLastPathComponent();
    if (!(lastPathComponent instanceof Renderable)) {
      return Render.NO_TAG;
    }
    Renderable renderable = (Renderable)lastPathComponent;
    Rectangle bounds = myTree.getPathBounds(treePath);
    assert bounds != null; // can't be null, as our path is valid
    CompositeCellRenderer renderer = (CompositeCellRenderer)myTree.getCellRenderer();
    if (renderer == null) {
      return Render.NO_TAG;
    }
    renderer.getTreeCellRendererComponent(myTree, lastPathComponent, myTree.isPathSelected(treePath), myTree.isExpanded(treePath),
                                          renderable.isLeaf(), myTree.getRowForPath(treePath), myTree.hasFocus());
    return Render.getFieldIndex(renderer, mouseX - bounds.x);
  }
}