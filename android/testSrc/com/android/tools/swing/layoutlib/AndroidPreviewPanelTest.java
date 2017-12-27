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
package com.android.tools.swing.layoutlib;

import com.android.ide.common.rendering.api.SessionParams;
import com.android.ide.common.rendering.api.ViewInfo;
import com.android.tools.idea.configurations.Configuration;
import com.android.tools.idea.configurations.ConfigurationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.AndroidTestCase;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.android.tools.adtui.imagediff.ImageDiffUtil.assertImageSimilar;

public class AndroidPreviewPanelTest extends AndroidTestCase {
  public void testSimpleRender() throws ParserConfigurationException, IOException, SAXException, InterruptedException {
    VirtualFile layout = myFixture.copyFileToProject("themeEditor/theme_preview_layout.xml", "res/layout/theme_preview_layout.xml");
    Configuration configuration = ConfigurationManager.getOrCreateInstance(myModule).getConfiguration(layout);
    AtomicBoolean executorCalled = new AtomicBoolean(false);
    ExecutorService threadPool = Executors.newFixedThreadPool(1);
    Executor executor = (r) -> {
      // Run in a separate thread and wait for the result
      try {
        threadPool.submit(r).get(60, TimeUnit.SECONDS);
        executorCalled.set(true);
      }
      catch (InterruptedException | ExecutionException | TimeoutException e) {
        throw new AssertionError("Unexpected exception", e);
      }
    };

    AndroidPreviewPanel.GraphicsLayoutRendererFactory graphicsFactory = (configuration1, parser, background) -> {
      Module module = configuration.getModule();
      AndroidFacet facet = AndroidFacet.getInstance(configuration.getModule());
      assertNotNull(facet);
      AndroidPlatform platform = AndroidPlatform.getInstance(module);
      assertNotNull(platform);

      return GraphicsLayoutRenderer
        .create(facet, platform, getProject(), configuration, parser, background, SessionParams.RenderingMode.V_SCROLL, false);
    };

    AndroidPreviewPanel panel = new AndroidPreviewPanel(configuration, executor, graphicsFactory) {
      @Override
      public void invalidateGraphicsRenderer() {
        myInvalidateRunnable.run();
      }
    };

    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(true);
    Document document = factory.newDocumentBuilder().parse(
      new InputSource(new StringReader(
        "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"" +
        "     android:layout_width=\"match_parent\"" +
        "     android:layout_height=\"match_parent\"" +
        "     android:background=\"#0F0\">" +
        "     <TextView" +
        "       android:layout_width=\"wrap_content\"" +
        "       android:layout_height=\"wrap_content\"" +
        "       android:text=\"Hello world\"" +
        "       android:background=\"#F00\"/>" +
        "</LinearLayout>"))
    );

    panel.setDocument(document);
    panel.setBounds(0, 0, 400, 100);
    //noinspection UndesirableClassUsage (we don't want to use HDPI images here)
    BufferedImage output = new BufferedImage(400, 100, BufferedImage.TYPE_INT_ARGB);
    panel.paintComponent(output.getGraphics());
    // Make sure that the executor got called since we were trying to render on the UI thread.
    assertTrue(executorCalled.get());
    BufferedImage goldenImage = ImageIO.read(new File(getTestDataPath() + "/themeEditor/previewPanel/golden.png"));
    assertImageSimilar("layout", goldenImage, output, 3);

    ViewInfo textView = panel.findViewAtPoint(new Point(0, 0));
    assertEquals("android.widget.TextView", textView.getClassName());

    threadPool.shutdownNow();
    threadPool.awaitTermination(30, TimeUnit.SECONDS);
    Disposer.dispose(panel);
  }
}
