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
import com.android.tools.idea.editors.gfxtrace.UiCallback;
import com.android.tools.idea.editors.gfxtrace.UiErrorCallback;
import com.android.tools.idea.editors.gfxtrace.actions.EditAtomParametersAction;
import com.android.tools.idea.editors.gfxtrace.models.AtomStream;
import com.android.tools.idea.editors.gfxtrace.renderers.Render;
import com.android.tools.idea.editors.gfxtrace.service.*;
import com.android.tools.idea.editors.gfxtrace.service.ServiceProtos.WireframeMode;
import com.android.tools.idea.editors.gfxtrace.service.atom.Atom;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomGroup;
import com.android.tools.idea.editors.gfxtrace.service.atom.AtomList;
import com.android.tools.idea.editors.gfxtrace.service.atom.Observation;
import com.android.tools.idea.editors.gfxtrace.service.atom.Observations;
import com.android.tools.idea.editors.gfxtrace.service.atom.Range;
import com.android.tools.idea.editors.gfxtrace.service.image.FetchedImage;
import com.android.tools.idea.editors.gfxtrace.service.memory.MemoryProtos.PoolNames;
import com.android.tools.idea.editors.gfxtrace.service.path.*;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadableIcon;
import com.android.tools.idea.logcat.RegexFilterComponent;
import com.android.tools.rpclib.rpccore.Rpc;
import com.android.tools.rpclib.rpccore.RpcException;
import com.google.common.base.Objects;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.wireless.android.sdk.stats.AndroidStudioStats;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent.EventCategory;
import com.google.wireless.android.sdk.stats.AndroidStudioStats.AndroidStudioEvent.EventKind;
import com.intellij.ide.util.treeView.AbstractTreeBuilder;
import com.intellij.ide.util.treeView.AbstractTreeStructure;
import com.intellij.ide.util.treeView.NodeDescriptor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.ui.JBUI;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public class AtomController extends TreeController implements AtomStream.Listener {
  public static JComponent createUI(GfxTraceEditor editor) {
    return new AtomController(editor).myPanel;
  }

  @NotNull private static final Logger LOG = Logger.getInstance(GfxTraceEditor.class);
  private final PathStore<DevicePath> myRenderDevice = new PathStore<DevicePath>();

  public static abstract class Renderable extends NodeDescriptor {
    public Renderable(@Nullable Renderable parent) {
      super(null, parent);
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

    abstract void render(@NotNull SimpleColoredComponent component, @NotNull SimpleTextAttributes attributes);
  }

  public static class Node extends Renderable {
    public final long index;
    public final Atom atom;
    public int hoveredParameter = -1;

    // Follow paths index by atom.fieldIndex. Null means don't know if followable and empty path means it's not followable.
    private final Path[] followPaths;

    public Node(@NotNull Renderable parent, long index, Atom atom) {
      super(parent);
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
      Render.render(this, component, attributes);
    }

    @NotNull
    public List<Memory> getChildren() {
      Observations observations = atom.getObservations();
      if (observations != null && (observations.getReads().length > 0 || observations.getWrites().length > 0)) {
        List<Memory> children = new ArrayList<>();
        for (Observation read : observations.getReads()) {
          children.add(new Memory(this, index, read, true));
        }
        for (Observation write : observations.getWrites()) {
          children.add(new Memory(this, index, write, false));
        }
        return children;
      }
      return Collections.emptyList();
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

    public Group(@Nullable Renderable parent, @NotNull AtomGroup group, @NotNull AtomList atoms) {
      super(parent);
      this.group = group;
      this.lastLeaf = atoms.get(group.getRange().getLast());
      this.indexOfLastLeaf = group.getRange().getLast();
    }

    public LoadableIcon getThumbnail(ServiceClient client, @NotNull DevicePath devicePath, @NotNull AtomsPath atomsPath) {
      updateIcons(client, devicePath, atomsPath);
      return thumbnail;
    }

    public LoadableIcon getPreview(ServiceClient client, @NotNull DevicePath devicePath, @NotNull AtomsPath atomsPath) {
      updateIcons(client, devicePath, atomsPath);
      return preview;
    }

    private void updateIcons(ServiceClient client, @NotNull DevicePath devicePath, @NotNull AtomsPath atomsPath) {
      if (previewFuture == null || !Objects.equal(lastDevicePath, devicePath)) {
        lastDevicePath = devicePath;
        previewFuture = FetchedImage.loadLevel(FetchedImage.load(client, client.getFramebufferColor(
            devicePath, new AtomPath().setAtoms(atomsPath).setIndex(indexOfLastLeaf), THUMBNAIL_SETTINGS)), 0);
        thumbnail = new LoadableIcon(THUMBNAIL_SIZE, THUMBNAIL_SIZE);
        preview = new LoadableIcon(PREVIEW_SIZE, PREVIEW_SIZE);

        Rpc.listen(previewFuture, LOG, new UiErrorCallback<BufferedImage, BufferedImage, Void>() {
          @Override
          protected ResultOrError<BufferedImage, Void> onRpcThread(Rpc.Result<BufferedImage> result) {
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
      Render.render(this, component, attributes);
    }

    @NotNull
    public List<Renderable> getChildren(@NotNull AtomList atoms, @NotNull Context context) {
      List<Renderable> children = new ArrayList<>();
      long next = group.getRange().getStart();
      for (AtomGroup subGroup : group.getSubGroups()) {
        // add any atoms that come before the group
        addAtoms(children, atoms, context, next, subGroup.getRange().getStart());
        // and add the group itself
        if (context.contains(subGroup.getRange())) {
          children.add(new Group(this, subGroup, atoms));
        }
        next = subGroup.getRange().getEnd();
      }
      // Add all the trailing atoms
      addAtoms(children, atoms, context, next, group.getRange().getEnd());
      return children;
    }

    private void addAtoms(@NotNull List<? super Node> list, @NotNull AtomList atomsList, @NotNull Context context, long start, long end) {
      for (long index = start; index < end; ++index) {
        if (context.contains(index)) {
          list.add(new Node(this, index, atomsList.get(index)));
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

  public static class Memory extends Renderable {
    public final long index;
    public final Observation observation;
    public final boolean isRead;

    public Memory(@NotNull Renderable parent, long index, @NotNull Observation observation, boolean isRead) {
      super(parent);
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
  @NotNull private final AbstractTreeBuilder myTreeBuilder;

  private AtomController(@NotNull GfxTraceEditor editor) {
    super(editor, GfxTraceEditor.LOADING_CAPTURE);
    myEditor.getAtomStream().addListener(this);

    myPanel.add(mySearchField, BorderLayout.NORTH);
    myScrollPane.setBorder(new EmptyBorder(0, 0, 0, 0));
    myTree.setLargeModel(true); // Set some performance optimizations for large models.
    myTree.addTreeSelectionListener(treeSelectionEvent -> {
      if (treeSelectionEvent.isAddedPath()) {
        AtomStream atoms = myEditor.getAtomStream();
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)myTree.getLastSelectedPathComponent();
        if (node == null || node.getUserObject() == null) return;
        Object object = node.getUserObject();

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
            DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)path.getLastPathComponent();
            Object userObject = treeNode.getUserObject();
            if (userObject instanceof Node) {
              EditAtomParametersAction editAction = EditAtomParametersAction.getEditActionFor((Node)userObject, myEditor);
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
              updateHovering((DefaultMutableTreeNode)path.getLastPathComponent(), bounds, x, y);
              return;
            }
          }
        }
        clearHovering();
      }

      private void updateHovering(@NotNull DefaultMutableTreeNode node, @NotNull Rectangle bounds, int x, int y) {
        Object userObject = node.getUserObject();
        hoverHand(myTree, myEditor.getAtomStream().getPath(), null);

        // Check if hovering the preview icon.
        if (userObject instanceof Group && shouldShowPreview((Group)userObject) && x < Group.THUMBNAIL_SIZE && y < Group.THUMBNAIL_SIZE) {
          setHoveringGroup((Group)userObject, bounds.x + Group.THUMBNAIL_SIZE, bounds.y + Group.THUMBNAIL_SIZE / 2);
          setHoveringNode(null, 0);
        }
        else {
          setHoveringGroup(null, 0, 0);

          // Check if hovering an atom parameter.
          int index = -1;
          if (userObject instanceof Node) {
            index = Render.getNodeFieldIndex(myTree, node, x, false);
          }
          if (index >= 0) {
            setHoveringNode((Node)userObject, index);
          }
          else {
            setHoveringNode(null, 0);
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

      private void setHoveringNode(@Nullable Node node, int index) {
        if (node != lastHoverNode && lastHoverNode != null) {
          lastHoverNode.hoveredParameter = -1;
        }
        lastHoverNode = node;

        Path followPath = Path.EMPTY;
        if (node != null) {
          node.computeFollowPath(myEditor.getClient(), myEditor.getAtomStream().getPath(), index, new Runnable() {
            @Override
            public void run() {
              myTree.repaint();
            }
          });
          followPath = node.getFollowPath(index);
        }

        if (followPath != Path.EMPTY) {
          node.hoveredParameter = index;
        }

        hoverHand(myTree, myEditor.getAtomStream().getPath(), followPath);
        myTree.repaint();
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
                        group.getPreview(myEditor.getClient(), device, atoms))
                    .setAnimationCycle(100)
                    .createBalloon();
                  lastShownBalloon.show(new RelativePoint(myTree, new Point(x, y)), Balloon.Position.atRight);
                }
              }
            }
          }
        });
      }

      @Override
      public void mouseClicked(MouseEvent event) {
        Object object = getDataObjectAt(myTree.getPathForLocation(event.getX(), event.getY()));
        if (object instanceof Node) {
          Node node = (Node)object;
          // The user was hovering over a parameter, fire off the path activation event on click.
          if (node.hoveredParameter >= 0) {
            Path path = node.getFollowPath(node.hoveredParameter);
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

    myTreeBuilder = new AbstractTreeBuilder(myTree, (DefaultTreeModel)myTree.getModel(), null, null, false);
    Disposer.register(myEditor, myTreeBuilder);
  }

  @NotNull
  @Override
  protected TreeCellRenderer createRenderer() {
    return new ColoredTreeCellRenderer() {
      @Override
      public void customizeCellRenderer(
          @NotNull final JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        if (value instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)value;
          Object obj = treeNode.getUserObject();
          if (obj instanceof Renderable) {
            Renderable renderable = (Renderable)obj;
            renderable.render(this, SimpleTextAttributes.REGULAR_ATTRIBUTES);
          }
          else {
            // may get "loading..." node
            append(String.valueOf(obj));
          }
        }
        else {
          assert false : value;
        }
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
        DevicePath device = myRenderDevice.getPath();
        AtomsPath atoms = myEditor.getAtomStream().getPath();
        if (userObject instanceof Group && device != null && atoms != null) {
          Group group = (Group)userObject;
          if (shouldShowPreview(group)) {
            setIcon(group.getThumbnail(myEditor.getClient(), device, atoms).withRepaintComponent(tree));
          }
        }
      }
    };
  }

  @Override
  @NotNull
  protected TreeModel createEmptyModel() {
    return new DefaultTreeModel(new DefaultMutableTreeNode());
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
  private Object getDataObjectAt(@Nullable TreePath path) {
    if (path == null) {
      return null;
    }
    DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode)path.getLastPathComponent();
    return treeNode.getUserObject();
  }

  private void findNextNode(Pattern pattern) {
    Renderable start = (Renderable)((DefaultMutableTreeNode)myTree.getLastSelectedPathComponent()).getUserObject();
    if (start == null) {
      start = getRoot();
    }
    Renderable change = findMatchingChild(start, pattern);
    if (change == null) {
      Renderable node = start;
      while (change == null && node != null) {
        change = findMatchingSibling(node, pattern);
        node = node.getParentDescriptor();
      }
    }
    if (change == null && start != myTree.getModel().getRoot()) {
      // TODO: this searches the entire tree again.
      change = findMatchingChild(getRoot(), pattern);
    }
    if (change != null) {
      myTreeBuilder.select(change);
    }
  }

  private Group getRoot() {
    AtomTreeStructure treeStructure = (AtomTreeStructure)myTreeBuilder.getTreeStructure();
    assert treeStructure != null;
    return treeStructure.getRootElement();
  }

  private Renderable findMatchingChild(Renderable node, Pattern pattern) {
    AbstractTreeStructure treeStructure = myTreeBuilder.getTreeStructure();
    assert treeStructure != null;

    for (Object object : treeStructure.getChildElements(node)) {
      Renderable child = (Renderable)object;
      if (matches(child, pattern)) {
        return child;
      }
      Renderable result = findMatchingChild(child, pattern);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private Renderable findMatchingSibling(Renderable node, Pattern pattern) {
    AbstractTreeStructure treeStructure = myTreeBuilder.getTreeStructure();
    assert treeStructure != null;

    Renderable parent = node.getParentDescriptor();
    if (parent == null) {
      return null;
    }

    Object[] siblings = treeStructure.getChildElements(parent);
    int index = Arrays.asList(siblings).indexOf(node);
    assert index >= 0;

    for (int i = index + 1; i < siblings.length; i++) {
      Renderable sibling = (Renderable)siblings[i];
      if (matches(sibling, pattern)) {
        return sibling;
      }
      Renderable result = findMatchingChild(sibling, pattern);
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  private boolean matches(Renderable node, Pattern pattern) {
    if (node instanceof Node) {
      return pattern.matcher(((Node)node).atom.getName()).find();
    }
    return false;
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
    Context context = atoms.getContexts().count() > 1 ? mySelectedContext : Context.ALL;

    assert hierarchy != null;
    myTreeBuilder.setTreeStructure(new AtomTreeStructure(hierarchy.getRoot(), atoms.getAtoms(), context));
    myTreeBuilder.queueUpdate();
  }

  static class AtomTreeStructure extends AbstractTreeStructure {

    @NotNull final AtomList myAtoms;
    @NotNull final Context myContext;
    @NotNull final Group myRoot;

    public AtomTreeStructure(@NotNull AtomGroup root, @NotNull AtomList atoms, @NotNull Context context) {
      myAtoms = atoms;
      myContext = context;
      myRoot = new Group(null, root, atoms);
    }

    @Override
    @NotNull
    public Group getRootElement() {
      return myRoot;
    }

    @Override
    @NotNull
    public Object[] getChildElements(Object element) {
      assert ((Renderable)element).getParentDescriptor() != null || myRoot.equals(element) : element;

      if (element instanceof Group) {
        Group group = (Group)element;
        return group.getChildren(myAtoms, myContext).toArray();
      }
      if (element instanceof Node) {
        Node node = (Node)element;
        return node.getChildren().toArray();
      }
      return ArrayUtil.EMPTY_OBJECT_ARRAY;
    }

    @Nullable
    @Override
    public Object getParentElement(Object element) {
      Renderable item = (Renderable)element;
      return item.getParentDescriptor();
    }

    @NotNull
    @Override
    public NodeDescriptor createDescriptor(Object element, NodeDescriptor parentDescriptor) {
      Renderable nodeDescriptor = (Renderable)element;
      assert nodeDescriptor.getParentDescriptor() == parentDescriptor;
      return nodeDescriptor;
    }

    @Override
    public void commit() {

    }

    @Override
    public boolean hasSomethingToCommit() {
      return false;
    }

    @Override
    public boolean isAlwaysLeaf(Object element) {
      return element instanceof Memory;
    }
  }

  private void selectContext(@NotNull ContextID id) {
    AtomStream atoms = myEditor.getAtomStream();
    Context context = atoms.getContexts().find(id, Context.ALL);
    if (!context.equals(mySelectedContext)) {
      mySelectedContext = context;

      if (myTreeBuilder.getTreeStructure() == null) {
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

  @Nullable("if this path can not be found in this tree")
  public static TreePath getTreePathInTree(TreePath treePath, JTree tree) {
    Object root = tree.getModel().getRoot();
    Object[] path = treePath.getPath();
    List<Object> newPath = new ArrayList<Object>();
    Object found = null;
    for (Object node : path) {
      if (found == null) {
        if (treeNodeEquals(root, node)) {
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
          if (treeNodeEquals(node, child)) {
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

  public static boolean treeNodeEquals(Object a, Object b) {
    if (a instanceof DefaultMutableTreeNode && b instanceof DefaultMutableTreeNode) {
      return Objects.equal(((DefaultMutableTreeNode)a).getUserObject(), ((DefaultMutableTreeNode)b).getUserObject());
    }
    return Objects.equal(a, b);
  }

  @Override
  public void onAtomsSelected(AtomRangePath path, Object source) {
    // we NEED this check here as if the user selects a memory observation (a child of a Atom)
    // we want other controllers to update to the new Atom position, but we dont want to move
    // our own selection to the Atom, we want to keep it on the child.
    if (source != this) {
      updateSelectionRange(getRoot(), path.getRange());
    }
  }

  /**
   * Attempts to select the highest possible node that represents the given range. If no such node exists,
   * the last atom in the range is selected. This does not fire an event, but simply updates the UI.
   */
  private void updateSelectionRange(@NotNull Group node, @NotNull Range range) {
    AbstractTreeStructure treeStructure = myTreeBuilder.getTreeStructure();
    assert treeStructure != null;

    // TODO: Searching through the list for now. Change to binary search.
    for (Object object : treeStructure.getChildElements(node)) {
      if (object instanceof Node && range.getLast() == (((Node)object).index)) {
        myTreeBuilder.select(object);
        return;
      }
      if (object instanceof Group) {
        Range groupRange = ((Group)object).group.getRange();
        if (groupRange.equals(range)) {
          myTreeBuilder.select(object);
          return;
        }
        if (groupRange.contains(range.getLast())) {
          updateSelectionRange((Group)object, range);
          return;
        }
      }
    }
  }
}
