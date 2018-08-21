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
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.imagepool.ImagePoolFactory;
import com.intellij.mock.MockApplicationEx;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class ScreenViewLayerTest {

  public static final int SCREEN_VIEW_WIDTH = 200;
  public static final int SCREEN_VIEW_HEIGHT = 200;
  public static final int IMAGE_WIDTH = 500;
  public static final int IMAGE_HEIGHT = 500;
  public static final double SCALE = SCREEN_VIEW_HEIGHT / (double)IMAGE_HEIGHT;
  private static final Rectangle FULL_SIZE = new Rectangle(IMAGE_WIDTH, IMAGE_HEIGHT);

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

  private static Rectangle scaleRectangle(Rectangle rect, double scale) {
    return new Rectangle(rect.x, rect.y, (int)(rect.width * scale), (int)(rect.width * scale));
  }

  /**
   * Gets the {@link Graphics2D} for the given image and cleans it up to be ready for the output.
   * It will paint the image as white and set the clip to the given size
   */
  @NotNull
  private static Graphics2D createGraphicsAndClean(@NotNull BufferedImage image, @NotNull Rectangle rectangle) {
    Graphics2D g = (Graphics2D)image.getGraphics();
    g.setColor(Color.WHITE);
    g.fill(rectangle);
    g.setClip(rectangle);

    return g;
  }

  @Test
  public void scalingPaintTest() throws Exception {
    VirtualTimeScheduler timeScheduler = new VirtualTimeScheduler();
    Ref<Rectangle> rectangle = new Ref<>(scaleRectangle(FULL_SIZE, SCALE));
    Disposable disposable = Disposer.newDisposable();
    MockApplicationEx instance = new MockApplicationEx(disposable);
    ApplicationManager.setApplication(instance, disposable);

    ScreenViewLayer layer = new ScreenViewLayer(myScreenView, timeScheduler);

    when(myScreenView.getScreenShape()).thenAnswer(new Answer<Shape>() {
      @Override
      public Shape answer(InvocationOnMock invocation) {
        return rectangle.get();
      }
    });
    when(myScreenView.getX()).thenReturn(0);
    when(myScreenView.getY()).thenReturn(0);
    when(myScreenView.getSize(any())).thenAnswer(new Answer<Dimension>() {
      @Override
      public Dimension answer(InvocationOnMock invocation) {
        Dimension returnDimension = (Dimension)invocation.getArguments()[0];

        if (returnDimension != null) {
          returnDimension.setSize(rectangle.get().getSize());
        }
        else {
          returnDimension = rectangle.get().getSize();
        }

        return returnDimension;
      }
    });
    // Create a high quality image bigger than the screenView that will be scaled.
    ImagePool.Image imageHQ = getTestImage(IMAGE_WIDTH, IMAGE_HEIGHT);

    when(myScreenView.getResult()).thenReturn(myRenderResult);
    when(myRenderResult.getRenderedImage()).thenReturn(imageHQ);
    when(myRenderResult.hasImage()).thenReturn(true);

    Graphics2D g;

    // First, we expect the low quality scaling in the first call.
    //noinspection UndesirableClassUsage
    BufferedImage unscaled = new BufferedImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    g = (Graphics2D)unscaled.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    imageHQ.drawImageTo(g, 0, 0, SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);

    //noinspection UndesirableClassUsage
    BufferedImage output = new BufferedImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    g = createGraphicsAndClean(output, rectangle.get());
    layer.paint(g);
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", unscaled, output, 0.0);

    double xScale = imageHQ.getWidth() / rectangle.get().getWidth();
    double yScale = imageHQ.getHeight() / rectangle.get().getHeight();
    BufferedImage imageHQScaled = ScreenViewLayer.scaleOriginalImage(imageHQ.getCopy(), xScale, yScale);

    BufferedImage scaledHQ = new BufferedImage(imageHQScaled.getWidth(), imageHQScaled.getHeight(), BufferedImage.TYPE_INT_ARGB);
    UIUtil.drawImage(scaledHQ.createGraphics(), imageHQScaled, 0, 0, null);

    // We wait more than the debounce delay to ensure that the next call to paint will draw an scaled image.
    timeScheduler.advanceBy(600, TimeUnit.MILLISECONDS);
    //noinspection UndesirableClassUsage
    g = createGraphicsAndClean(output, rectangle.get());
    layer.paint(g);
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", scaledHQ, output, 0.0);

    // Scale value back to 1.0, so no scaling.
    rectangle.set(FULL_SIZE);
    unscaled = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    output = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    g = (Graphics2D)unscaled.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    imageHQ.drawImageTo(g, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    g = createGraphicsAndClean(output, rectangle.get());
    layer.paint(g);
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", unscaled, output, 0.0);

    Disposer.dispose(disposable);
  }

  @NotNull
  private static ImagePool.Image getTestImage(int imageWidth, int imageHeight) {
    ImagePool imagePool = ImagePoolFactory.createImagePool();
    ImagePool.Image imageHQ = imagePool.create(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
    imageHQ.paint(g -> {
      g.setStroke(new BasicStroke(10));
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, imageWidth, imageHeight);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      g.setColor(Color.BLACK);
      g.drawLine(0, 0, imageWidth, imageHeight);
      g.drawLine(imageWidth, 0, 0, imageHeight);
    });
    return imageHQ;
  }
}