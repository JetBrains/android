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
import com.android.tools.idea.rendering.LayoutPullParserFactory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiFile;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

/**
 * UI component that renders a theme.
 */
public class AndroidThemePreviewPanel extends JComponent implements Scrollable {
  private static final Logger LOG = Logger.getInstance(AndroidThemePreviewPanel.class.getName());
  private static final String THEME_PREVIEW_LAYOUT = "/themeEditor/sample_layout.xml";

  private Document myDocument;
  private GraphicsLayoutRenderer myGraphicsLayoutRenderer;
  private ILayoutPullParser myParser;
  private Configuration myConfiguration;

  public AndroidThemePreviewPanel(Configuration configuration) {
    super();

    myConfiguration = configuration;

    myDocument = null;
    try {
      myDocument =
        DomPullParser.createNewDocumentBuilder().parse(LayoutPullParserFactory.class.getResourceAsStream(THEME_PREVIEW_LAYOUT));
    }
    catch (SAXException e) {
      LOG.error(e);
    }
    catch (IOException e) {
      LOG.error(e);
    }
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);

    if (myGraphicsLayoutRenderer != null) {
      myGraphicsLayoutRenderer.setSize(getSize());
    }
  }

  /**
   * Updates the current configuration. You need to call this method is you change the configuration and want to update the rendered view.
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
        myParser = new DomPullParser(myDocument.getDocumentElement());
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
}
