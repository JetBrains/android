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
package com.android.tools.idea.uibuilder.surface;

import com.android.annotations.VisibleForTesting;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.android.tools.idea.rendering.RenderResult;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Responsible for painting a screen view
 */
public class ScreenViewLayer extends Layer {

  public final static Map<RenderingHints.Key, Object> HQ_RENDERING_HINTS = ImmutableMap.of(
    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR
  );

  public static final int REQUEST_SCALE_DEBOUNCE_TIME_IN_MS = 300;
  private final ScreenView myScreenView;

  /**
   * Cached scaled image
   */
  @Nullable private BufferedImage myCachedVisibleImage;
  /**
   * Cached last render result
   */
  @Nullable private RenderResult myLastRenderResult;

  private final ScheduledExecutorService myScheduledExecutorService;
  private final RescaleRunnable myRescaleRunnable = new RescaleRunnable();
  @Nullable private ScheduledFuture<?> myScheduledFuture;
  private final Rectangle myScreenViewVisibleSize = new Rectangle();
  private final Dimension myScreenViewSize = new Dimension();
  private final Rectangle myCachedScreenViewDisplaySize = new Rectangle();

  /**
   * Create a new ScreenView
   *
   * @param screenView The screenView containing the model to render
   */
  public ScreenViewLayer(@NotNull ScreenView screenView) {
    this(screenView, null);
  }

  /**
   * Create a new ScreenView using the provided executor to debounce
   * the requests for a high quality scaled image.
   * <p>
   * This method should only be used for tests
   * </p>
   *
   * @param screenView The screenView containing the model to render
   * @param executor   Executor used to debounce the calls to {@link #requestHighQualityScaledImage()}
   */
  @VisibleForTesting
  ScreenViewLayer(@NotNull ScreenView screenView, @Nullable ScheduledExecutorService executor) {
    myScreenView = screenView;
    myScheduledExecutorService = executor != null ? executor : Executors.newScheduledThreadPool(1);
    Disposer.register(screenView.getSurface(), this);
  }

  /**
   * Renders a preview image trying to reuse the existing buffer when possible.
   */
  @NotNull
  private static BufferedImage getPreviewImage(@NotNull GraphicsConfiguration configuration,
                                               @NotNull ImagePool.Image renderedImage,
                                               int screenViewX, int screenViewY,
                                               @NotNull Rectangle screenViewVisibleSize,
                                               double xScaleFactor, double yScaleFactor,
                                               @Nullable BufferedImage existingBuffer) {
    // Extract from the result image only the visible rectangle. The result image might be bigger or smaller than the actual ScreenView
    // size so we need to also rescale.
    int sx1 = (int)Math.round((screenViewVisibleSize.x - screenViewX) * xScaleFactor);
    int sy1 = (int)Math.round((screenViewVisibleSize.y - screenViewY) * yScaleFactor);
    int sx2 = sx1 + (int)Math.round(screenViewVisibleSize.width * xScaleFactor);
    int sy2 = sy1 + (int)Math.round(screenViewVisibleSize.height * yScaleFactor);
    BufferedImage image;
    if (existingBuffer != null &&
        existingBuffer.getWidth() == screenViewVisibleSize.width &&
        existingBuffer.getHeight() == screenViewVisibleSize.height) {
      image = existingBuffer;
    }
    else {
      image = configuration.createCompatibleImage(screenViewVisibleSize.width, screenViewVisibleSize.height, Transparency.TRANSLUCENT);
      assert image != null;
    }
    Graphics2D cacheImageGraphics = image.createGraphics();
    cacheImageGraphics.setRenderingHints(HQ_RENDERING_HINTS);
    renderedImage.drawImageTo(cacheImageGraphics,
                                   0, 0, image.getWidth(), image.getHeight(),
                                   sx1, sy1, sx2, sy2);
    cacheImageGraphics.dispose();

    return image;
  }

  @Override
  public void paint(@NotNull Graphics2D graphics2D) {
    myScreenView.getSize(myScreenViewSize);
    // Calculate the portion of the screen view that it's visible
    myScreenViewVisibleSize.setBounds(myScreenView.getX(), myScreenView.getY(),
                                      myScreenViewSize.width, myScreenViewSize.height);
    Rectangle2D.intersect(myScreenViewVisibleSize, graphics2D.getClipBounds(), myScreenViewVisibleSize);
    if (myScreenViewVisibleSize.isEmpty()) {
      return;
    }

    // In some cases, we will try to re-use the previous image to paint on top of it, assuming that it still matches the right dimensions.
    // This way we can save the allocation.
    BufferedImage previousVisibleImage = null;
    RenderResult renderResult = myScreenView.getResult();
    if (renderResultHasChanged(renderResult)) {
      myLastRenderResult = renderResult;
      previousVisibleImage = myCachedVisibleImage;
      myCachedVisibleImage = null;
    }

    if (myLastRenderResult == null) {
      return;
    }

    Graphics2D g = (Graphics2D) graphics2D.create();
    Shape screenShape = myScreenView.getScreenShape();
    if (screenShape != null) {
      g.clip(screenShape);
    }

    BufferedImage cachedVisibleImage = myCachedVisibleImage;
    if (cachedVisibleImage == null || !myScreenViewVisibleSize.equals(myCachedScreenViewDisplaySize)) {
      int resultImageWidth = myLastRenderResult.getRenderedImage().getWidth();
      int resultImageHeight = myLastRenderResult.getRenderedImage().getHeight();

      myCachedScreenViewDisplaySize.setBounds(myScreenViewVisibleSize);
      // Obtain the factors to convert from screen view coordinates to our result image coordinates
      double xScaleFactor = (double)resultImageWidth / myScreenViewSize.width;
      double yScaleFactor = (double)resultImageHeight / myScreenViewSize.height;
      if (xScaleFactor > 1.0 && yScaleFactor > 1.0) {
        // This means that the result image is bigger than the ScreenView by more than a 20%. For this cases, we need to scale down the
        // result image to make it fit in the ScreenView and we use a higher quality (but slow) process. We will issue a request to obtain
        // the high quality version but paint the low quality version below. Once it's ready, we'll repaint.
        requestHighQualityScaledImage();
      }

      cachedVisibleImage = getPreviewImage(g.getDeviceConfiguration(),
                                             myLastRenderResult.getRenderedImage(),
                                             myScreenView.getX(), myScreenView.getY(),
                                             myScreenViewVisibleSize, xScaleFactor, yScaleFactor,
                                             previousVisibleImage);
      myCachedVisibleImage = cachedVisibleImage;

    }
    UIUtil.drawImage(g, cachedVisibleImage, myScreenViewVisibleSize.x, myScreenViewVisibleSize.y, null);
    g.dispose();
  }

  /**
   * Check whether the provided render result is the same than the previous one
   *
   * @param renderResult The renderResult from {@link NlModel#getRenderResult()}
   * @return false if renderResult is null or the same as the previous one or if no image is available, true otherwise
   */
  private boolean renderResultHasChanged(@Nullable RenderResult renderResult) {
    return renderResult != null && renderResult.hasImage() && renderResult != myLastRenderResult;
  }

  private void cancelHighQualityScaleRequests() {
    if (myScheduledFuture != null && !myScheduledFuture.isDone()) {
      myScheduledFuture.cancel(false);
    }
  }

  /**
   * Request to run the {@link RescaleRunnable} to execute the scale of {@link #myImage} in background.
   * <p>
   * The calls to this methods are debounced using a {@link ScheduledExecutorService}.
   * This means that the {@link RescaleRunnable} will only be executed after a delay
   * of {@value #REQUEST_SCALE_DEBOUNCE_TIME_IN_MS}ms (set in {@link #REQUEST_SCALE_DEBOUNCE_TIME_IN_MS})
   * and the delay is reset if another call occurs in this delay
   * </p>
   * <pre>
   *    calls:  ---|-|--|------>
   *    delay:      = == ====--->
   *                           | actual call to {@link RescaleRunnable#run()}
   * </pre>
   */
  private void requestHighQualityScaledImage() {
    cancelHighQualityScaleRequests();
    try {
      myScheduledFuture = myScheduledExecutorService.schedule(myRescaleRunnable, REQUEST_SCALE_DEBOUNCE_TIME_IN_MS, TimeUnit.MILLISECONDS);
    }
    catch (RejectedExecutionException e) {
      // Catch the potential race condition where we've disposed the ScreenViewLayer but we have requested a high quality scaled image
      Logger.getInstance(ScreenViewLayer.class).warn(e);
    }
  }

  @Override
  public void dispose() {
    super.dispose();
    myLastRenderResult = null;
    myScheduledExecutorService.shutdown();
  }

  @Nullable
  static BufferedImage scaleOriginalImage(@Nullable GraphicsConfiguration gc,
                                  @NotNull ImagePool.Image image,
                                  @NotNull ScreenView screenView,
                                  @NotNull Dimension screenViewSize,
                                  @NotNull Rectangle screenViewVisibleSize) {
    // Obtain the factors to convert from screen view coordinates to our result image coordinates
    double xScaleFactor = (double)image.getWidth() / screenViewSize.width;
    double yScaleFactor = (double)image.getHeight() / screenViewSize.height;

    // Extract from the result image only the visible rectangle. The result image might be bigger or smaller than the actual ScreenView
    // size so we need to also rescale.
    int sx = (int)Math.round((screenViewVisibleSize.x - screenView.getX()) * xScaleFactor);
    int sy = (int)Math.round((screenViewVisibleSize.y - screenView.getY()) * yScaleFactor);
    int sw = (int)Math.round(screenViewVisibleSize.width * xScaleFactor);
    int sh = (int)Math.round(screenViewVisibleSize.height * yScaleFactor);

    BufferedImage imageCopy = gc != null ? image.getCopy(gc, sx, sy, sw, sh) : image.getCopy(sx, sy, sw, sh);
    if (imageCopy == null) {
      return null;
    }

    BufferedImage scaledImage = null;
    if (UIUtil.isRetina() && ImageUtils.supportsRetina()) {
      scaledImage = getRetinaScaledImage(imageCopy, 1 / xScaleFactor, 1 / yScaleFactor, false);
    }
    if (scaledImage == null) {
      scaledImage = ImageUtils.scale(imageCopy, 1 / xScaleFactor, 1 / yScaleFactor);
    }
    return scaledImage;
  }

  /**
   * Implementation of {@link Runnable} to do a high quality scaling of {@link RenderResult} image in background.
   * When the scaling is done, the {@link DesignSurface} will be repainted.
   *
   * @see ImageUtils#scale(BufferedImage, double)
   */
  private class RescaleRunnable implements Runnable {
    @Nullable private final GraphicsConfiguration myGc;

    private RescaleRunnable() {
      if (!ApplicationManager.getApplication().isHeadlessEnvironment()) {
        // Save the graphics context to create image copies that match the screen configuration. This
        // way, we avoid Swing making additional copies of large bitmaps to transform them to the right
        // configuration.
        myGc = GraphicsEnvironment.getLocalGraphicsEnvironment()
                                  .getDefaultScreenDevice()
                                  .getDefaultConfiguration();
      }
      else {
        myGc = null;
      }
    }

    @Override
    public void run() {
      myCachedVisibleImage = null;
      if (myLastRenderResult == null) {
        return;
      }

      myCachedVisibleImage = scaleOriginalImage(myGc,
                         myLastRenderResult.getRenderedImage(),
                         myScreenView,
                         myScreenViewSize,
                         myScreenViewVisibleSize);

      UIUtil.invokeLaterIfNeeded(
        () -> myScreenView.getSurface().repaint());
    }
  }

  @Nullable
  private static BufferedImage getRetinaScaledImage(@NotNull BufferedImage original,
                                                    double scaleX,
                                                    double scaleY,
                                                    boolean fastScaling) {
    // No scaling if very close to 1.0 (we check for 0.5 since we're doubling the output)
    double xRetinaScale = 2 * scaleX;
    double yRetinaScale = 2 * scaleY;

    if (fastScaling) {
      original = ImageUtils.lowQualityFastScale(original, xRetinaScale, yRetinaScale);
    }
    else {
      original = ImageUtils.scale(original, xRetinaScale, yRetinaScale);
    }

    return ImageUtils.convertToRetina(original);
  }
}
