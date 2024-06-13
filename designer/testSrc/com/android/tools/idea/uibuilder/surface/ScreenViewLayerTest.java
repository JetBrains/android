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

import static com.android.tools.idea.DesignSurfaceTestUtil.createZoomControllerFake;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.ide.common.rendering.api.Result;
import com.android.testutils.ImageDiffUtil;
import com.android.tools.idea.uibuilder.scene.LayoutlibSceneManager;
import com.android.tools.idea.uibuilder.visual.colorblindmode.ColorBlindMode;
import com.android.tools.rendering.RenderResult;
import com.android.tools.rendering.imagepool.ImagePool;
import com.android.tools.rendering.imagepool.ImagePoolFactory;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.NotNull;
import org.junit.After;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;

public class ScreenViewLayerTest {

  public static final int SCREEN_VIEW_WIDTH = 200;
  public static final int SCREEN_VIEW_HEIGHT = 200;
  public static final int IMAGE_WIDTH = 500;
  public static final int IMAGE_HEIGHT = 500;
  public static final double SCALE = SCREEN_VIEW_HEIGHT / (double)IMAGE_HEIGHT;
  private static final Rectangle FULL_SIZE = new Rectangle(IMAGE_WIDTH, IMAGE_HEIGHT);

  private Disposable myDisposable =  Disposer.newDisposable();

  @After
  public void tearDown() {
    Disposer.dispose(myDisposable);
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

  @NotNull
  private static RenderResult createRenderResultMock(@NotNull ImagePool.Image resultImage) {
    RenderResult result = mock(RenderResult.class);
    when(result.getRenderedImage()).thenReturn(resultImage);
    when(result.getRenderResult()).thenReturn(Result.Status.SUCCESS.createResult());

    return result;
  }

  @NotNull
  private ScreenView createScreenViewMock(@NotNull Ref<Rectangle> screenViewLayerSize,
                                          @NotNull RenderResult firstResult,
                                          @NotNull RenderResult ...otherResults) {
    LayoutlibSceneManager sceneManager = mock(LayoutlibSceneManager.class, RETURNS_DEEP_STUBS);
    NlDesignSurface designSurface = mock();
    when(designSurface.getZoomController()).thenReturn(createZoomControllerFake());
    ScreenView screenView = Mockito.spy(
      new ScreenView(designSurface, sceneManager, (screenView1, outDimension) -> {
      }));
    when(screenView.getScreenShape()).thenAnswer((Answer<Shape>)invocation -> screenViewLayerSize.get());
    when(screenView.getScale()).thenReturn(1.);
    when(screenView.getContentSize(any(Dimension.class))).thenAnswer((Answer<Dimension>)invocation -> {
      Dimension returnDimension = (Dimension)invocation.getArguments()[0];

      if (returnDimension != null) {
        returnDimension.setSize(screenViewLayerSize.get().getSize());
      }
      else {
        returnDimension = screenViewLayerSize.get().getSize();
      }

      return returnDimension;
    });

    when(screenView.getResult()).thenReturn(firstResult, otherResults);

    Disposer.register(myDisposable, screenView.getSurface());
    Disposer.register(myDisposable, sceneManager);
    Disposer.register(sceneManager, screenView);

    return screenView;
  }

  @SuppressWarnings("UndesirableClassUsage")
  @Test
  public void paintTest() throws Exception {
    Ref<Rectangle> screenViewSize = new Ref<>(scaleRectangle(FULL_SIZE, SCALE));

    // Create a high quality image bigger than the screenView that will be scaled.
    ImagePool.Image imageHQ = getTestImage(IMAGE_WIDTH, IMAGE_HEIGHT);
    ScreenView screenView = createScreenViewMock(screenViewSize, createRenderResultMock(imageHQ));
    ScreenViewLayer layer = new ScreenViewLayer(screenView, ColorBlindMode.NONE, screenView.getSurface(), screenView.getSurface()::getRotateSurfaceDegree);

    // First, we expect the low quality scaling in the first call.
    BufferedImage unscaled = new BufferedImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D)unscaled.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    imageHQ.drawImageTo(g, 0, 0, SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT);

    BufferedImage output = new BufferedImage(SCREEN_VIEW_WIDTH, SCREEN_VIEW_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    g = createGraphicsAndClean(output, screenViewSize.get());
    layer.paint(g);
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", unscaled, output, 0.0);

    // Scale value back to 1.0, so no scaling.
    screenViewSize.set(FULL_SIZE);
    unscaled = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    output = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    g = (Graphics2D)unscaled.getGraphics();
    g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    imageHQ.drawImageTo(g, 0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
    g = createGraphicsAndClean(output, screenViewSize.get());
    layer.paint(g);
    ImageDiffUtil.assertImageSimilar("screenviewlayer_result.png", unscaled, output, 0.0);
  }

  @NotNull
  private static ImagePool.Image getTestImage(int imageWidth, int imageHeight) {
    ImagePool imagePool = ImagePoolFactory.createImagePool();
    ImagePool.Image imageHQ = imagePool.create(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
    imageHQ.paint(g -> {
      g.setStroke(new BasicStroke(10));
      //noinspection UseJBColor
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, imageWidth, imageHeight);
      g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      //noinspection UseJBColor
      g.setColor(Color.BLACK);
      g.drawLine(0, 0, imageWidth, imageHeight);
      g.drawLine(imageWidth, 0, 0, imageHeight);
    });
    return imageHQ;
  }
}