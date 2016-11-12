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
import com.android.tools.idea.editors.gfxtrace.service.path.Path;
import com.android.tools.idea.editors.gfxtrace.widgets.CopyEnabledTree;
import com.android.tools.idea.editors.gfxtrace.widgets.LoadablePanel;
import com.android.tools.idea.editors.gfxtrace.widgets.Tree;
import com.intellij.openapi.actionSystem.impl.ActionMenu;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreeModel;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;


public abstract class TreeController extends Controller implements CopyEnabledTree.ColumnTextProvider {
  public static final int TREE_ROW_HEIGHT = JBUI.scale(19);

  /**
   * Renderer used for Nodes to allow them compose another component on the right.
   * See: {@link com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeRenderer}.
   */
  public abstract static class CompositeCellRenderer extends ColoredTreeCellRenderer {
    protected final RightCellRenderer myRightComponent = new RightCellRenderer();
    protected int myRightComponentOffset;
    protected boolean myRightComponentShow;

    public CompositeCellRenderer() {
      super();
      getIpad().right = 0;
      myRightComponent.getIpad().left = 0;
    }

    public void setup(@NotNull JTree tree, @NotNull TreePath treePath) {
      getTreeCellRendererComponent(tree, treePath.getLastPathComponent(),
                                   tree.isPathSelected(treePath), tree.isExpanded(treePath),
                                   true/*has no effect*/, tree.getRowForPath(treePath), tree.hasFocus());
    }

    public RightCellRenderer getRightComponent() {
      return myRightComponent;
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

    public int getRightComponentOffset() {
      return myRightComponentOffset;
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

  public static class RightCellRenderer extends ColoredTreeCellRenderer {
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

  @NotNull protected final LoadablePanel myLoadingPanel;
  @NotNull protected final JPanel myPanel = new JPanel(new BorderLayout());
  @NotNull protected final JBScrollPane myScrollPane = new JBScrollPane();
  @NotNull protected final Tree myTree = new CopyEnabledTree(createEmptyModel(), this);

  public TreeController(@NotNull GfxTraceEditor editor, @NotNull String emptyText) {
    super(editor);
    myPanel.add(myScrollPane, BorderLayout.CENTER);
    myTree.setRowHeight(TREE_ROW_HEIGHT);
    myTree.setRootVisible(false);
    myTree.setLineStyleAngled();
    myTree.setCellRenderer(createRenderer());
    myTree.getEmptyText().setText(emptyText);
    myLoadingPanel = new LoadablePanel(new BorderLayout());
    myLoadingPanel.getContentLayer().add(myTree);
    myScrollPane.setViewportView(myLoadingPanel);
    myScrollPane.getHorizontalScrollBar().setUnitIncrement(TREE_ROW_HEIGHT);
    myScrollPane.getVerticalScrollBar().setUnitIncrement(TREE_ROW_HEIGHT);
  }

  @NotNull
  protected abstract TreeCellRenderer createRenderer();

  @NotNull
  protected abstract TreeModel createEmptyModel();

  @Override
  public void clear() {
    // avoid setting the model to null, as then queued events coming back will throw null pointers.
    myTree.setModel(createEmptyModel());
  }

  public void setModel(@NotNull TreeModel model) {
    assert (ApplicationManager.getApplication().isDispatchThread());
    myTree.setModel(model);
    myLoadingPanel.stopLoading();
  }

  @NotNull
  public TreeModel getModel() {
    return myTree.getModel();
  }

  public static void hoverHand(@NotNull Component component, @Nullable Path root, @Nullable Path followPath) {
    boolean validPath = followPath != null && followPath != Path.EMPTY;
    ActionMenu.showDescriptionInStatusBar(true, component, validPath ? getDisplayTextFor(root, followPath) : null);
    component.setCursor(validPath ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR) : Cursor.getDefaultCursor());
  }

  public static @NotNull String getDisplayTextFor(@Nullable Path root, @NotNull Path path) {
    List<Path> pathParts = new ArrayList<>();
    while (path != null) {
      pathParts.add(path);
      path = path.getParent();
    }

    List<Path> rootParts = new ArrayList<>();
    while (root != null) {
      rootParts.add(root);
      root = root.getParent();
    }

    // If our path starts with status or atoms root, then we want to trim that from the start.
    for (int i = rootParts.size() - 1; i >= 0; i--) {
      if (pathParts.get(pathParts.size() - 1).equals(rootParts.get(i))) {
        pathParts.remove(pathParts.size() - 1);
      }
      else {
        break;
      }
    }

    StringBuilder stringBuilder = new StringBuilder();
    stringBuilder.append(pathParts.get(pathParts.size() - 1).getSegmentString());
    for (int i = pathParts.size() - 2; i >= 0; i--) {
      pathParts.get(i).appendSegmentToPath(stringBuilder);
    }
    return stringBuilder.toString();
  }
}
