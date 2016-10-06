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

import com.android.ide.common.rendering.api.ResourceValue;
import com.android.ide.common.resources.ResourceResolver;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class NlPreviewPanel extends JPanel implements SelectionListener, Disposable {
  private final ImagePanel myImage;
  private final JLabel myItemName;

  public NlPreviewPanel(@NotNull DependencyManager dependencyManager) {
    super(new BorderLayout(0, 0));
    myImage = new ImagePanel(dependencyManager);
    myImage.setPreferredSize(new Dimension(0, 0));
    myItemName = new JBLabel();
    myItemName.setHorizontalAlignment(SwingConstants.CENTER);
    add(myImage, BorderLayout.CENTER);
    add(myItemName, BorderLayout.SOUTH);
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    myImage.setDesignSurface(designSurface);
  }

  @Override
  public void selectionChanged(@Nullable Palette.Item item) {
    myImage.setItem(item);
    myItemName.setText(item != null ? item.getTitle() : " ");
  }

  @Override
  public void dispose() {
    Disposer.dispose(myImage);
  }

  private static class ImagePanel extends JComponent implements Disposable {
    private final DependencyManager myDependencyManager;
    private DesignSurface myDesignSurface;
    private Palette.Item myItem;
    private BufferedImage myImage;
    private boolean myKeepImageScaledToMatchPanelWidth;
    private int myLastWidth;

    private ImagePanel(@NotNull DependencyManager dependencyManager) {
      myDependencyManager = dependencyManager;
      addMouseListener(new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent event) {
          if (myItem == null) {
            return;
          }
          if (!myDependencyManager.needsLibraryLoad(myItem)) {
            TransferHandler handler = getTransferHandler();
            if (handler != null) {
              handler.exportAsDrag(ImagePanel.this, event, TransferHandler.COPY);
            }
          }
          else {
            myDependencyManager.ensureLibraryIsIncluded(myItem);
          }
        }
      });
    }

    @Override
    public void dispose() {
      setTransferHandler(null);
    }

    private void setItem(@Nullable Palette.Item item) {
      myItem = item;
      myImage = null;
      repaint();
    }

    @Nullable
    private Palette.Item getItem() {
      return myItem;
    }

    private void setDesignSurface(@Nullable DesignSurface designSurface) {
      myDesignSurface = designSurface;
      myImage = null;
      setTransferHandler(designSurface != null ? new ItemTransferHandler(myDesignSurface, this::getItem) : null);
      repaint();
    }

    @Override
    public void paint(Graphics g) {
      g.setColor(getBackgroundColor());
      g.fillRect(0, 0, getWidth(), getHeight());

      if (myImage == null || myImage.getWidth() > getWidth() || myKeepImageScaledToMatchPanelWidth && myLastWidth != getWidth()) {
        myImage = createPreviewImage();
        myLastWidth = getWidth();
      }
      if (myImage != null) {
        int x = Math.max(0, (getWidth() - myImage.getWidth()) / 2);
        int y = Math.max(0, (getHeight() - myImage.getHeight()) / 2);
        UIUtil.drawImage(g, myImage, x, y, this);

        if (myImage.getHeight() > getHeight() && g instanceof Graphics2D) {
          int width = Math.min(getWidth(), myImage.getWidth());
          int height = Math.min(getHeight(), myImage.getHeight());
          Graphics2D g2 = (Graphics2D)g;
          Color color = getBackground();
          //noinspection UseJBColor
          g2.setPaint(new GradientPaint(
            new Point(0, height / 8),
            new Color(color.getRed(), color.getGreen(), color.getBlue(), 0),
            new Point(0, height),
            new Color(color.getRed(), color.getGreen(), color.getBlue(), 220)));
          g2.fillRect(x, 0, width, height);
        }
      }
      else if (myItem != null) {
        Icon icon = myDependencyManager.createLargeItemIcon(myItem, this);
        int x = Math.max(0, (getWidth() - icon.getIconWidth()) / 2);
        int y = Math.max(0, (getHeight() - icon.getIconWidth()) / 2);
        icon.paintIcon(this, g, x, y);
      }
    }

    @NotNull
    private Color getBackgroundColor() {
      Configuration configuration = myDesignSurface != null ? myDesignSurface.getConfiguration() : null;
      ResourceResolver resolver = configuration != null ? configuration.getResourceResolver() : null;
      if (resolver == null) {
        return UIUtil.getPanelBackground();
      }
      ResourceValue windowBackground = resolver.findItemInTheme("background", true);
      Color background = ResourceHelper.resolveColor(resolver, windowBackground, myDesignSurface.getProject());
      return background != null ? background : UIUtil.getPanelBackground();
    }

    @Nullable
    private BufferedImage createPreviewImage() {
      if (myItem == null || myDesignSurface == null) {
        return null;
      }
      ScreenView screenView = myDesignSurface.getCurrentScreenView();
      if (screenView == null) {
        return null;
      }
      BufferedImage image = IconPreviewFactory.get().renderDragImage(myItem, screenView);
      if (image == null) {
        return null;
      }
      double factor = ImageUtils.supportsRetina() ? 2.0 : 1.0;
      double scale = factor * myDesignSurface.getScale();
      myKeepImageScaledToMatchPanelWidth = image.getWidth() * scale > getWidth() * factor;
      if (myKeepImageScaledToMatchPanelWidth) {
        scale = factor * getWidth() / image.getWidth();
      }
      image = ImageUtils.scale(image, scale);
      BufferedImage retina = ImageUtils.convertToRetina(image);
      if (retina != null) {
        image = retina;
      }
      return image;
    }
  }
}
