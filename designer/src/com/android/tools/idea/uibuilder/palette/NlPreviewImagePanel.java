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
import com.android.tools.idea.configurations.ConfigurationListener;
import com.android.tools.idea.rendering.ImageUtils;
import com.android.tools.idea.res.ResourceHelper;
import com.android.tools.idea.res.ResourceNotificationManager;
import com.android.tools.idea.res.ResourceNotificationManager.ResourceChangeListener;
import com.android.tools.idea.uibuilder.surface.DesignSurface;
import com.android.tools.idea.uibuilder.surface.PanZoomListener;
import com.android.tools.idea.uibuilder.surface.ScreenView;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.module.Module;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AdjustmentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

public class NlPreviewImagePanel extends JComponent implements Disposable {
  private static final int BORDER_WIDTH = 10;
  
  private final IconPreviewFactory myIconPreviewFactory;
  private final DependencyManager myDependencyManager;
  private final Runnable myCloseAutoHideCallback;
  private final ConfigurationListener myConfigurationListener;
  private final ResourceChangeListener myResourceChangeListener;
  private final PanZoomListener myZoomListener;
  private DesignSurface myDesignSurface;
  private Palette.Item myItem;
  private BufferedImage myImage;
  private boolean myPreviewGenerationDone;
  private boolean myKeepImageScaledToMatchPanelWidth;
  private int myLastWidth;

  public NlPreviewImagePanel(@NotNull IconPreviewFactory iconFactory,
                      @NotNull DependencyManager dependencyManager,
                      @NotNull Runnable closeAutoHideCallback) {
    myIconPreviewFactory = iconFactory;
    myDependencyManager = dependencyManager;
    myCloseAutoHideCallback = closeAutoHideCallback;
    myResourceChangeListener = reason -> invalidateUI();
    myConfigurationListener = flags -> {
      invalidateUI();
      return true;
    };
    myZoomListener = new PanZoomListener() {
      @Override
      public void zoomChanged(DesignSurface designSurface) {
        invalidateUI();
      }

      @Override
      public void panningChanged(AdjustmentEvent adjustmentEvent) {}
    };
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent event) {
        if (myItem == null) {
          return;
        }
        if (!myDependencyManager.needsLibraryLoad(myItem)) {
          TransferHandler handler = getTransferHandler();
          if (handler != null) {
            myCloseAutoHideCallback.run();
            handler.exportAsDrag(NlPreviewImagePanel.this, event, TransferHandler.COPY);
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
    setDesignSurface(null);
    setTransferHandler(null);
  }

  private void invalidateUI() {
    myImage = null;
    myPreviewGenerationDone = false;
    repaint();
  }

  public void setItem(@Nullable Palette.Item item) {
    myItem = item;
    invalidateUI();
  }

  @Nullable
  private Palette.Item getItem() {
    return myItem;
  }

  public void setDesignSurface(@Nullable DesignSurface designSurface) {
    Module oldModule = null;
    Configuration oldConfiguration = myDesignSurface != null ? myDesignSurface.getConfiguration() : null;
    if (oldConfiguration != null) {
      oldModule = oldConfiguration.getModule();
      oldConfiguration.removeListener(myConfigurationListener);
    }
    if (myDesignSurface != null) {
      myDesignSurface.removePanZoomListener(myZoomListener);
    }
    myDesignSurface = designSurface;
    Module newModule = null;
    Configuration newConfiguration = myDesignSurface != null ? myDesignSurface.getConfiguration() : null;
    if (newConfiguration != null) {
      newModule = newConfiguration.getModule();
      newConfiguration.addListener(myConfigurationListener);
    }
    if (myDesignSurface != null) {
      myDesignSurface.addPanZoomListener(myZoomListener);
    }
    if (newModule != oldModule) {
      ResourceNotificationManager manager = ResourceNotificationManager.getInstance(myDependencyManager.getProject());
      AndroidFacet oldFacet = oldModule != null ? AndroidFacet.getInstance(oldModule) : null;
      if (oldFacet != null) {
        manager.removeListener(myResourceChangeListener, oldFacet, null, null);
      }
      AndroidFacet newFacet = newModule != null ? AndroidFacet.getInstance(newModule) : null;
      if (newFacet != null) {
        manager.addListener(myResourceChangeListener, newFacet, null, null);
      }
    }
    myImage = null;
    myPreviewGenerationDone = false;
    setTransferHandler(designSurface != null ? new ItemTransferHandler(myDesignSurface, this::getItem, myIconPreviewFactory) : null);
    invalidateUI();
  }

  @Override
  public void paintComponent(@NotNull Graphics graphics) {
    BufferedImage cachedImage = getPreviewImage();
    if (cachedImage != null) {
      int xDiff = Math.max(0, getWidth() - cachedImage.getWidth());
      int yDiff = Math.max(0, getHeight() - cachedImage.getHeight());
      int xMargin = Math.min(BORDER_WIDTH, Math.max(0, xDiff - 2 * BORDER_WIDTH) / 2);
      int yMargin = Math.min(BORDER_WIDTH, Math.max(0, yDiff - 2 * BORDER_WIDTH) / 2);
      graphics.setColor(getBackgroundColor());
      graphics.fillRect(xMargin, yMargin, getWidth() - 2 * xMargin, getHeight() - 2 * yMargin);

      UIUtil.drawImage(graphics, cachedImage, xDiff / 2, yDiff / 2, this);

      if (cachedImage.getHeight() > getHeight() && graphics instanceof Graphics2D) {
        int width = Math.min(getWidth(), cachedImage.getWidth());
        int height = Math.min(getHeight(), cachedImage.getHeight());
        Graphics2D g2 = (Graphics2D)graphics;
        Color color = getBackground();
        //noinspection UseJBColor
        g2.setPaint(new GradientPaint(
          new Point(0, height / 8),
          new Color(color.getRed(), color.getGreen(), color.getBlue(), 0),
          new Point(0, height),
          new Color(color.getRed(), color.getGreen(), color.getBlue(), 220)));
        g2.fillRect(xDiff / 2, 0, width, height);
      }
    }
    else if (myItem != null) {
      Icon icon = myDependencyManager.createLargeItemIcon(myItem, this);
      int x = Math.max(0, (getWidth() - icon.getIconWidth()) / 2);
      int y = Math.max(0, (getHeight() - icon.getIconWidth()) / 2);
      icon.paintIcon(this, graphics, x, y);
    }
  }

  @NotNull
  private Color getBackgroundColor() {
    Configuration configuration = myDesignSurface != null ? myDesignSurface.getConfiguration() : null;
    ResourceResolver resolver = configuration != null ? configuration.getResourceResolver() : null;
    if (resolver == null) {
      return UIUtil.getPanelBackground();
    }
    ResourceValue windowBackground = resolver.findItemInTheme("colorBackground", true);
    Color background = ResourceHelper.resolveColor(resolver, windowBackground, myDesignSurface.getProject());
    return background != null ? background : UIUtil.getPanelBackground();
  }

  @Nullable
  private BufferedImage getPreviewImage() {
    if (myPreviewGenerationDone) {
      if (myImage == null || !(myImage.getWidth() > getWidth() || myKeepImageScaledToMatchPanelWidth && myLastWidth != getWidth())) {
        return myImage;
      }
    }
    myLastWidth = getWidth();
    myPreviewGenerationDone = true;
    if (myItem == null || myDesignSurface == null) {
      return null;
    }
    ScreenView screenView = myDesignSurface.getCurrentScreenView();
    if (screenView == null) {
      return null;
    }
    BufferedImage image = myIconPreviewFactory.renderDragImage(myItem, screenView);
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
    myImage = ImageUtils.convertToRetinaIgnoringFailures(image);
    return myImage;
  }
}