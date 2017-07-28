/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.tools.idea.uibuilder.surface;

import com.android.testutils.VirtualTimeScheduler;
import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.android.tools.idea.rendering.ImagePool;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.rendering.RenderResult;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

public class ScreenViewLayerTest {

  public static final int SCREEN_VIEW_WIDTH = 200;
  public static final int SCREEN_VIEW_HEIGHT = 200;
  public static final int IMAGE_WIDTH = 500;
  public static final int IMAGE_HEIGHT = 500;
  public static final double SCALE = SCREEN_VIEW_HEIGHT / (double)IMAGE_HEIGHT;

  @Mock(answer = Answers.RETURNS_DEEP_STUBS)
  ScreenView myScreenView;

  @Mock
  RenderResult myRenderResult;


  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @After
  public void tearDown() {
    // myScreenView registers its design surface in the disposer tree as a root.
    Disposer.dispose(myScreenView.getSurface());

    // Null out all fields, since otherwise they're retained for the lifetime of the suite (which can be long if e.g. you're running many
    // tests through IJ)
    myScreenView = null;
    myRenderResult = null;
  }

  @Test
  public void lowHighQualityPainting() throws Exception {

    VirtualTimeScheduler timeScheduler = new VirtualTimeScheduler();
    Rectangle rectangle = new Rectangle(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);
    ScreenViewLayer layer = new ScreenViewLayer(myScreenView, timeScheduler);

    when(myScreenView.getScreenShape()).thenReturn(rectangle);
    when(myScreenView.getX()).thenReturn(0);
    when(myScreenView.getY()).thenReturn(0);
    when(myScreenView.getScale()).thenReturn(SCALE, SCALE, 1.0, SCALE);//, 0.5, 3.0);
    when(myScreenView.getSize(any())).thenReturn(rectangle.getSize());

    // Create a high quality image bigger than the screenView that will be scaled
    ImagePool.Image imageHQ = getTestImage(IMAGE_WIDTH, IMAGE_HEIGHT);

    when(myScreenView.getResult()).thenReturn(myRenderResult);
    when(myRenderResult.getRenderedImage()).thenReturn(imageHQ);
    when(myRenderResult.hasImage()).thenReturn(true);

    // First, we expect the layer to draw a low quality image at the first call to paint
    // We expect an aliased image
    //noinspection UndesirableClassUsage
    BufferedImage lowQoutput = new BufferedImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D)lowQoutput.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    imageHQ.drawImageTo(g, 0, 0, SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);
    g.dispose();

    //noinspection UndesirableClassUsage
    BufferedImage output = new BufferedImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    g = (Graphics2D)output.getGraphics();

    g.setClip(rectangle);
    layer.paint(g);
    g.dispose();
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", lowQoutput, output, 0.0);


    BufferedImage imageHQScaled = imageHQ.getCopy();
    imageHQScaled = ImageUtils.scale(imageHQScaled, SCALE);
    g.dispose();

    // We wait more than the debounce delay to ensure that the next call to pain will draw a high quality image
    timeScheduler.advanceBy(600, TimeUnit.MILLISECONDS);
    //noinspection UndesirableClassUsage
    g = (Graphics2D)output.getGraphics();
    g.setColor(Color.WHITE);
    g.fill(rectangle);
    g.setClip(rectangle);
    layer.paint(g);
    g.dispose();

    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", imageHQScaled, output, 0.0);


    layer.paint(g); // Call one more time to change the value of getScale() (see mock creation)

    // The scale value changed so the image should be in low quality
    g = (Graphics2D)output.getGraphics();
    g.setColor(Color.WHITE);
    g.fill(rectangle);
    g.setClip(rectangle);
    layer.paint(g);
    g.dispose();
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", lowQoutput, output, 0.0);
  }

  @NotNull
  private static ImagePool.Image getTestImage(int imageWidth, int imageHeight) {
    ImagePool imagePool = new ImagePool();
    ImagePool.Image imageHQ = imagePool.create(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
    imageHQ.paint(g -> {
      g.setStroke(new BasicStroke(10));
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, imageWidth, imageHeight);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setColor(Color.BLACK);
      g.drawLine(0, 0, imageWidth, imageHeight);
      g.drawLine(imageWidth, 0, 0, imageHeight);
    });
    return imageHQ;
  }
}