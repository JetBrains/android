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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.android.tools.idea.uibuilder.model.NlModel;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * Enable dragging of components in the component tree.
 */
public final class TreeTransferHandler extends TransferHandlerWithDragImage {

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // TransferHandler
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  @Override
  public int getSourceActions(JComponent c) {
    return DnDConstants.ACTION_COPY_OR_MOVE;
  }

  @Override
  protected Transferable createTransferable(JComponent c) {
    NlComponentTree tree = (NlComponentTree)c;
    setDragImage(getDragImageOfSelection(tree));
    NlModel model = tree.getDesignerModel();
    if (model == null || model.getSelectionModel().isEmpty()) {
      return null;
    }
    return model.getSelectionAsTransferable();
  }

  @Override
  protected void exportDone(JComponent c, Transferable transferable, int dropAction) {
    if (dropAction == DnDConstants.ACTION_MOVE) {
      // All we need to do is deleting the components that were moved out of this designer.
      // Internal moves are handled by {@see NlDropListener} such that both the copy and the delete
      // are in a single undo transaction.
      NlComponentTree tree = (NlComponentTree)c;
      NlModel model = tree.getDesignerModel();
      assert model != null;
      model.delete(tree.getSelectedComponents());
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
    List<BufferedImage> images = new ArrayList<BufferedImage>(paths.length);
    for (TreePath path : paths) {
      int row = tree.getRowForPath(path);
      Component comp =
        tree.getCellRenderer().getTreeCellRendererComponent(tree, path.getLastPathComponent(), false, true, true, row, false);
      comp.setSize(comp.getPreferredSize());
      final BufferedImage image = UIUtil.createImage(comp.getWidth(), comp.getHeight(), BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();
      g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.7f));
      comp.paint(g2);
      g2.dispose();
      images.add(image);
      width = Math.max(width, comp.getWidth());
      height += comp.getHeight();
    }
    final BufferedImage result = UIUtil.createImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = (Graphics2D)result.getGraphics();
    int y = 0;
    for (BufferedImage image : images) {
      g2.drawImage(image, null, 0, y);
      y += image.getHeight();
    }
    g2.dispose();

    return result;
  }
}
