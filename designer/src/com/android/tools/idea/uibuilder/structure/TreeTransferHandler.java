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
package com.android.tools.idea.uibuilder.structure;

import com.android.tools.idea.common.model.NlComponent;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.scene.Scene;
import com.intellij.openapi.application.ApplicationManager;
import java.awt.AlphaComposite;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.TransferHandler;
import javax.swing.tree.TreePath;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enable dragging of components in the component tree.
 */
public final class TreeTransferHandler extends TransferHandler {

  @Override
  public int getSourceActions(JComponent c) {
    return DnDConstants.ACTION_COPY_OR_MOVE;
  }

  @Override
  protected Transferable createTransferable(JComponent c) {
    NlComponentTree tree = (NlComponentTree)c;
    setDragImage(getDragImageOfSelection(tree));
    Scene scene = tree.getScene();
    if (scene != null && !scene.getDesignSurface().getSelectionModel().isEmpty()) {
      return scene.getDesignSurface().getSelectionAsTransferable();
    }
    return delegateTransfer(tree);
  }

  private static Transferable delegateTransfer(NlComponentTree tree) {
    DelegatedTreeEventHandler handler = NlTreeUtil.getSelectionTreeHandler(tree);
    TreePath[] selectionPaths = tree.getSelectionModel().getSelectionPaths();
    return handler != null ? handler.getTransferable(selectionPaths) : null;
  }

  @Override
  protected void exportDone(JComponent c, Transferable transferable, int dropAction) {
    if (dropAction == DnDConstants.ACTION_MOVE) {
      // All we need to do is deleting the components that were moved out of this designer.
      // Internal moves are handled by {@see NlDropListener} such that both the copy and the delete
      // are in a single undo transaction.
      NlComponentTree tree = (NlComponentTree)c;
      NlModel model = tree.getDesignerModel();
      List<NlComponent> selected = tree.getSelectedComponents();
      if (model != null && !selected.isEmpty()) {
        ApplicationManager.getApplication().invokeLater(() -> model.getTreeWriter().delete(selected));
      }
    }
  }

  @Nullable
  private static Image getDragImageOfSelection(@NotNull NlComponentTree tree) {
    TreePath[] paths = tree.getSelectionPaths();
    if (paths == null) {
      return null;
    }
    int width = 0;
    int height = 0;
    for (TreePath path : paths) {
      int row = tree.getRowForPath(path);
      Component component =
        tree.getCellRenderer().getTreeCellRendererComponent(tree, path.getLastPathComponent(), false, true, true, row, false);
      Dimension size = component.getPreferredSize();
      width = Math.max(width, size.width);
      height += size.height;
    }

    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = (Graphics2D)image.getGraphics();
    for (TreePath path : paths) {
      int row = tree.getRowForPath(path);
      Component component =
        tree.getCellRenderer().getTreeCellRendererComponent(tree, path.getLastPathComponent(), false, true, true, row, false);
      Dimension size = component.getPreferredSize();
      component.setSize(size);
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
      g2.translate(0, size.height);
    }
    g2.dispose();

    return image;
  }
}
