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
package com.android.tools.idea.updater.configure;

import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.util.ui.ThreeStateCheckBox;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.accessibility.AccessibleContextUtil;
import org.jetbrains.annotations.NotNull;

import javax.accessibility.AccessibleAction;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.accessibility.AccessibleStateSet;
import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeCellRenderer;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

/**
 * A tree node with a checkbox that can have three states.
 */
abstract class UpdaterTreeNode extends DefaultMutableTreeNode implements Comparable<UpdaterTreeNode>, SdkUpdaterConfigPanel.MultiStateRow {
  /**
   * @return The initial state of this node (representing the current state of the SDK).
   */
  abstract public NodeStateHolder.SelectedState getInitialState();

  /**
   * @return The current selected state of this node.
   */
  abstract public NodeStateHolder.SelectedState getCurrentState();

  /**
   * Set the state of the node to the given state. For parent nodes this will also set the state of the children.
   * @param state
   */
  abstract protected void setState(NodeStateHolder.SelectedState state);

  /**
   * Set the state of this node back to its initial state.
   */
  public final void resetState() {
    setState(getInitialState());
  }

  /**
   * Indicates whether this node, when represented by a summary node, should be installed when the
   * summary node is marked as to be installed.
   * @return True if this node should be selected when the corresponding summary node is selected.
   */
  public boolean includeInSummary() {
    return false;
  }

  /**
   * Indicates whether this node should be used to retrieve data for a summary node (e.g. currently installed revision).
   * @return
   */
  public boolean isPrimary() {
    return false;
  }

  /**
   * Set the selected state of this node to the next one in sequence. This is what happens when a user clicks a checkbox.
   */
  @Override
  public final void cycleState() {
    if (getCurrentState() == null) {
      return;
    }
    if (getCurrentState() == NodeStateHolder.SelectedState.NOT_INSTALLED) {
      if (canHaveMixedState()) {
        setState(NodeStateHolder.SelectedState.MIXED);
      }
      else {
        setState(NodeStateHolder.SelectedState.INSTALLED);
      }
    }
    else if (getCurrentState() == NodeStateHolder.SelectedState.INSTALLED) {
      setState(NodeStateHolder.SelectedState.NOT_INSTALLED);
    }
    else {
      setState(NodeStateHolder.SelectedState.INSTALLED);
    }
  }

  /**
   * Whether this node can have an intermediate state (neither selected nor deselected). This corresponds to e.g. a package being installed
   * but needing an update, or only some of the children of this node being selected.
   */
  protected boolean canHaveMixedState() {
    return false;
  }

  /**
   * Status to show in the table.
   */
  public String getStatusString() {
    return "";
  }

  @Override
  public int compareTo(UpdaterTreeNode o) {
    return toString().compareTo(o.toString());
  }

  public static class Renderer extends JPanel implements TreeCellRenderer {
    private final ColoredTreeCellRenderer myTextRenderer;
    public final ThreeStateCheckBox myCheckbox;

    public Renderer() {
      super(new BorderLayout());
      myCheckbox = new ThreeStateCheckBox();
      myTextRenderer = new ColoredTreeCellRenderer() {
        @Override
        public void customizeCellRenderer(@NotNull JTree tree,
                                          Object value,
                                          boolean selected,
                                          boolean expanded,
                                          boolean leaf,
                                          int row,
                                          boolean hasFocus) {
        }
      };
      myTextRenderer.setOpaque(true);
      add(myCheckbox, BorderLayout.WEST);
      add(myTextRenderer, BorderLayout.CENTER);
    }

    @Override
    public final Component getTreeCellRendererComponent(JTree tree,
                                                        Object value,
                                                        boolean selected,
                                                        boolean expanded,
                                                        boolean leaf,
                                                        int row,
                                                        boolean hasFocus) {
      if (!(value instanceof UpdaterTreeNode)) {
        return null;
      }
      UpdaterTreeNode node = (UpdaterTreeNode)value;
      invalidate();
      myCheckbox.setVisible(true);
      if (node.getCurrentState() == NodeStateHolder.SelectedState.MIXED) {
        myCheckbox.setState(ThreeStateCheckBox.State.DONT_CARE);
      }
      else {
        myCheckbox.setSelected(node.getCurrentState() == NodeStateHolder.SelectedState.INSTALLED);
      }
      myCheckbox.setOpaque(false);
      myCheckbox.setBackground(null);
      setBackground(null);
      myTextRenderer.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);

      if (UIUtil.isUnderGTKLookAndFeel()) {
        final Color background = selected ? UIUtil.getTreeSelectionBackground() : UIUtil.getTreeTextBackground();
        UIUtil.changeBackGround(this, background);
      }
      else if (UIUtil.isUnderNimbusLookAndFeel()) {
        UIUtil.changeBackGround(this, UIUtil.TRANSPARENT_COLOR);
      }
      node.customizeRenderer(this, tree, selected, expanded, leaf, row, hasFocus);
      revalidate();

      return this;
    }

    public ColoredTreeCellRenderer getTextRenderer() {
      return myTextRenderer;
    }

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext = new AccessibleRenderer();
      }
      return accessibleContext;
    }

    /**
     * Expose accessible properties as a mix of the underlying {@link #myCheckbox} and {@link #myTextRenderer}
     * so that {@link Renderer} behaves like a regular checkbox with an associated label.
     */
    protected class AccessibleRenderer extends AccessibleJPanel {

      @Override
      public AccessibleRole getAccessibleRole() {
        return myCheckbox.getAccessibleContext().getAccessibleRole();
      }

      @Override
      public String getAccessibleName() {
        return AccessibleContextUtil.combineAccessibleStrings(
          myTextRenderer.getAccessibleContext().getAccessibleName(), " ",
          myCheckbox.getAccessibleContext().getAccessibleName());
      }

      @Override
      public String getAccessibleDescription() {
        return AccessibleContextUtil.combineAccessibleStrings(
          myTextRenderer.getAccessibleContext().getAccessibleDescription(), " ",
          myCheckbox.getAccessibleContext().getAccessibleDescription());
      }

      @Override
      public AccessibleStateSet getAccessibleStateSet() {
        AccessibleStateSet set = new AccessibleStateSet();
        set.addAll(myCheckbox.getAccessibleContext().getAccessibleStateSet().toArray());
        set.addAll(myTextRenderer.getAccessibleContext().getAccessibleStateSet().toArray());
        return set;
      }

      @Override
      public AccessibleAction getAccessibleAction() {
        return myCheckbox.getAccessibleContext().getAccessibleAction();
      }
    }
  }

  public void customizeRenderer(Renderer renderer,
                                JTree tree,
                                boolean selected,
                                boolean expanded,
                                boolean leaf,
                                int row,
                                boolean hasFocus) {
  }
}
