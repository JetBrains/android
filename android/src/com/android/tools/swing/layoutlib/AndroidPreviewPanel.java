/*
 * Copyright (C) 2014 The Android Open Source Project
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
package com.android.tools.swing.layoutlib;

import com.android.ide.common.rendering.api.ILayoutPullParser;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.DomPullParser;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbService;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generic UI component for rendering.
 */
public class AndroidPreviewPanel extends JComponent implements Scrollable {
  private static final Logger LOG = Logger.getInstance(AndroidPreviewPanel.class);

  private static final Notification UNSUPPORTED_LAYOUTLIB_NOTIFICATION =
    new Notification("Android", "Layoutlib", "The preview requires the latest version of layoutlib", NotificationType.ERROR);

  private static final AtomicBoolean ourLayoutlibNotification = new AtomicBoolean(false);

  private final DumbService myDumbService;
  private Configuration myConfiguration;
  private Document myDocument;
  private GraphicsLayoutRenderer myGraphicsLayoutRenderer;
  private double myScale = 1.0;
  private Dimension myLastRenderedSize;

  public AndroidPreviewPanel(@NotNull Configuration configuration) {
    myConfiguration = configuration;

    myDumbService = DumbService.getInstance(myConfiguration.getModule().getProject());
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    Dimension previousSize = getSize();

    super.setBounds(x, y, width, height);

    // Update the size of the layout renderer. This is done here instead of a component listener because
    // this runs before the paintComponent saving an extra paint cycle.
    Dimension currentSize = getSize();
    if (myGraphicsLayoutRenderer != null && !currentSize.equals(previousSize)) {
      // Because we use GraphicsLayoutRender in vertical scroll mode, the height passed it's only a minimum. If the actual rendering results
      // in a bigger size, the GraphicsLayoutRenderer.getPreferredSize() call will return the correct size.
      myGraphicsLayoutRenderer.setSize(width, height);
    }
  }

  public void setScale(double scale) {
    myScale = scale;
    if (myGraphicsLayoutRenderer != null) {
      myGraphicsLayoutRenderer.setScale(scale);
    }
  }

  public void invalidateGraphicsRenderer() {
    if (myGraphicsLayoutRenderer == null) {
      return;
    }

    myGraphicsLayoutRenderer.dispose();
    myGraphicsLayoutRenderer = null;
  }

  /**
   * Updates the current configuration. You need to call this method if you change the configuration and want to update the rendered view.
   * <p/>
   * <p/>This will re-inflate the sample view with the new parameters in the configuration.
   *
   * @param configuration
   */
  public void updateConfiguration(@NotNull Configuration configuration) {
    myConfiguration = configuration;
    invalidateGraphicsRenderer();
  }

  public void setDocument(@NotNull Document document) {
    myDocument = document;
    invalidateGraphicsRenderer();
  }

  @Override
  public void paintComponent(final Graphics graphics) {
    super.paintComponent(graphics);

    if (myGraphicsLayoutRenderer == null && myDocument != null) {
      if (myDumbService.isDumb()) {
        // We avoid inflating views when running on dumb mode. Layoutlib might try to access the PSI and it would fail.
        myDumbService.runWhenSmart(new Runnable() {
          @Override
          public void run() {
            repaint();
          }
        });
        return;
      }

      ILayoutPullParser parser = new DomPullParser(myDocument.getDocumentElement());
      try {
        myGraphicsLayoutRenderer =
          GraphicsLayoutRenderer.create(myConfiguration, parser, false/*hasHorizontalScroll*/, true/*hasVerticalScroll*/);

        myGraphicsLayoutRenderer.setScale(myScale);
        myGraphicsLayoutRenderer.setSize(getSize().width, getSize().height);
      }
      catch (UnsupportedLayoutlibException e) {
        notifyUnsupportedLayoutlib();
      }
      catch (InitializationException e) {
        LOG.error(e);
      }
    }

    if (myGraphicsLayoutRenderer != null) {
      myGraphicsLayoutRenderer.render((Graphics2D)graphics);
      Dimension renderSize = myGraphicsLayoutRenderer.getPreferredSize();

      // We will only call revalidate (to adjust the scrollbars) if the size of the output has actually changed.
      if (!renderSize.equals(myLastRenderedSize)) {
        myLastRenderedSize = renderSize;
        revalidate();
      }
    }
  }

  private void notifyUnsupportedLayoutlib() {
    if (ourLayoutlibNotification.compareAndSet(false, true)) {
      Notifications.Bus.notify(UNSUPPORTED_LAYOUTLIB_NOTIFICATION);
    }
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet() || myGraphicsLayoutRenderer == null) {
       return super.getPreferredSize();
    }

    return myGraphicsLayoutRenderer.getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 5;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return 10;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    // We only scroll vertically so the viewport can adjust the size to our real width.
    return true;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  /**
   * Returns the list of attribute names used to render the preview..
   */
  public Set<String> getUsedAttrs() {
    if (myGraphicsLayoutRenderer == null) {
      return Collections.emptySet();
    }

    return myGraphicsLayoutRenderer.getUsedAttrs();
  }

  public ViewInfo findViewAtPoint(Point p) {
    return myGraphicsLayoutRenderer != null ? myGraphicsLayoutRenderer.findViewAtPoint(p) : null;
  }
}
