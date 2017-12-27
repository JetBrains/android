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
package com.android.tools.idea.uibuilder.palette;

import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.common.model.AndroidCoordinate;
import com.android.tools.idea.uibuilder.model.DnDTransferComponent;
import com.android.tools.idea.uibuilder.model.DnDTransferItem;
import com.android.tools.idea.uibuilder.model.ItemTransferable;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.SceneView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DnDConstants;
import java.awt.image.BufferedImage;
import java.util.function.Supplier;

public class ItemTransferHandler extends TransferHandler {
  private final DesignSurface myDesignSurface;
  private final DependencyManager myDependencyManager;
  private final Supplier<Palette.Item> myItemSupplier;
  private final IconPreviewFactory myIconFactory;

  // TODO: Look into combining DependencyManager and IconPreviewFactory into a PreviewProvider
  public ItemTransferHandler(@NotNull DesignSurface designSurface,
                             @NotNull DependencyManager dependencyManager,
                             @NotNull Supplier<Palette.Item> itemSupplier,
                             @NotNull IconPreviewFactory iconFactory) {
    myDesignSurface = designSurface;
    myDependencyManager = dependencyManager;
    myItemSupplier = itemSupplier;
    myIconFactory = iconFactory;
  }

  @Override
  public int getSourceActions(@NotNull JComponent component) {
    return DnDConstants.ACTION_COPY_OR_MOVE;
  }

  @Override
  @Nullable
  protected Transferable createTransferable(@NotNull JComponent component) {
    Palette.Item item = myItemSupplier.get();
    if (item == null) {
      return null;
    }
    SceneView sceneView = myDesignSurface.getCurrentSceneView();
    if (sceneView == null) {
      return null;
    }

    @AndroidCoordinate
    Dimension size;
    BufferedImage image = myDependencyManager.needsLibraryLoad(item) ? null : myIconFactory.renderDragImage(item, sceneView);
    if (image != null) {
      size = new Dimension(image.getWidth(), image.getHeight());
      double scale = myDesignSurface.getScale();
      image = ImageUtils.scale(image, scale);
    }
    else {
      Icon icon = item.getIcon();
      //noinspection UndesirableClassUsage
      image = new BufferedImage(icon.getIconWidth(),
                               icon.getIconHeight(),
                               BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2 = (Graphics2D)image.getGraphics();
      icon.paintIcon(component, g2, 0, 0);
      g2.dispose();

      double scale = myDesignSurface.getScale();
      size = new Dimension((int)(image.getWidth() / scale), (int)(image.getHeight() / scale));
    }
    setDragImage(image);
    setDragImageOffset(new Point(-image.getWidth() / 2, -image.getHeight() / 2));
    DnDTransferComponent dndComponent = new DnDTransferComponent(item.getTagName(), item.getXml(), size.width, size.height);
    return new ItemTransferable(new DnDTransferItem(dndComponent));
  }
}
