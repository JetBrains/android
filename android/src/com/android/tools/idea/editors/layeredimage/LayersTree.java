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

package com.android.tools.idea.editors.layeredimage;

import com.android.tools.pixelprobe.Image;
import com.android.tools.pixelprobe.Layer;
import com.intellij.icons.AllIcons;
import com.intellij.ui.ColoredTreeCellRenderer;
import com.intellij.ui.treeStructure.Tree;
import com.intellij.util.ui.tree.TreeUtil;
import icons.AndroidIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeSelectionModel;
import java.awt.*;
import java.util.List;

class LayersTree extends Tree {
  private DefaultMutableTreeNode myRoot;

  LayersTree() {
    configure();

    DefaultTreeModel model = new DefaultTreeModel(null);
    setModel(model);
  }

  void setImage(@Nullable Image image) {
    myRoot = new DefaultMutableTreeNode("Layers");
    if (image != null) {
      addLayers(myRoot, image.getLayers());
    }

    DefaultTreeModel model = (DefaultTreeModel)getModel();
    model.setRoot(myRoot);
    model.reload();

    expandAllRows();
  }

  private static void addLayers(@NotNull DefaultMutableTreeNode root, @NotNull List<Layer> layers) {
    for (Layer layer : layers) {
      DefaultMutableTreeNode node = new DefaultMutableTreeNode(layer);
      if (layer.getType() == Layer.Type.GROUP) {
        addLayers(node, layer.getChildren());
      }
      root.add(node);
    }
  }

  private void configure() {
    createCellRenderer();
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    setToggleClickCount(2);
    TreeUtil.installActions(this);
  }

  private void expandAllRows() {
    for (int i = 0; i < getRowCount(); i++) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)getPathForRow(i).getLastPathComponent();
      if (node == myRoot) {
        expandRow(i);
      } else {
        Layer layer = (Layer)node.getUserObject();
        if (layer.getType() == Layer.Type.GROUP && layer.isOpen()) {
          expandRow(i);
        }
      }
    }
    setRootVisible(false);
  }

  private void createCellRenderer() {
    ColoredTreeCellRenderer renderer = new LayerTreeCellRenderer();
    renderer.setBorder(BorderFactory.createEmptyBorder(1, 1, 0, 0));
    setCellRenderer(renderer);
  }

  private static class LayerTreeCellRenderer extends ColoredTreeCellRenderer {
    private final Composite mAlphaComposite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f);
    private boolean mVisible;

    @Override
    public void customizeCellRenderer(@NotNull JTree tree,
                                      Object value,
                                      boolean selected,
                                      boolean expanded,
                                      boolean leaf,
                                      int row,
                                      boolean hasFocus) {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
      Object content = node.getUserObject();

      if (content instanceof Layer) {
        Layer layer = (Layer) content;
        append(layer.getName());

        switch (layer.getType()) {
          case ADJUSTMENT:
            setIcon(AndroidIcons.Views.SeekBar);
            break;
          case IMAGE:
            setIcon(AndroidIcons.Views.ImageView);
            break;
          case GROUP:
            setIcon(AllIcons.Nodes.Folder);
            break;
          case SHAPE:
            setIcon(AndroidIcons.Views.TextureView);
            break;
          case TEXT:
            setIcon(AndroidIcons.Views.TextView);
            break;
        }

        mVisible = layer.isVisible();
      }
    }

    @Override
    protected void doPaint(Graphics2D g) {
      Composite old = g.getComposite();
      if (!mVisible) {
        g.setComposite(mAlphaComposite);
      }
      super.doPaint(g);
      if (!mVisible) {
        g.setComposite(old);
      }
    }
  }
}
