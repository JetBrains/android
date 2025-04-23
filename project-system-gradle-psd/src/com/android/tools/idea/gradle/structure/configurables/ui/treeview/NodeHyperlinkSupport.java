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
package com.android.tools.idea.gradle.structure.configurables.ui.treeview;

import static com.android.tools.idea.gradle.structure.configurables.ui.UiUtil.isMetaOrCtrlKeyPressed;
import static com.android.tools.idea.gradle.structure.model.PsIssueCollectionKt.getTooltipText;
import static com.intellij.ui.SimpleTextAttributes.LINK_ATTRIBUTES;
import static com.intellij.ui.SimpleTextAttributes.STYLE_WAVED;
import static java.awt.Cursor.HAND_CURSOR;
import static java.awt.Cursor.getPredefinedCursor;
import static java.awt.event.KeyEvent.KEY_PRESSED;
import static java.awt.event.KeyEvent.KEY_RELEASED;
import static javax.swing.SwingUtilities.convertPointFromScreen;

import com.android.tools.idea.gradle.structure.configurables.PsContext;
import com.android.tools.idea.gradle.structure.configurables.issues.IssuesByTypeAndTextComparator;
import com.android.tools.idea.gradle.structure.model.PsChildModel;
import com.android.tools.idea.gradle.structure.model.PsIssue;
import com.android.tools.idea.gradle.structure.model.PsIssueCollection;
import com.android.tools.idea.gradle.structure.model.PsModel;
import com.intellij.ide.projectView.PresentationData;
import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.Tree;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import javax.swing.JTree;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NodeHyperlinkSupport<T extends SimpleNode> implements Disposable {
  @NotNull private final Tree myTree;
  @NotNull private final Class<T> mySupportedNodeType;
  @NotNull private final PsContext myContext;
  private final boolean myShowIssues;

  private T myHoveredNode;
  private KeyEventDispatcher myKeyEventDispatcher;

  public NodeHyperlinkSupport(@NotNull Tree tree, @NotNull Class<T> supportedNodeType, @NotNull PsContext context, boolean showIssues) {
    myTree = tree;
    mySupportedNodeType = supportedNodeType;
    myContext = context;
    myShowIssues = showIssues;
    addHyperlinkBehaviorToSupportedNodes();
    myTree.repaint();
  }

  private void addHyperlinkBehaviorToSupportedNodes() {
    myTree.setCellRenderer(new NodeRenderer() {
      @NotNull
      @Override
      protected SimpleTextAttributes getSimpleTextAttributes(@NotNull PresentationData presentation, Color color, @NotNull Object node) {
        List<PsIssue> issues = Collections.emptyList();

        if (myShowIssues && node instanceof AbstractPsModelNode) {
          AbstractPsModelNode<? extends PsModel> modelNode = (AbstractPsModelNode<? extends PsModel>)node;
          issues = findIssues(modelNode, IssuesByTypeAndTextComparator.INSTANCE);
          presentation.setTooltip(getTooltipText(issues, false));
        }

        if (myHoveredNode != null && myHoveredNode == node) {
          return LINK_ATTRIBUTES;
        }

        SimpleTextAttributes textAttributes = super.getSimpleTextAttributes(presentation, color, node);
        if (!issues.isEmpty()) {
          PsIssue issue = issues.get(0);
          Color waveColor = issue.getSeverity().getColor();
          textAttributes = textAttributes.derive(STYLE_WAVED, null, null, waveColor);
        }

        return textAttributes;
      }

      @Override
      public void customizeCellRenderer(@NotNull JTree tree,
                                        Object value,
                                        boolean selected,
                                        boolean expanded,
                                        boolean leaf,
                                        int row,
                                        boolean hasFocus) {
        if (myShowIssues) {
          AbstractPsModelNode<? extends PsModel> modelNode = findModelNode(value);
          if (modelNode != null) {
            List<PsIssue> issues = findIssues(modelNode, null);
            if (!issues.isEmpty()) {
              // Force color change of the node.
              modelNode.getPresentation().clearText();
            }
          }
        }

        super.customizeCellRenderer(tree, value, selected, expanded, leaf, row, hasFocus);
        Object userObject = ((DefaultMutableTreeNode)value).getUserObject();
          if (userObject instanceof CellAppearanceEx) {
          ((CellAppearanceEx)userObject).customize(this);
        }
      }

      @Nullable
      private AbstractPsModelNode<? extends PsModel> findModelNode(@Nullable Object value) {
        if (value instanceof DefaultMutableTreeNode) {
          DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
          Object userObject = node.getUserObject();
          if (userObject instanceof AbstractPsModelNode) {
            return (AbstractPsModelNode<? extends PsModel>)userObject;
          }
        }
        return null;
      }
    });

    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        Cursor cursor = null;
        T node = getIfHyperlink(e);
        if (node != null) {
          cursor = getPredefinedCursor(HAND_CURSOR);
        }
        setHoveredNode(node);
        myTree.setCursor(cursor);
      }
    };
    myTree.addMouseMotionListener(mouseListener);

    // Make the cursor change to 'hand' if the mouse pointer is over a supported node and the user presses Ctrl or Cmd.
    myKeyEventDispatcher = e -> {
      T node = null;
      if (e.getID() == KEY_PRESSED) {
        Cursor cursor = null;
        if (isMetaOrCtrlKeyPressed(e)) {
          node = getNodeUnderMousePointer();
          if (node != null) {
            cursor = getPredefinedCursor(HAND_CURSOR);
          }
        }
        setHoveredNode(node);
        myTree.setCursor(cursor);
      }
      else if (e.getID() == KEY_RELEASED) {
        if (isMetaOrCtrlKeyPressed(e)) {
          setHoveredNode(null);
        }
        myTree.setCursor(null);
      }
      return false;
    };

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
  }

  @NotNull
  private List<PsIssue> findIssues(@NotNull AbstractPsModelNode<? extends PsModel> modelNode, @Nullable Comparator<PsIssue> comparator) {
    List<PsIssue> issues = new ArrayList<>();

    PsIssueCollection issueCollection = myContext.getAnalyzerDaemon().getIssues();
    for (PsModel model : modelNode.getModels()) {
      if (model instanceof PsChildModel) {
        issues.addAll(issueCollection.findIssues(model.getPath(), null));
      }
    }
    if (comparator != null && issues.size() > 1) {
      issues.sort(comparator);
    }
    return issues;
  }

  private void setHoveredNode(@Nullable T node) {
    myHoveredNode = node;
    if (myHoveredNode != null) {
      // Force color change of the node.
      myHoveredNode.getPresentation().clearText();
    }
    myTree.repaint();
  }

  @Nullable
  public T getIfHyperlink(@NotNull MouseEvent e) {
    if (isMetaOrCtrlKeyPressed(e)) {
      return getNodeForLocation(e.getX(), e.getY());
    }
    return null;
  }

  private T getNodeUnderMousePointer() {
    PointerInfo pointerInfo = MouseInfo.getPointerInfo();
    if (pointerInfo != null) {
      Point location = pointerInfo.getLocation();
      convertPointFromScreen(location, myTree);
      return getNodeForLocation(location.x, location.y);
    }
    return null;
  }

  @Nullable
  public T getNodeForLocation(int x, int y) {
    Object userObject = null;

    TreePath path = myTree.getPathForLocation(x, y);
    if (path != null) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
      if (node != null) {
        userObject = node.getUserObject();
      }
    }

    return mySupportedNodeType.isInstance(userObject) ? mySupportedNodeType.cast(userObject) : null;
  }

  @Override
  public void dispose() {
    if (myKeyEventDispatcher != null) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(myKeyEventDispatcher);
    }
  }
}
