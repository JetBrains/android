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

import com.android.tools.analytics.UsageTracker;
import com.android.tools.idea.editors.gfxtrace.GfxTraceEditor;
import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.actions.EditAtomParametersAction;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.models.ReportStream;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.*;
import com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.atom.*;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.log.LogProtos;
import com.android.tools.idea.editors.gfxtrace.service.memory.MemoryProtos.PoolNames;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadableIcon;
import com.android.tools.idea.logcat.RegexFilterComponent;
import com.android.tools.rpclib.multiplex.Channel;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.android.utils.SparseArray;
import com.google.common.base.Objects;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent.EventKind;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.RowIcon;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.List;
import java.util.Map;
import java.util.RandomAccess;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class AtomController extends TreeController implements AtomStream.Listener, ReportStream.Listener {

  public static JComponent createUI(GfxTraceEditor editor) {
    return new AtomController(editor).myPanel;
  }

  @NotNull private static final Logger LOG = Logger.getInstance(AtomController.class);
  private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();

  public static abstract class Renderable {

    public final int myChildIndex;

    public Renderable(int childIndex) {
      myChildIndex = childIndex;
    }

    abstract void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes);
  }

  public static class Node extends Renderable {
    public static final int REPORT_ICON_WIDTH = AllIcons.General.Error.getIconWidth();
    public static final int REPORT_ICON_HEIGHT = AllIcons.General.Error.getIconHeight();
    public static final int REPORT_BALLOON_ANIMATION_CYCLES = 100;

    // TODO: Replace for instance method in (abstract?) superclass when implement this behaviour in Group
    public static int getBalloonX(int x) {
      return x + REPORT_ICON_WIDTH / 2;
    }

    public static int getBalloonY(int y) {
      return y + REPORT_ICON_HEIGHT;
    }

    public final long index;
    public final Atom atom;
    public int hoveredParameter = -1;

    // Follow paths index by atom.fieldIndex. Null means don't know if followable and empty path means it's not followable.
    private final Path[] followPaths;

    public Node(int childIndex, long index, Atom atom) {
      super(childIndex);
      this.index = index;
      this.atom = atom;
      this.followPaths = new Path[atom.getFieldCount()];
      // The extras are never followable.
      if (atom.getExtrasIndex() >= 0) {
        followPaths[atom.getExtrasIndex()] = Path.EMPTY;
      }
    }

    public Path getFollowPath(int parameter) {
      synchronized (followPaths) {
        return parameter >= 0 && parameter < followPaths.length && followPaths[parameter] != null ? followPaths[parameter] : Path.EMPTY;
      }
    }

    /**
     * @param x Relative x in terms of tree cell component
     * @param y Relative y in terms of tree cell component
     */
    public boolean isInsideReportIcon(ReportStream reportStream, @NotNull CompositeCellRenderer renderer, final int x, final int y) {
      return reportStream.hasReportItems(index) &&
             x - renderer.getRightComponentOffset() < REPORT_ICON_WIDTH &&
             y < REPORT_ICON_HEIGHT;
    }

    @NotNull
    public FieldPath getFieldPath(@NotNull AtomsPath atomsPath, int fieldIndex) {
      return atomsPath.index(index).field(atom.getFieldInfo(fieldIndex).getName());
    }

    /**
     * Determines whether the given parameter is followable. Will invoke onUpdate if it is and {@link #getFollowPath(int)} changes from
     * returning an empty path to returning a non-empty path.
     */
    public void computeFollowPath(@NotNull ServiceClient client,
                                  @NotNull AtomsPath atomsPath,
                                  final int parameter,
                                  final Runnable onUpdate) {
      synchronized (followPaths) {
        if (parameter >= 0 && parameter < followPaths.length && followPaths[parameter] == null) {
          followPaths[parameter] = Path.EMPTY;

          Path path = getFieldPath(atomsPath, parameter);
          Futures.addCallback(client.follow(path), new FutureCallback<Path>() {
            @Override
            public void onSuccess(Path result) {
              synchronized (followPaths) {
                followPaths[parameter] = result;
              }
              if (onUpdate != null) {
                onUpdate.run();
              }
            }

            @Override
            public void onFailure(Throwable t) {
              // TODO: we're working on figuring out how to better do this. For now, ignore all follow errors.
            }
          });
        }
      }
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      Render.render(this, (CompositeCellRenderer)component, attributes);
    }

    public Memory getChild(int childIndex) {
      Observations obs = atom.getObservations();
      if (childIndex < obs.getReads().length) {
        return new Memory(childIndex, this.index, obs.getReads()[childIndex], true);
      }
      else {
        return new Memory(childIndex, this.index, obs.getWrites()[childIndex - obs.getReads().length], false);
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Node node = (Node)o;
      if (index != node.index) return false;
      if (atom != null ? !atom.equals(node.atom) : node.atom != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = (int)(index ^ (index >>> 32));
      result = 31 * result + (atom != null ? atom.hashCode() : 0);
      return result;
    }

    @Override
    public String toString() {
      return "Node{atom=" + atom + ", index=" + index + '}';
    }
  }

  public static class Group extends Renderable {
    public static final int THUMBNAIL_SIZE = JBUI.scale(18);
    public static final int PREVIEW_SIZE = JBUI.scale(200);

    private static final RenderSettings THUMBNAIL_SETTINGS =
      new RenderSettings().setMaxWidth(PREVIEW_SIZE).setMaxHeight(PREVIEW_SIZE).setWireframeMode(WireframeMode.None);

    public final AtomGroup group;
    public final Atom lastLeaf;
    public final long indexOfLastLeaf;

    private ListenableFuture<BufferedImage> previewFuture;
    private LoadableIcon thumbnail, preview;
    private DevicePath lastDevicePath;

    private Context myContextUsed;
    private int myChildCount;
    private Lookup myGroupLookup;
    private Lookup myAtomLookup;
    private List<Reference<Renderable>> mySoftChildren;

    public Group(int childIndex, @NotNull AtomGroup group, @NotNull AtomList atoms) {
      super(childIndex);
      this.group = group;
      if (group.getRange().getCount() > 0) {
        this.lastLeaf = atoms.get(group.getRange().getLast());
        this.indexOfLastLeaf = group.getRange().getLast();
      }
      else { // root or empty node
        this.lastLeaf = null;
        this.indexOfLastLeaf = -1;
      }
    }

    public LoadableIcon getThumbnail(GfxTraceEditor gfxTraceEditor, @NotNull DevicePath devicePath, @NotNull AtomsPath atomsPath) {
      updateIcons(gfxTraceEditor, devicePath, atomsPath);
      return thumbnail;
    }

    public LoadableIcon getPreview(GfxTraceEditor gfxTraceEditor, @NotNull DevicePath devicePath, @NotNull AtomsPath atomsPath) {
      updateIcons(gfxTraceEditor, devicePath, atomsPath);
      return preview;
    }

    private void updateIcons(GfxTraceEditor editor, @NotNull DevicePath devicePath, @NotNull AtomsPath atomsPath) {
      if (previewFuture == null || !Objects.equal(lastDevicePath, devicePath)) {
        lastDevicePath = devicePath;
        ServiceClient client = editor.getClient();
        previewFuture = FetchedImage.loadLevel(FetchedImage.load(client, client.getFramebufferColor(
            devicePath, new AtomPath().setAtoms(atomsPath).setIndex(indexOfLastLeaf), THUMBNAIL_SETTINGS)), 0);
        thumbnail = new LoadableIcon(THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        preview = new LoadableIcon(PREVIEW_SIZE, PREVIEW_SIZE);

        Rpc.listen(previewFuture, new UiErrorCallback<BufferedImage, BufferedImage, Void>(editor, LOG) {
          @Override
          protected ResultOrError<BufferedImage, Void> onRpcThread(Rpc.Result<BufferedImage> result) throws Channel.NotConnectedException {
            try {
              return success(result.get());
            }
            catch (RpcException | ExecutionException e) {
              LOG.warn("Failed to load image", e);
              return error(null);
            }
          }

          @Override
          protected void onUiThreadSuccess(BufferedImage result) {
            thumbnail.withImage(result, false);
            preview.withImage(result, false);
          }

          @Override
          protected void onUiThreadError(Void error) {
            thumbnail.withImage(null, true);
            preview.withImage(null, true);
          }
        });
      }
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      Render.render(this, (CompositeCellRenderer)component, attributes);
    }

    public int getChildCount(Context context, AtomList atoms) {
      setupChildLookups(context, atoms);
      return myChildCount;
    }

    public Renderable getChild(int childIndex, Context context, AtomList atoms) {
      setupChildLookups(context, atoms);

      Reference<Renderable> ref = mySoftChildren.get(childIndex);
      if (ref != null) {
        Renderable child = ref.get();
        if (child != null) {
          return child;
        }
      }

      int groupIndex = (int)myGroupLookup.lookup(childIndex);
      Renderable child;
      if (groupIndex >= 0) {
        child = new Group(childIndex, this.group.getSubGroups()[groupIndex], atoms);
      }
      else {
        long atomIndex = myAtomLookup.lookup(childIndex);
        assert atomIndex >= 0;
        child = new Node(childIndex, atomIndex, atoms.get(atomIndex));
      }
      mySoftChildren.set(childIndex, new SoftReference<>(child));
      return child;
    }

    private void setupChildLookups(Context context, AtomList atoms) {
      if (context != myContextUsed) {
        Map<Range, Long> atomMap = new HashMap<>();
        Map<Range, Long> groupMap = new HashMap<>();
        List<Range> atomRanges = new ArrayList<>();
        List<Range> groupsRanges = new ArrayList<>();
        int childIndex = 0;
        long next = group.getRange().getStart();
        for (int groupIndex = 0; groupIndex < group.getSubGroups().length; groupIndex++) {
          AtomGroup subGroup = group.getSubGroups()[groupIndex];
          Range range = new Range().setStart(next).setEnd(subGroup.getRange().getStart());
          if (range.getCount() > 0) {
            List<Range> intersection = Range.intersection(context.getRanges(atoms), range);
            for (Range r : intersection) {
              long start = r.getStart();
              long size = r.getCount();
              r.setStart(childIndex).setEnd(childIndex + size);
              atomMap.put(r, start);
              childIndex += size;
              atomRanges.add(r);
            }
          }
          if (context.contains(subGroup.getRange())) {
            Range lastRange = groupsRanges.isEmpty() ? null : groupsRanges.get(groupsRanges.size() - 1);
            if (lastRange != null && lastRange.getEnd() == childIndex && (groupMap.get(lastRange) + lastRange.getCount()) == groupIndex) {
              long orgGroupIndex = groupMap.remove(lastRange);
              lastRange.setEnd(lastRange.getEnd() + 1); // will change the hash, as we remove then add
              groupMap.put(lastRange, orgGroupIndex);
            }
            else {
              Range newRange = new Range().setStart(childIndex).setEnd(childIndex + 1);
              groupMap.put(newRange, (long)groupIndex);
              groupsRanges.add(newRange);
            }
            childIndex++;
          }
          next = subGroup.getRange().getEnd();
        }
        Range range = new Range().setStart(next).setEnd(group.getRange().getEnd());
        if (range.getCount() > 0) {
          List<Range> intersection = Range.intersection(context.getRanges(atoms), range);
          for (Range r : intersection) {
            long start = r.getStart();
            long size = r.getCount();
            r.setStart(childIndex).setEnd(childIndex + size);
            atomMap.put(r, start);
            childIndex += size;
            atomRanges.add(r);
          }
        }

        myGroupLookup = new Lookup(groupMap, groupsRanges);
        myAtomLookup = new Lookup(atomMap, atomRanges);
        myChildCount = childIndex;
        myContextUsed = context;
        if (myChildCount < 100) {
          //noinspection unchecked
          mySoftChildren = Arrays.asList(new Reference[myChildCount]);
        }
        else {
          mySoftChildren = new SparseArrayList<>(100);
        }
      }
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Group group1 = (Group)o;
      if (indexOfLastLeaf != group1.indexOfLastLeaf) return false;
      if (group != null ? !group.equals(group1.group) : group1.group != null) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = group != null ? group.hashCode() : 0;
      result = 31 * result + (int)(indexOfLastLeaf ^ (indexOfLastLeaf >>> 32));
      return result;
    }

    @Override
    public String toString() {
      return "Group{group=" + group + ", indexOfLastLeaf=" + indexOfLastLeaf + '}';
    }
  }

  static class Lookup {

    private final Range[] myRanges;
    private final Map<Range, Long> myMap;

    public Lookup(Map<Range, Long> map, List<Range> list) {
      assert map.size() == list.size();
      myMap = map;
      myRanges = list.toArray(new Range[list.size()]);
    }

    public long lookup(int childIndex) {
      int result = Range.contains(myRanges, childIndex);
      if (result >= 0) {
        Range groupIndexRange = myRanges[result];
        assert childIndex >= groupIndexRange.getStart() && childIndex < groupIndexRange.getEnd();
        return myMap.get(groupIndexRange) + (childIndex - groupIndexRange.getStart());
      }
      return -1;
    }
  }

  static class SparseArrayList<T> extends AbstractList<T> {

    private final SparseArray<T> myData;

    public SparseArrayList(int initialCapacity) {
      myData = new SparseArray<>(initialCapacity);
    }

    @Override
    public T get(int index) {
      return myData.get(index);
    }

    @Override
    public T set(int index, T element) {
      myData.put(index, element);
      return null;
    }

    @Override
    public int size() {
      throw new UnsupportedOperationException();
    }
  }

  public static class Memory extends Renderable {

    public final long index;
    public final Observation observation;
    public final boolean isRead;

    public Memory(int childIndex, long index, Observation observation, boolean isRead) {
      super(childIndex);
      this.index = index;
      this.observation = observation;
      this.isRead = isRead;
    }

    @Override
    public void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes) {
      Render.render(this, component, attributes);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Memory memory = (Memory)o;
      if (index != memory.index) return false;
      if (isRead != memory.isRead) return false;
      if (!observation.equals(memory.observation)) return false;
      return true;
    }

    @Override
    public int hashCode() {
      int result = (int)(index ^ (index >>> 32));
      result = 31 * result + observation.hashCode();
      result = 31 * result + (isRead ? 1 : 0);
      return result;
    }

    @Override
    public String toString() {
      return "Memory{index=" + index + ", isRead=" + isRead + ", observation=" + observation + '}';
    }
  }

  @NotNull private RegexFilterComponent mySearchField = new RegexFilterComponent(AtomController.class.getName(), 10);
  @NotNull private Map<ContextID, Hierarchy> mySelectedHierarchies = Maps.newHashMap();
  @NotNull private Context mySelectedContext = Context.ALL;

  private AtomController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.LOADING_CAPTURE);
    myEditor.getAtomStream().addListener(this);
    // Listen to ReportStream updates in order to show report information in atom tree
    myEditor.getReportStream().addListener(this);

    myPanel.add(mySearchField, BorderLayout.NORTH);
    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
    myTree.setLargeModel(true); // Set some performance optimizations for large models.
    myTree.addTreeSelectionListener(treeSelectionEvent -> {
      if (treeSelectionEvent.isAddedPath()) {
        AtomStream atoms = myEditor.getAtomStream();
        Renderable object = (Renderable)myTree.getLastSelectedPathComponent();

        UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                       .setCategory(EventCategory.GPU_PROFILER)
                                       .setKind(EventKind.GFX_TRACE_COMMAND_SELECTED)
                                       .setGfxTracingDetails(AndroidStudioStats.GfxTracingDetails.newBuilder()
                                                             .setCommand(object.getClass().getSimpleName())));

        if (object instanceof Group) {
          atoms.selectAtoms(((Group)object).group.getRange(), AtomController.this);
        }
        else if (object instanceof Node) {
          atoms.selectAtoms(((Node)object).index, 1, AtomController.this);
        }
        else if (object instanceof Memory) {
          Memory memory = (Memory)object;
          myEditor.activatePath(
            atoms.getPath().index(memory.index).memoryAfter(PoolNames.Application_VALUE, memory.observation.getRange()), AtomController.this);
        }
      }
    });
    mySearchField.getTextEditor().addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent evt) {
        if (evt.getKeyCode() == KeyEvent.VK_ENTER) {
          findNextNode(mySearchField.getPattern());
          // Move the focus back to the search box.
          ApplicationManager.getApplication().invokeLater(() -> mySearchField.getTextEditor().requestFocus());
        }
      }
    });
    MouseAdapter mouseHandler = new MouseAdapter() {
      private static final int PREVIEW_HOVER_DELAY_MS = 500;
      private final ScheduledExecutorService scheduler = ConcurrencyUtil.newSingleScheduledThreadExecutor("PreviewHover");
      private Group lastHoverGroup;
      private Node lastHoverNode;
      private Future<?> lastScheduledFuture = Futures.immediateFuture(null);
      private Balloon lastShownBalloon;
      private JPopupMenu popupMenu = new JPopupMenu();

      @Override
      public void mouseEntered(MouseEvent event) {
        updateHovering(event.getX(), event.getY());
      }

      @Override
      public void mouseExited(MouseEvent event) {
        clearHovering();
      }

      @Override
      public void mouseMoved(MouseEvent event) {
        updateHovering(event.getX(), event.getY());
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (e.isPopupTrigger()) {
          TreePath path = myTree.getPathForLocation(e.getX(), e.getY());
          if (path != null) {
            Object treeNode = path.getLastPathComponent();
            if (treeNode instanceof Node) {
              EditAtomParametersAction editAction = EditAtomParametersAction.getEditActionFor((Node)treeNode, myEditor);
              if (editAction != null) {
                popupMenu.removeAll();
                popupMenu.add(editAction);
                popupMenu.show(e.getComponent(), e.getX(), e.getY());
              }
            }
          }
        }
      }

      @Override
      public void mouseWheelMoved(MouseWheelEvent event) {
        clearHovering();

        // Bubble the event.
        JScrollPane ancestor = (JBScrollPane)SwingUtilities.getAncestorOfClass(JBScrollPane.class, myTree);
        if (ancestor != null) {
          MouseWheelEvent converted = (MouseWheelEvent)SwingUtilities.convertMouseEvent(myTree, event, ancestor);
          for (MouseWheelListener listener : ancestor.getMouseWheelListeners()) {
            listener.mouseWheelMoved(converted);
          }
        }

        // Update the hover position after the scroll.
        Point location = new Point(MouseInfo.getPointerInfo().getLocation());
        SwingUtilities.convertPointFromScreen(location, myTree);
        updateHovering(location.x, location.y);
      }

      private void updateHovering(int mouseX, int mouseY) {
        TreePath path = myTree.getClosestPathForLocation(mouseX, mouseY);
        if (path != null) {
          Rectangle bounds = myTree.getPathBounds(path);
          if (bounds != null) {
            int x = mouseX - bounds.x, y = mouseY - bounds.y;
            if (x >= 0 && x < bounds.width && y >= 0 && y < bounds.height) {
              Object obj = path.getLastPathComponent();
              updateHovering((Renderable)obj, path, bounds, x, y);
              return;
            }
          }
        }
        clearHovering();
      }

      private void updateHovering(@NotNull Renderable node, @NotNull TreePath treePath, @NotNull Rectangle bounds, int x, int y) {
        hoverHand(myTree, myEditor.getAtomStream().getPath(), null);

        // Check if hovering the preview icon.
        if (node instanceof Group && shouldShowPreview((Group)node) && x < Group.THUMBNAIL_SIZE && y < Group.THUMBNAIL_SIZE) {
          setHoveringGroup((Group)node, bounds.x + Group.THUMBNAIL_SIZE, bounds.y + Group.THUMBNAIL_SIZE / 2);
          setHoveringNode(null, 0);
        }
        else {
          setHoveringGroup(null, 0, 0);

          CompositeCellRenderer renderer = (CompositeCellRenderer)myTree.getCellRenderer();
          renderer.setup(myTree, treePath);
          // Check if hovering an atom parameter.
          if (node instanceof Node) {
            Node atomNode = (Node)node;
            int index = Render.getFieldIndex(renderer, x);
            if (index >= 0) {
              setHoveringNode(atomNode, index);
            }
            else if (atomNode.isInsideReportIcon(myEditor.getReportStream(), renderer, x, y)) {
              setHoveringNode(atomNode, myEditor.getReportStream().getReportItemPath(atomNode.index), renderer, bounds);
            }
            else {
              setHoveringNode(null, 0);
            }
          }
        }
      }

      private void clearHovering() {
        setHoveringGroup(null, 0, 0);
        setHoveringNode(null, 0);
      }

      private synchronized void setHoveringGroup(@Nullable final Group group, final int x, final int y) {
        if (group != lastHoverGroup) {
          lastScheduledFuture.cancel(true);
          lastHoverGroup = group;
          if (group != null) {
            lastScheduledFuture = scheduler.schedule(new Runnable() {
              @Override
              public void run() {
                hover(group, x, y);
              }
            }, PREVIEW_HOVER_DELAY_MS, TimeUnit.MILLISECONDS);
          }
        }
        if (group == null && lastShownBalloon != null) {
          lastShownBalloon.hide();
          lastShownBalloon = null;
        }
      }

      private void onNewHoverNode(@Nullable Node node, boolean cancelPopup) {
        if (lastHoverNode != null) {
          // Clear hovered parameter for another node
          lastHoverNode.hoveredParameter = -1;
        }
        lastScheduledFuture.cancel(cancelPopup);
        lastHoverNode = node;
      }

      /**
       * Sets hovering when of x and y inside report icon
       */
      private void setHoveringNode(@Nullable Node node, @NotNull ReportItemPath followPath,
                                   @NotNull final CompositeCellRenderer renderer, @NotNull Rectangle bounds) {
        final int x = Node.getBalloonX(bounds.x);
        final int y = Node.getBalloonY(bounds.y);
        // Check if we've met this node
        if (node != lastHoverNode) {
          onNewHoverNode(node, true);
          if (node != null) {
            lastScheduledFuture = scheduler.schedule(() -> hover(node, x + renderer.getRightComponentOffset(), y),
                                                     PREVIEW_HOVER_DELAY_MS, TimeUnit.MILLISECONDS);
          }
        }
        if (node == null && lastShownBalloon != null) {
          lastShownBalloon.hide();
          lastShownBalloon = null;
        }

        hoverHand(myTree, myEditor.getAtomStream().getPath(), followPath);
      }

      /**
       * Sets hovering when cursor points to atom field
       */
      private void setHoveringNode(@Nullable Node node, int index) {
        PathStore<Path> followPathStore = new PathStore<>();
        // Check if we've met this node
        if (node != lastHoverNode) {
          onNewHoverNode(node, false);
        }

        if (node != null) {
          onAtomFieldHover(node, index, followPathStore);
        }

        hoverHand(myTree, myEditor.getAtomStream().getPath(), followPathStore.getPath());
        myTree.repaint();
      }

      private void onAtomFieldHover(@NotNull final Node node, int index, final PathStore<Path> pathStore) {
        final Path followPath = node.getFollowPath(index);
        if (followPath == Path.EMPTY) {
          node.hoveredParameter = -1;
          node.computeFollowPath(myEditor.getClient(), myEditor.getAtomStream().getPath(), index, () -> {
            myTree.repaint();
            final Path freshFollowPath = node.getFollowPath(index);
            if (freshFollowPath != null && freshFollowPath != Path.EMPTY) {
              pathStore.update(freshFollowPath);
              node.hoveredParameter = index;
            }
          });
        }
        else {
          pathStore.update(followPath);
          node.hoveredParameter = index;
        }
      }

      private void hover(final Group group, final int x, final int y) {
        final Object lock = this;
        ApplicationManager.getApplication().invokeLater(new Runnable() {
          @Override
          public void run() {
            synchronized (lock) {
              if (group == lastHoverGroup) {
                if (lastShownBalloon != null) {
                  lastShownBalloon.hide();
                }
                DevicePath device = myRenderDevice.getPath();
                AtomsPath atoms = myEditor.getAtomStream().getPath();
                if (device != null && atoms != null) {
                  lastShownBalloon = JBPopupFactory.getInstance().createBalloonBuilder(
                        group.getPreview(myEditor, device, atoms))
                    .setAnimationCycle(100)
                    .createBalloon();
                  Disposer.register(AtomController.this, lastShownBalloon);
                  lastShownBalloon.show(new RelativePoint(myTree, new Point(x, y)), Balloon.Position.atRight);
                }
              }
            }
          }
        });
      }

      private void hover(final Node node, final int x, final int y) {
        ApplicationManager.getApplication().invokeLater(() -> {
          if (node == lastHoverNode) {
            if (lastShownBalloon != null) {
              lastShownBalloon.hide();
            }
            JTextArea component = new JTextArea();
            component.setOpaque(false);

            Report report = myEditor.getReportStream().getReport();
            if (report == null) {
              return;
            }
            com.google.common.collect.Range<Integer> itemIndices = report.getForAtom(node.index);
            if (itemIndices != null) {
              component.append(ContiguousSet.create(itemIndices, DiscreteDomain.integers()).stream()
                                 .map(report::constructMessage).collect(Collectors.joining("\n")));
            }
            lastShownBalloon = JBPopupFactory.getInstance().createBalloonBuilder(component)
              .setAnimationCycle(Node.REPORT_BALLOON_ANIMATION_CYCLES).createBalloon();
            lastShownBalloon.show(new RelativePoint(myTree, new Point(x, y)), Balloon.Position.below);
          }
        });
      }

      @Override
      public void mouseClicked(MouseEvent event) {
        Object object = getDataObjectAt(myTree.getPathForLocation(event.getX(), event.getY()));
        if (object instanceof Node) {
          Node node = (Node)object;
          // The user was hovering over a parameter, fire off the path activation event on click.
          Path path = null;
          if (node.hoveredParameter >= 0) {
            path = node.getFollowPath(node.hoveredParameter);
          }
          else {
            TreePath treePath = myTree.getClosestPathForLocation(event.getX(), event.getY());
            if (treePath != null) {
              Rectangle bounds = myTree.getPathBounds(treePath);
              assert bounds != null;
              CompositeCellRenderer renderer = (CompositeCellRenderer)myTree.getCellRenderer();
              assert renderer != null;
              renderer.setup(myTree, treePath);
              if (node.isInsideReportIcon(myEditor.getReportStream(),
                                          renderer,
                                          event.getX() - bounds.x,
                                          event.getY() - bounds.y)) {
                path = myEditor.getReportStream().getReportItemPath(node.index);
              }
            }
          }

          if (path != null) {
            UsageTracker.getInstance().log(AndroidStudioEvent.newBuilder()
                                           .setCategory(EventCategory.GPU_PROFILER)
                                           .setKind(EventKind.GFX_TRACE_LINK_CLICKED)
                                           .setGfxTracingDetails(AndroidStudioStats.GfxTracingDetails.newBuilder()
                                                                .setTracePath(path.toString())));

            myEditor.activatePath(path, AtomController.this);
          }
        }
      }
    };
    myTree.addMouseListener(mouseHandler);
    myTree.addMouseMotionListener(mouseHandler);
    myTree.addMouseWheelListener(mouseHandler);
  }

  @NotNull
  @Override
  protected TreeCellRenderer createRenderer() {
    return new CompositeCellRenderer() {
      @Override
      public void customizeCellRenderer(@NotNull final JTree tree,
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
          renderable.render(this, SimpleTextAttributes.REGULAR_ATTRIBUTES);
        }
        LoadableIcon loadableIcon = customizeFramePreviewRendering(
          tree, value, myRenderDevice.getPath(), myEditor.getAtomStream().getPath());
        Icon reportIcon = customizeReportInfoRendering(value);
        if (loadableIcon != null) {
          setIcon(loadableIcon);
        }
        if (reportIcon != null) {
          myRightComponent.setIcon(reportIcon);
        }
        myRightComponentOffset = getPreferredSize().width;
        myRightComponentShow = true;
      }

      /**
       * Decides if we need to display LoadableIcon and sets it up in case we need
       */
      private LoadableIcon customizeFramePreviewRendering(
        @NotNull JTree tree, @NotNull Object userObject, DevicePath device, AtomsPath atoms) {
        if (userObject instanceof Group && device != null && atoms != null) {
          Group group = (Group)userObject;
          if (shouldShowPreview(group)) {
            return group.getThumbnail(myEditor, device, atoms).withRepaintComponent(tree);
          }
        }
        return null;
      }

      /**
       * Decides if we need to display report info icon and which icon to display
       */
      private Icon customizeReportInfoRendering(@NotNull Object userObject) {
        ReportStream reportStream = myEditor.getReportStream();
        // Check whether there's report to show
        if (reportStream.getReport() != null) {
          com.google.common.collect.Range<Integer> reportItemIndices = null;
          if (userObject instanceof Node) {
            Node atomNode = (Node)userObject;
            reportItemIndices = reportStream.getReport().getForAtom(atomNode.index);
          }
          else if (userObject instanceof Group) {
            Group atomsGroup = (Group)userObject;
            Range groupRange = atomsGroup.group.getRange();
            reportItemIndices = reportStream.getReport()
              .getForAtoms(groupRange.getStart(), groupRange.getLast());
          }
          // If there's at least one report item associated - just return an icon for the most severe
          if (reportItemIndices != null && !reportItemIndices.isEmpty()) {
            return getReportIcon(reportStream.maxSeverity(reportItemIndices));
          }
        }
        return null;
      }

      @NotNull
      private Icon getReportIcon(@NotNull LogProtos.Severity severity) {
        switch (severity) {
          case Emergency:
          case Alert:
          case Critical:
          case Error:
            return AllIcons.General.Error;
          case Warning:
            return AllIcons.General.Warning;
          case Notice:
          case Info:
          case Debug:
            return AllIcons.General.Information;
        }
        return null;
      }
    };
  }

  @Override
  @NotNull
  protected TreeModel createEmptyModel() {
    return new AtomTreeModel(new AtomGroup().setRange(new Range()).setSubGroups(new AtomGroup[0]), new AtomList(), Context.ALL);
  }

  @NotNull
  @Override
  public String[] getColumns(TreePath path) {
    Object object = getDataObjectAt(path);
    if (object instanceof Group) {
      AtomGroup group = ((Group)object).group;
      Range range = group.getRange();
      return new String[] {
        group.getName(),
        "(" + range.getStart() + " - " + range.getLast() + ")",
      };
    }
    if (object instanceof Node) {
      Node node = (Node)object;
      SimpleColoredComponent component = new SimpleColoredComponent();
      Render.render(node.atom, component, node.hoveredParameter);
      return new String[]{ node.index + ":", component.toString() };
    }
    return new String[]{ object.toString() };
  }

  /**
   * @return the data object (usually a {@link Node}, {@link Group} or {@link Memory} for the
   * object at the specified path.
   */
  @Nullable
  @Contract("null -> null;!null -> !null")
  private Renderable getDataObjectAt(@Nullable TreePath path) {
    if (path == null) {
      return null;
    }
    return (Renderable)path.getLastPathComponent();
  }

  private void findNextNode(Pattern pattern) {
    AtomTreeModel model = (AtomTreeModel)getModel();
    TreePath next = model.findNextNode(myTree.getSelectionPath(), pattern);
    if (next != null) {
      updateSelection(next, true);
    }
  }

  private static boolean shouldShowPreview(Group group) {
    return group.lastLeaf.isEndOfFrame() || group.lastLeaf.isDrawCall();
  }

  private void updateTree(AtomStream atoms) {
    Hierarchy hierarchy = mySelectedHierarchies.get(mySelectedContext);
    if (hierarchy == null) {
      // No hierarchy selection made for this context yet, select the first one.
      hierarchy = atoms.getHierarchies().firstWithContext(mySelectedContext.getID());
      mySelectedHierarchies.put(mySelectedContext.getID(), hierarchy);
    }
    Enumeration<TreePath> treeState = myTree.getExpandedDescendants(new TreePath(getModel().getRoot()));
    Context context = atoms.getContexts().count() > 1 ? mySelectedContext : Context.ALL;
    assert hierarchy != null;

    myTree.setModel(new AtomTreeModel(hierarchy.getRoot(), atoms.getAtoms(), context));

    if (treeState != null) {
      while (treeState.hasMoreElements()) {
        myTree.expandPath(getTreePathInTree(treeState.nextElement(), myTree));
      }
    }
  }

  static class AtomTreeModel implements TreeModel {

    @NotNull private final AtomList myAtoms;
    @NotNull private final Context myContext;
    @NotNull private final Group myRoot;

    public AtomTreeModel(@NotNull AtomGroup root, @NotNull AtomList atoms, @NotNull Context context) {
      myAtoms = atoms;
      myContext = context;
      myRoot = new Group(-1, root, atoms);
    }

    @Override
    @NotNull
    public Group getRoot() {
      return myRoot;
    }

    @Override
    public Renderable getChild(Object element, int index) {
      if (element instanceof Group) {
        Group group = (Group)element;
        return group.getChild(index, myContext, myAtoms);
      }
      if (element instanceof Node) {
        Node node = (Node)element;
        return node.getChild(index);
      }
      return null;
    }

    @Override
    public int getChildCount(Object element) {
      if (element instanceof Group) {
        Group group = (Group)element;
        return group.getChildCount(myContext, myAtoms);
      }
      if (element instanceof Node) {
        Node node = (Node)element;
        return node.atom.getObservationCount();
      }
      return 0;
    }

    @Override
    public boolean isLeaf(Object node) {
      if (node instanceof Group) {
        return false;
      }
      if (node instanceof Node) {
        return ((Node)node).atom.getObservationCount() == 0;
      }
      return true;
    }

    @Override
    public int getIndexOfChild(Object parent, Object child) {
      return ((Renderable)child).myChildIndex;
    }

    public TreePath findNextNode(TreePath path, Pattern pattern) {
      long start = 0;
      if (path != null) {
        Object node = path.getLastPathComponent();
        if (node instanceof Group) {
          start = ((Group)node).group.getRange().getStart();
        }
        else if (node instanceof Node) {
          start = ((Node)node).index + 1;
        }
        else if (node instanceof Memory) {
          start = ((Memory)node).index + 1;
        }
      }
      long next = search(start, myAtoms.getAtoms().length, pattern);
      if (next < 0) {
        next = search(0, start, pattern);
      }
      return (next < 0) ? null : getTreePathTo(new Range().setStart(next).setEnd(next + 1));
    }

    private long search(long start, long end, Pattern pattern) {
      for (long index = start; index < end; index++) {
        if (myContext.contains(index) && pattern.matcher(myAtoms.get(index).getName()).find()) {
          return index;
        }
      }
      return -1;
    }

    public TreePath getTreePathTo(Range range) {
      return getTreePathTo(myRoot, new TreePath(myRoot), range);
    }

    private TreePath getTreePathTo(Renderable node, TreePath path, Range range) {
      assert !isLeaf(node);

      int found = Collections.binarySearch(new ChildList(node), null, (child, ignored) -> {
        if (child instanceof Group) {
          Range childRange = ((Group)child).group.getRange();
          if (childRange.contains(range.getLast())) return 0;
          if (range.getLast() > childRange.getLast()) return -1;
          return 1;
        }
        if (child instanceof Node) {
          return Long.compare(((Node)child).index, range.getLast());
        }
        throw new IllegalStateException();
      });

      if (found >= 0) {
        Renderable object = (Renderable)getChild(node, found);
        if (object instanceof Node) {
          assert range.getLast() == (((Node)object).index);
          return path.pathByAddingChild(object);
        }
        else if (object instanceof Group) {
          Range groupRange = ((Group)object).group.getRange();
          if (groupRange.equals(range)) {
            return path.pathByAddingChild(object);
          }
          else {
            assert groupRange.contains(range.getLast());
            return getTreePathTo(object, path.pathByAddingChild(object), range);
          }
        }
        throw new IllegalStateException("what have we found " + object);
      }
      return null;
    }

    class ChildList extends AbstractList<Renderable> implements RandomAccess {
      private Renderable myNode;

      ChildList (Renderable node) {
        myNode = node;
      }

      @Override
      public Renderable get(int index) {
        return getChild(myNode, index);
      }

      @Override
      public int size() {
        return getChildCount(myNode);
      }
    }

    @Override
    public void valueForPathChanged(TreePath path, Object newValue) {
      throw new UnsupportedOperationException(getClass().getName() + " does not support editing");
    }

    @Override
    public void addTreeModelListener(TreeModelListener l) {
      // We don't fire any events, so no need to track the listeners.
    }

    @Override
    public void removeTreeModelListener(TreeModelListener l) {
      // We don't fire any events, so no need to track the listeners.
    }
  }

  private void selectContext(@NotNull ContextID id) {
    AtomStream atoms = myEditor.getAtomStream();
    Context context = atoms.getContexts().find(id, Context.ALL);
    if (!context.equals(mySelectedContext)) {
      mySelectedContext = context;

      AtomTreeModel model = (AtomTreeModel)getModel();
      if (model.getChildCount(model.getRoot()) == 0) {
        // if onAtomLoadingComplete has not happened yet, we dont want to load anything.
        return;
      }

      // we are switching context, we don't want to try and preserve the selected (it would cause loads of selection changing events)
      myTree.setSelectionPath(null);
      updateTree(atoms);
    }
  }

  @Override
  public void notifyPath(PathEvent event) {
    ContextPath contextPath = event.findContextPath();
    if (contextPath != null) {
      selectContext(contextPath.getID());
    }
    if (myRenderDevice.updateIfNotNull(event.findDevicePath())) {
      // Only the icons would need to be changed.
      myTree.repaint();
    }
  }

  @Override
  public void onAtomLoadingStart(AtomStream atoms) {
    myTree.getEmptyText().setText("");
    myLoadingPanel.startLoading();
  }

  @Override
  public void onAtomLoadingComplete(AtomStream atoms) {
    if (atoms.isLoaded()) {
      myLoadingPanel.stopLoading();
      // Map all hierarchy selections into something equivalent.
      Maps.transformValues(mySelectedHierarchies,
                           hierarchy -> atoms.getHierarchies().findSimilar(hierarchy));
      updateTree(atoms);
    } else {
      myLoadingPanel.showLoadingError("Failed to load GPU commands");
    }
  }


  @Override
  public void onReportLoadingStart(ReportStream reportStream) {
  }

  @Override
  public void onReportLoadingFailure(ReportStream reportStream, String errorMessage) {
  }

  @Override
  public void onReportLoadingSuccess(ReportStream reportStream) {
    myTree.repaint();
  }

  @Override
  public void onReportItemSelected(ReportItem reportItem) {
  }

  @Nullable("if this path can not be found in this tree")
  public static TreePath getTreePathInTree(TreePath treePath, JTree tree) {
    Object root = tree.getModel().getRoot();
    Object[] path = treePath.getPath();
    List<Object> newPath = new ArrayList<Object>();
    Object found = null;
    for (Object node : path) {
      if (found == null) {
        if (Objects.equal(root, node)) {
          found = root;
        }
        else {
          return null;
        }
      }
      else {
        Object foundChild = null;
        for (int i = 0; i < tree.getModel().getChildCount(found); i++) {
          Object child = tree.getModel().getChild(found, i);
          if (Objects.equal(node, child)) {
            foundChild = child;
            break;
          }
        }
        if (foundChild == null) {
          return null;
        }
        found = foundChild;
      }
      newPath.add(found);
    }
    return new TreePath(newPath.toArray());
  }

  @Override
  public void onAtomsSelected(AtomRangePath path, Object source) {
    // we NEED this check here as if the user selects a memory observation (a child of a Atom)
    // we want other controllers to update to the new Atom position, but we dont want to move
    // our own selection to the Atom, we want to keep it on the child.
    if (source != this) {
      TreePath treePath = ((AtomTreeModel)getModel()).getTreePathTo(path.getRange());
      if (treePath != null) {
        updateSelection(treePath, false);
      }
    }
  }

  /**
   * Selects the given path and makes sure it's visible. This does not fire an event, but simply updates the UI.
   */
  private void updateSelection(TreePath path, boolean fireEvent) {
    myTree.setSelectionPath(path, fireEvent);

    // Only scroll vertically. JTree's scrollPathToVisible also scrolls horizontally, which is annoying.
    Rectangle bounds = myTree.getPathBounds(path);
    if (bounds != null) {
      bounds.width += bounds.x;
      bounds.x = 0;
      myTree.scrollRectToVisible(bounds);
    }
  }
}
