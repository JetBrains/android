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
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.rendering.DomPullParser;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.Set;

/**
 * Generic UI component for rendering.
 */
public class AndroidPreviewPanel extends JComponent implements Scrollable {
  private static final Logger LOG = Logger.getInstance(AndroidPreviewPanel.class.getName());

  protected Document myDocument;
  private GraphicsLayoutRenderer myGraphicsLayoutRenderer;
  protected Configuration myConfiguration;

  public AndroidPreviewPanel(Configuration configuration) {
    myConfiguration = configuration;
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);

    if (myGraphicsLayoutRenderer != null) {
      myGraphicsLayoutRenderer.setSize(getSize());
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
    // Invalidate current configuration if any.
    myGraphicsLayoutRenderer = null;
  }

  @Override
  public void paintComponent(final Graphics graphics) {
    super.paintComponent(graphics);

    if (myGraphicsLayoutRenderer == null && myDocument != null) {
      try {
        ILayoutPullParser myParser = new DomPullParser(myDocument.getDocumentElement());
        myGraphicsLayoutRenderer = GraphicsLayoutRenderer.create(myConfiguration, myParser);
        myGraphicsLayoutRenderer.setSize(getSize());
      }
      catch (InitializationException e) {
        LOG.error(e);
      }
    }

    if (myGraphicsLayoutRenderer != null) {
      myGraphicsLayoutRenderer.render((Graphics2D)graphics);
    }
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
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
}
