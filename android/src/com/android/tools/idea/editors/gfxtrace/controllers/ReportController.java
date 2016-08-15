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
import com.android.tools.idea.editors.gfxtrace.forms.ReportControls;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.models.ReportStream;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.*;
import com.android.tools.idea.editors.gfxtrace.service.log.LogProtos;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.TreeUtil;
import com.android.tools.rpclib.binary.BinaryObject;
import com.android.utils.SparseArray;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.ui.ExpandableItemsHandler;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBList;
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
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.*;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ReportController extends TreeController implements ReportStream.Listener, AtomStream.Listener {
  public static JComponent createUI(@NotNull GfxTraceEditor editor) {
    return new ReportController(editor).myPanel;
  }

  private static final @NotNull Logger LOG = Logger.getInstance(ReportController.class);

  public abstract static class Renderable {

    protected static final int CACHE_SIZE = 100;

    protected SparseArray<Reference<Renderable>> mySoftChildren;
    @NotNull protected final Report myReport;

    public final int myChildIndex;

    @NotNull protected Predicate<BinaryObject> myFilter = x -> true;

    /**
     * Filtered index -> Delegate index map.
     * TODO: It would be better to use a bijective structure here, because both key and value are sets.
     */
    @NotNull protected Map<Integer, Integer> myIndexMap = new HashMap<>();

    public Renderable(@NotNull Report report, int childIndex) {
      myReport = report;
      myChildIndex = childIndex;
    }

    public int getChildCount() {
      // Might be invalidated before this call.
      filterIfInvalid();
      return myIndexMap.size();
    }

    /**
     * @param filteredIndex Index in a model, continuously ranging from 1 to filtered child count, mapped to a delegate index.
     */
    @Nullable
    public Renderable getChild(int filteredIndex) {
      filterIfInvalid();
      if (!myIndexMap.containsKey(filteredIndex)) {
        return null;
      }
      if (mySoftChildren == null) {
        mySoftChildren = new SparseArray<>(Math.min(getDelegateChildCount(), CACHE_SIZE));
      }

      Reference<Renderable> ref = mySoftChildren.get(filteredIndex);
      Renderable child;
      if (ref != null) {
        child = ref.get();
        if (child != null) {
          return child;
        }
      }

      child = createChild(filteredIndex, myIndexMap.get(filteredIndex));
      child.myFilter = myFilter;
      mySoftChildren.put(filteredIndex, new SoftReference<>(child));
      return child;
    }

    public void resetFilter(@NotNull Predicate<BinaryObject> filter) {
      if (!Objects.equals(myFilter, filter)) {
        myFilter = filter;
        myIndexMap.clear();
        if (mySoftChildren != null) {
          mySoftChildren.clear();
        }
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof Renderable)) return false;
      Renderable that = (Renderable)o;
      if (!getDelegate().equals(that.getDelegate())) return false;
      return true;
    }

    @Override
    public int hashCode() {
      return getDelegate().hashCode();
    }

    protected void filterIfInvalid() {
      if (myIndexMap.isEmpty()) {
        int filteredIndex = 0;
        for (int i = 0; i < getDelegateChildCount(); i++) {
          if (myFilter.test(getDelegateChild(i))) {
            myIndexMap.put(filteredIndex++, i);
          }
        }
      }
    }

    public abstract void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes);

    public abstract TreePath pathTo(ReportItem item, TreePath root);

    /**
     * @return BinaryObject instance which this Renderable represents, e.g. specific ReportItem for Node.
     */
    public abstract BinaryObject getDelegate();

    public abstract int getDelegateChildCount();

    public abstract BinaryObject getDelegateChild(int childIndex);

    /**
     * Creates new renderable as a child of current with a specified filtered child index.
     * @param delegateIndex Index in BinaryObject's array (aka delegate index).
     */
    protected abstract Renderable createChild(int filteredIndex, int delegateIndex);
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
      for (int i = 0; i < getDelegateChildCount(); ++i) {
        if (myReport.getGroups()[i].containsItemForAtom(item.getAtom(), myReport)) {
          Group child = (Group)getChild(i);
          return child.pathTo(item, root.pathByAddingChild(child));
        }
      }
      return null;
    }

    @Override
    protected Renderable createChild(int filteredIndex, int delegateIndex) {
      return new Group(myReport, myReport.getGroups()[delegateIndex], filteredIndex);
    }

    @Override
    public BinaryObject getDelegate() {
      return myReport;
    }

    @Override
    public int getDelegateChildCount() {
      return myReport.getGroups().length;
    }

    @Override
    public BinaryObject getDelegateChild(int childIndex) {
      return myReport.getGroups()[childIndex];
    }
  }

  public static class Group extends Renderable {

    @NotNull private final ReportGroup myReportGroup;
    @Nullable private String myName;

    public Group(@NotNull Report report, @NotNull ReportGroup group, int childIndex) {
      super(report, childIndex);
      myReportGroup = group;
      myName = report.getOrConstructMessage(myReportGroup.getName());
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
    protected Renderable createChild(int filteredIndex, int delegateIndex) {
      int itemIndex = myReportGroup.getItems()[delegateIndex];
      return new Node(myReport, myReport.getItems()[itemIndex], filteredIndex, itemIndex);
    }

    @Override
    public BinaryObject getDelegate() {
      return myReportGroup;
    }

    @Override
    public int getDelegateChildCount() {
      return myReportGroup.getItems().length;
    }

    @Override
    public BinaryObject getDelegateChild(int childIndex) {
      return myReport.getItems()[myReportGroup.getItems()[childIndex]];
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
    protected Renderable createChild(int filteredIndex, int delegateIndex) {
      return new Tag(myReport, myReportItem.getTags()[delegateIndex], filteredIndex);
    }

    @Override
    public BinaryObject getDelegate() {
      return myReportItem;
    }

    @Override
    public int getDelegateChildCount() {
      return myReportItem.getTags().length;
    }

    @Override
    public BinaryObject getDelegateChild(int childIndex) {
      return myReportItem.getTags()[childIndex];
    }

    public boolean hasTags() {
      return myReportItem.getTags().length > 0;
    }

    public List<String> getTagStrings() {
      return Arrays.stream(myReportItem.getTags()).map(myReport::getOrConstructMessage).collect(Collectors.toList());
    }
  }

  public static class Tag extends Renderable {

    @NotNull private final MsgRef myRef;

    public Tag(@NotNull Report report, @NotNull MsgRef ref, int childIndex) {
      super(report, childIndex);
      myRef = ref;
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      Render.render(this, component);
    }

    @Override
    public TreePath pathTo(ReportItem item, TreePath root) {
      throw new UnsupportedOperationException();
    }

    @Override
    public BinaryObject getDelegate() {
      return myRef;
    }

    @Override
    public int getDelegateChildCount() {
      return 0;
    }

    @Override
    public BinaryObject getDelegateChild(int childIndex) {
      return null;
    }

    @Override
    protected Renderable createChild(int filteredIndex, int delegateIndex) {
      return null;
    }

    @NotNull
    public String getString() {
      return myReport.getOrConstructMessage(myRef);
    }
  }

  private class ReportTreeModel implements TreeModel {

    /**
     * Finds first matching child in a group.
     */
    private boolean testGroup(@NotNull final ReportGroup group, @NotNull Predicate<BinaryObject> filter, @NotNull Report report) {
      // We seek for the first item that satisfies filter condition in ReportGroup.
      for (Integer index : group.getItems()) {
        if (filter.test(report.getItems()[index])) {
          return true;
        }
      }
      return false;
    }

    /**
     * Predicate with a state, needed to fire model's predicate change events conveniently.
     */
    private class SeverityFilter implements Predicate<BinaryObject> {
      @NotNull private final LogProtos.Severity myMinimumSeverity;

      public SeverityFilter() {
        myMinimumSeverity = LogProtos.Severity.Debug;
      }

      public SeverityFilter(@NotNull LogProtos.Severity minimumSeverity) {
        myMinimumSeverity = minimumSeverity;
      }

      @Override
      public boolean test(BinaryObject binaryObject) {
        if (binaryObject instanceof Report || binaryObject instanceof MsgRef) {
          // Report and Tags are always shown by default.
          return true;
        }
        if (binaryObject instanceof ReportGroup && myReport != null) {
          return testGroup((ReportGroup)binaryObject, this, myReport);
        }
        if (binaryObject instanceof ReportItem) {
          return ((ReportItem)binaryObject).isAtLeast(myMinimumSeverity);
        }
        return false;
      }
    }

    /**
     * Filters a ReportItem by intersecting its tags and myTags
     */
    private class TagFilter implements Predicate<BinaryObject> {
      @NotNull private final ImmutableSet<MsgRef> myTags;

      public TagFilter() {
        myTags = ImmutableSet.of();
      }

      public TagFilter(final TagFilter prev, @NotNull final MsgRef ref) {
        ImmutableSet.Builder<MsgRef> builder = ImmutableSet.builder();
        if (prev != null) {
          builder.addAll(prev.myTags);
        }
        myTags = builder.add(ref).build();
      }

      @Override
      public boolean test(BinaryObject binaryObject) {
        if (myTags.isEmpty() || binaryObject instanceof Report || binaryObject instanceof MsgRef) {
          return true;
        }
        if (binaryObject instanceof ReportGroup && myReport != null) {
          return testGroup((ReportGroup)binaryObject, this, myReport);
        }
        if (binaryObject instanceof ReportItem) {
          return ImmutableSet.copyOf(((ReportItem)binaryObject).getTags()).containsAll(myTags);
        }
        return false;
      }
    }

    private class ContextFilter implements Predicate<BinaryObject> {
      @NotNull private final Context mySelectedContext;

      public ContextFilter() {
        mySelectedContext = Context.ALL;
      }

      public ContextFilter(@NotNull Context selectedContext) {
        mySelectedContext = selectedContext;
      }

      @Override
      public boolean test(BinaryObject binaryObject) {
        if (mySelectedContext == Context.ALL || binaryObject instanceof Report || binaryObject instanceof MsgRef) {
          return true;
        }
        if (binaryObject instanceof ReportGroup && myReport != null) {
          return testGroup((ReportGroup)binaryObject, this, myReport);
        }
        if (binaryObject instanceof ReportItem) {
          return mySelectedContext.contains(((ReportItem)binaryObject).getAtom());
        }
        return false;
      }
    }

    @NotNull private Object myRoot;
    @Nullable private Report myReport;

    @NotNull private Predicate<BinaryObject> myFilter = x -> true;
    @NotNull private SeverityFilter mySeverityFilter = new SeverityFilter();
    @NotNull private TagFilter myTagFilter = new TagFilter();
    @NotNull private ContextFilter myContextFilter = new ContextFilter();
    @NotNull private final List<TreeModelListener> myListenerList = new ArrayList<>();

    public ReportTreeModel(@NotNull Object root) {
      myRoot = root;
    }

    public void rebuild(@NotNull Renderable root, @NotNull Report report) {
      myRoot = root;
      myReport = report;
      myListenerList.forEach(l -> l.treeStructureChanged(new TreeModelEvent(root, new TreePath(root))));
      changeFilter();
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
        return getChildCount(element) == 0;
      }
      return true;
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
      return -1;
    }

    @Override
    public void addTreeModelListener(TreeModelListener treeModelListener) {
      myListenerList.add(treeModelListener);
    }

    @Override
    public void removeTreeModelListener(TreeModelListener treeModelListener) {
      myListenerList.remove(treeModelListener);
    }

    public boolean shallowTest(@NotNull BinaryObject obj) {
      if (obj instanceof ReportItem) {
        return myFilter.test(obj);
      }
      return true;
    }

    public void clearFilter() {
      mySeverityFilter = new SeverityFilter();
      myTagFilter = new TagFilter();
      myContextFilter = new ContextFilter();
      changeFilter();
    }

    public void setMinimumSeverity(@NotNull final LogProtos.Severity minimumSeverity) {
      // Change it now so model will display new items according to a new filter.
      mySeverityFilter = new SeverityFilter(minimumSeverity);
      changeFilter();
    }

    public void addTagToFilter(@NotNull MsgRef ref) {
      myTagFilter = new TagFilter(myTagFilter, ref);
      changeFilter();
    }

    public void clearTagsFromFilter() {
      myTagFilter = new TagFilter();
      changeFilter();
    }

    public void setContext(@NotNull Context context) {
      myContextFilter = new ContextFilter(context);
      changeFilter();
    }

    public void reload() {
      Enumeration<TreePath> treeState = myTree.getExpandedDescendants(new TreePath(myRoot));

      TreePath selectionPath = myTree.getSelectionPath();

      // Copy listeners since some of them can be removed while called.
      final List<TreeModelListener> listeners = new ArrayList<>(myListenerList);
      listeners.forEach(l -> l.treeStructureChanged(new TreeModelEvent(myRoot, new TreePath(myRoot))));

      if (treeState != null) {
        while (treeState.hasMoreElements()) {
          TreePath path = TreeUtil.getTreePathInTree(treeState.nextElement(), myTree);
          if (path != null) {
            myTree.expandPath(path);
          }
        }
      }

      if (selectionPath != null) {
        TreePath newPath = TreeUtil.getTreePathInTree(selectionPath, myTree);
        if (newPath != null) {
          updateSelection(newPath);
        }
      }
    }

    private void changeFilter() {
      if (myReport != null) {
        myFilter = myContextFilter;
        myFilter = myFilter.and(mySeverityFilter);
        myFilter = myFilter.and(myTagFilter);
        ((Renderable)getRoot()).resetFilter(myFilter);
        reload();
      }
    }
  }

  @NotNull private final ReportControls myControls;

  private ReportController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.LOADING_CAPTURE);
    myEditor.getReportStream().addListener(this);
    myEditor.getAtomStream().addListener(this);

    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));

    myControls = new ReportControls();
    JComboBox severityCombo = myControls.getSeverityCombo();
    Arrays.stream(LogProtos.Severity.values()).filter(s -> s != LogProtos.Severity.UNRECOGNIZED).map(LogProtos.Severity::name)
      .forEach(severityCombo::addItem);
    severityCombo.setSelectedIndex(severityCombo.getItemCount() - 1);
    severityCombo.addActionListener(
      actionEvent -> {
        LogProtos.Severity severity = LogProtos.Severity.valueOf((String)severityCombo.getSelectedItem());
        ((ReportTreeModel)getModel()).setMinimumSeverity(severity);
      });

    JBList list = myControls.getTagList();
    list.setEmptyText("No tags to filter by");
    JButton clearButton = myControls.getClearTagsButton();
    clearButton.addActionListener(event -> {
      ((ReportTreeModel)myTree.getModel()).clearTagsFromFilter();
      myControls.clearTags();
    });
    myPanel.add(myControls.getPanel(), BorderLayout.NORTH);

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
          case Render.TAG_ITEM_TAG:
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
          case Render.TAG_ITEM_TAG:
            onTagClick((Tag)renderable);
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

      private void onTagClick(@NotNull Tag tag) {
        ((ReportTreeModel)myTree.getModel()).addTagToFilter((MsgRef)tag.getDelegate());
        myControls.addTag(tag.getString());
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
        // May be disposed before Renderable structure is loaded
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
    return new ReportTreeModel(new Object());
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
    myControls.setVisible(true);
    myLoadingPanel.startLoading();
  }

  @Override
  public void onReportLoadingFailure(ReportStream reportStream, String errorMessage) {
    myControls.setVisible(false);
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
    final ReportTreeModel model = (ReportTreeModel)getModel();
    if (!model.shallowTest(reportItem)) {
      model.clearFilter();
      myControls.clearFilter();
    }
    Renderable root = (Renderable)myTree.getModel().getRoot();
    updateSelection(root, new TreePath(root), reportItem);
  }

  @Override
  public void onAtomLoadingStart(AtomStream atoms) {
  }

  @Override
  public void onAtomLoadingComplete(AtomStream atoms) {
  }

  @Override
  public void onAtomsSelected(AtomRangePath path, Object source) {
  }

  @Override
  public void onContextChanged(@NotNull Context context) {
    ((ReportTreeModel)getModel()).setContext(context);
  }

  private void updateSelection(Renderable root, TreePath path, final ReportItem reportItem) {
    TreePath treePath = root.pathTo(reportItem, path);
    if (treePath != null) {
      updateSelection(treePath);
    }
  }

  private void updateSelection(@NotNull final TreePath path) {
    if (path.getParentPath() != null) {
      myTree.expandPath(path.getParentPath());
    }
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
    ((ReportTreeModel)getModel()).rebuild(new ReportNode(report), report);
    if (report.getChildCount() == 0) {
      myControls.setVisible(false);
    }
  }

  private int getComponentTag(@NotNull TreePath treePath, int mouseX) {
    Object lastPathComponent = treePath.getLastPathComponent();
    if (!(lastPathComponent instanceof Renderable)) {
      return Render.NO_TAG;
    }
    Rectangle bounds = myTree.getPathBounds(treePath);
    assert bounds != null; // can't be null, as our path is valid
    CompositeCellRenderer renderer = (CompositeCellRenderer)myTree.getCellRenderer();
    if (renderer == null) {
      return Render.NO_TAG;
    }
    renderer.getTreeCellRendererComponent(myTree, lastPathComponent, myTree.isPathSelected(treePath), myTree.isExpanded(treePath),
                                          getModel().isLeaf(treePath.getLastPathComponent()), myTree.getRowForPath(treePath),
                                          myTree.hasFocus());
    return Render.getFieldIndex(renderer, mouseX - bounds.x);
  }
}