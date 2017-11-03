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
package com.android.tools.swing.layoutlib;

import com.android.ide.common.rendering.api.SessionParams;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.android.tools.idea.rendering.DomPullParser;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static org.junit.Assert.assertNotEquals;

public class GraphicsLayoutRendererTest extends AndroidTestCase {
  private static Dimension EMPTY_DIMENSION = new Dimension();

  public void testInflateAndRender() throws InitializationException, ParserConfigurationException, IOException, SAXException {
    VirtualFile layout = myFixture.copyFileToProject("themeEditor/theme_preview_layout.xml", "res/layout/theme_preview_layout.xml");
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout);

    DomPullParser parser = new DomPullParser(
      DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(layout.getInputStream()).getDocumentElement());

    AndroidFacet facet = AndroidFacet.getInstance(configuration.getModule());
    assertNotNull(facet);
    AndroidPlatform platform = AndroidPlatform.getInstance(myModule);
    assertNotNull(platform);
    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
    GraphicsLayoutRenderer renderer = GraphicsLayoutRenderer.create(
      facet, platform, myModule.getProject(), configuration, parser, Color.BLACK, SessionParams.RenderingMode.V_SCROLL, false);

    // The first render triggers a render (to a NOP Graphics object) so we expect sizes to have been initialized.
    Dimension initialSize = renderer.getPreferredSize();
    assertNotEquals("Expected layout dimensions after create", EMPTY_DIMENSION, initialSize);
    assertTrue(renderer.render((Graphics2D)image.getGraphics()));

    // We haven't changed the layout so, after the render, we expect the same dimensions.
    assertEquals(initialSize, renderer.getPreferredSize());

    renderer.setSize(new Dimension(50, 50));
    assertTrue(renderer.render((Graphics2D)image.getGraphics()));
  }
}