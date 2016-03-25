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
package com.android.tools.idea.gradle.structure.configurables.android.dependencies.treeview;

import com.intellij.ide.util.treeView.NodeRenderer;
import com.intellij.ide.util.treeView.PresentableNodeDescriptor;
import com.intellij.openapi.Disposable;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.treeStructure.SimpleNode;
import com.intellij.ui.treeStructure.Tree;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import static com.android.tools.idea.gradle.structure.configurables.android.dependencies.UiUtil.isMetaOrCtrlKeyPressed;
import static com.intellij.ui.SimpleTextAttributes.LINK_ATTRIBUTES;
import static java.awt.Cursor.*;
import static java.awt.event.KeyEvent.KEY_PRESSED;
import static java.awt.event.KeyEvent.KEY_RELEASED;
import static javax.swing.SwingUtilities.convertPointFromScreen;

public class NodeHyperlinkSupport<T extends SimpleNode> implements Disposable {
  @NotNull private final Tree myTree;
  @NotNull private final Class<T> mySupportedNodeType;

  private T myHoveredNode;
  private KeyEventDispatcher myKeyEventDispatcher;

  public NodeHyperlinkSupport(@NotNull Tree tree, @NotNull Class<T> supportedNodeType) {
    myTree = tree;
    mySupportedNodeType = supportedNodeType;
    addHyperlinkBehaviorToSupportedNodes();
  }

  private void addHyperlinkBehaviorToSupportedNodes() {
    myTree.setCellRenderer(new NodeRenderer() {
      @Override
      protected SimpleTextAttributes getSimpleTextAttributes(PresentableNodeDescriptor node, Color color) {
        if (myHoveredNode != null && myHoveredNode == node) {
          return LINK_ATTRIBUTES;
        }
        return super.getSimpleTextAttributes(node, color);
      }
    });

    MouseAdapter mouseListener = new MouseAdapter() {
      @Override
      public void mouseMoved(MouseEvent e) {
        Cursor cursor = getDefaultCursor();
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
    myKeyEventDispatcher = new KeyEventDispatcher() {
      @Override
      public boolean dispatchKeyEvent(KeyEvent e) {
        T node = null;
        if (e.getID() == KEY_PRESSED) {
          Cursor cursor = getDefaultCursor();
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
          myTree.setCursor(getDefaultCursor());
        }
        return false;
      }
    };

    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(myKeyEventDispatcher);
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
