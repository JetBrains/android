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

import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.SwingWorker;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Generic UI component for rendering.
 */
public class AndroidPreviewPanel extends JComponent implements Scrollable {
  private static final Logger LOG = Logger.getInstance(AndroidPreviewPanel.class);

  public static final int VERTICAL_SCROLLING_UNIT_INCREMENT = 5;
  public static final int VERTICAL_SCROLLING_BLOCK_INCREMENT = 10;

  // The two booleans are used to control the flow of pending invalidates and to avoid creating
  // any unnecessary tasks.
  // If myRunningInvalidates is true, a task is currently running so no other task will be started.
  // If myPendingInvalidates is true, an invalidateGraphicsRenderer call has been issued. This allows
  // the current task to do another invalidate once it has finished the current one.
  private final AtomicBoolean myRunningInvalidates = new AtomicBoolean(false);
  private final AtomicBoolean myPendingInvalidates = new AtomicBoolean(false);
  private final Runnable myInvalidateRunnable = new Runnable() {
    @Override
    public void run() {
      ILayoutPullParser parser = new DomPullParser(myDocument.getDocumentElement());
      try {
        synchronized (myGraphicsLayoutRendererLock) {
          // The previous GraphicsLayoutRenderer needs to be disposed before we create a new one since there is static state that
          // can not be shared.
          if (myGraphicsLayoutRenderer != null) {
            myGraphicsLayoutRenderer.dispose();
            myGraphicsLayoutRenderer = null;
          }
        }

        GraphicsLayoutRenderer graphicsLayoutRenderer = GraphicsLayoutRenderer
          .create(myConfiguration, parser, getBackground(), false/*hasHorizontalScroll*/, true/*hasVerticalScroll*/);
        graphicsLayoutRenderer.setScale(myScale);
        // We reset the height so that it can be recomputed to the needed value.
        graphicsLayoutRenderer.setSize(getWidth(), 1);

        synchronized (myGraphicsLayoutRendererLock) {
          myGraphicsLayoutRenderer = graphicsLayoutRenderer;
        }
      }
      catch (UnsupportedLayoutlibException e) {
        notifyUnsupportedLayoutlib();
      }
      catch (InitializationException e) {
        LOG.error(e);
      }
    }
  };

  private class InvalidateTask extends SwingWorker<Void, Void> {
    @Override
    protected Void doInBackground() throws Exception {
      do {
        myRunningInvalidates.set(true);
        myPendingInvalidates.set(false);

        // We can only inflate views when the project has been indexed
        myDumbService.runReadActionInSmartMode(myInvalidateRunnable);

        myRunningInvalidates.set(false);
      } while (myPendingInvalidates.get());

      return null;
    }

    @Override
    protected void done() {
      repaint();
    }
  }

  private static final Notification UNSUPPORTED_LAYOUTLIB_NOTIFICATION =
    new Notification("Android", "Layoutlib", "The Theme Editor preview requires at least Android M Preview SDK", NotificationType.ERROR);

  private static final AtomicBoolean ourLayoutlibNotification = new AtomicBoolean(false);

  private final DumbService myDumbService;
  private final Object myGraphicsLayoutRendererLock = new Object();
  private GraphicsLayoutRenderer myGraphicsLayoutRenderer;
  private Configuration myConfiguration;
  private Document myDocument;
  private double myScale = 1.0;
  private Dimension myLastRenderedSize;
  private Dimension myCachedPreferredSize;
  private int myCurrentWidth;

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
    synchronized (myGraphicsLayoutRendererLock) {
      if (myGraphicsLayoutRenderer != null && !currentSize.equals(previousSize)) {
        // Because we use GraphicsLayoutRender in vertical scroll mode, the height passed it's only a minimum.
        // If the actual rendering results in a bigger size, the GraphicsLayoutRenderer.getPreferredSize()
        // call will return the correct size.

        // Older versions of layoutlib do not handle correctly when 1 is passed and don't always recalculate
        // the height if the width hasn't decreased.
        // We workaround that by keep track of the last known width and passing height 1 when it decreases.
        myGraphicsLayoutRenderer.setSize(width, (myCurrentWidth < width) ? 1 : height);
        myCurrentWidth = width;
      }
    }
  }

  public void setScale(double scale) {
    myScale = scale;
    synchronized (myGraphicsLayoutRendererLock) {
      if (myGraphicsLayoutRenderer != null) {
        myGraphicsLayoutRenderer.setScale(scale);
      }
    }
  }

  public void invalidateGraphicsRenderer() {
    if (myDocument != null) {
      myPendingInvalidates.set(true);
      if (!myRunningInvalidates.get()) {
        new InvalidateTask().execute();
      }
    }
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
    synchronized (myGraphicsLayoutRendererLock) {
      if (myGraphicsLayoutRenderer != null) {
        myGraphicsLayoutRenderer.render((Graphics2D)graphics);
        Dimension renderSize = myGraphicsLayoutRenderer.getPreferredSize();

        // We will only call revalidate (to adjust the scrollbars) if the size of the output has actually changed. This can happen after
        // the side of the panel has been changed. The new render will have a different size.
        if (!renderSize.equals(myLastRenderedSize)) {
          myLastRenderedSize = renderSize;
          revalidate();
        }
      }
    }
  }

  private static void notifyUnsupportedLayoutlib() {
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
    synchronized (myGraphicsLayoutRendererLock) {
      if (isPreferredSizeSet() || myGraphicsLayoutRenderer == null) {
        return myCachedPreferredSize == null ? super.getPreferredSize() : myCachedPreferredSize;
      }

      myCachedPreferredSize = myGraphicsLayoutRenderer.getPreferredSize();
      return myCachedPreferredSize;
    }
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return VERTICAL_SCROLLING_UNIT_INCREMENT;
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    return VERTICAL_SCROLLING_BLOCK_INCREMENT;
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
    synchronized (myGraphicsLayoutRendererLock) {
      if (myGraphicsLayoutRenderer == null) {
        return Collections.emptySet();
      }

      return myGraphicsLayoutRenderer.getUsedAttrs();
    }
  }

  public ViewInfo findViewAtPoint(Point p) {
    synchronized (myGraphicsLayoutRendererLock) {
      return myGraphicsLayoutRenderer != null ? myGraphicsLayoutRenderer.findViewAtPoint(p) : null;
    }
  }

  @Override
  public void setBackground(Color bg) {
    super.setBackground(bg);

    invalidateGraphicsRenderer();
  }
}
