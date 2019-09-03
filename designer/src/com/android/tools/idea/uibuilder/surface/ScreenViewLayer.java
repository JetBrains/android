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

import com.google.common.annotations.VisibleForTesting;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.common.model.NlModel;
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.rendering.imagepool.ImagePool;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Disposer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.ui.scale.ScaleContext;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Consumer;

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
  private final RescaleRunnable myRescaleRunnable = new RescaleRunnable(this::onScaledResultReady);
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
   * @param executor   Executor used to debounce the calls to {@link #requestHighQualityScaledImage}
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
                                               @NotNull BufferedImage renderedImage,
                                               int screenViewX, int screenViewY,
                                               @NotNull Rectangle screenViewVisibleSize,
                                               double xScaleFactor, double yScaleFactor,
                                               @Nullable BufferedImage existingBuffer,
                                               boolean screenViewHasBorderLayer) {
    // Extract from the result image only the visible rectangle. The result image might be bigger or smaller than the actual ScreenView
    // size so we need to also rescale.
    int sx1 = (int)Math.round((screenViewVisibleSize.x - screenViewX) * xScaleFactor);
    int sy1 = (int)Math.round((screenViewVisibleSize.y - screenViewY) * yScaleFactor);
    int sx2 = sx1 + (int)Math.round(screenViewVisibleSize.width * xScaleFactor);
    int sy2 = sy1 + (int)Math.round(screenViewVisibleSize.height * yScaleFactor);
    BufferedImage image;
    boolean bufferWithScreenViewSizeExists = existingBuffer != null && existingBuffer.getWidth() == screenViewVisibleSize.width
                                             && existingBuffer.getHeight() == screenViewVisibleSize.height;
    if (screenViewHasBorderLayer && bufferWithScreenViewSizeExists) {
      // Reuse the buffered image if the screen view visible size matches the existing buffer's, and if we're rendering a screen view that
      // has a border layer. Screen views without a border layer might contain transparent images, e.g. a drawable, and reusing the buffer
      // might cause the unexpected effect of parts of the old image being rendered on the transparent parts of the new one. Therefore, we
      // need to force the creation of a new image in this case.
      image = existingBuffer;
    }
    else {
      image = configuration.createCompatibleImage(screenViewVisibleSize.width, screenViewVisibleSize.height, Transparency.TRANSLUCENT);
      assert image != null;
    }
    Graphics2D cacheImageGraphics = image.createGraphics();
    cacheImageGraphics.setRenderingHints(HQ_RENDERING_HINTS);
    cacheImageGraphics.drawImage(renderedImage, 0, 0, image.getWidth(), image.getHeight(), sx1, sy1, sx2, sy2, null);
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
    BufferedImage previousVisibleImage = myCachedVisibleImage;
    RenderResult renderResult = myScreenView.getResult();
    boolean drawNewImg = false;
    if (newRenderImageAvailable(renderResult)) {
      myLastRenderResult = renderResult;
      drawNewImg = true;
    }

    Graphics2D g = (Graphics2D) graphics2D.create();
    BufferedImage cachedVisibleImage = drawNewImg ? null : previousVisibleImage;
    if (drawNewImg || !myScreenViewVisibleSize.equals(myCachedScreenViewDisplaySize)) {
      if (myLastRenderResult != null && myLastRenderResult.hasImage()) {
        BufferedImage renderedImage = myLastRenderResult.getRenderedImage().getCopy();
        assert renderedImage != null : "Image was already disposed";
        int resultImageWidth = renderedImage.getWidth();
        int resultImageHeight = renderedImage.getHeight();

        myCachedScreenViewDisplaySize.setBounds(myScreenViewVisibleSize);
        // Obtain the factors to convert from screen view coordinates to our result image coordinates
        double xScaleFactor = (double)resultImageWidth / myScreenViewSize.width;
        double yScaleFactor = (double)resultImageHeight / myScreenViewSize.height;
        cancelHighQualityScaleRequests();
        if (Math.abs(1 - xScaleFactor) > 0.2 && Math.abs(1 - yScaleFactor) > 0.2) {
          // This means that the result image is bigger than the ScreenView by more than a 20%. For this cases, we need to scale down the
          // result image to make it fit in the ScreenView and we use a higher quality (but slow) process. We will issue a request to obtain
          // the high quality version but paint the low quality version below. Once it's ready, we'll repaint.

          requestHighQualityScaledImage(ScaleContext.create(g));
        }

        cachedVisibleImage = getPreviewImage(g.getDeviceConfiguration(), renderedImage,
                                             myScreenView.getX(), myScreenView.getY(),
                                             myScreenViewVisibleSize, xScaleFactor, yScaleFactor,
                                             previousVisibleImage, myScreenView.hasBorderLayer());
        myCachedVisibleImage = cachedVisibleImage;
      }
    }

    if (cachedVisibleImage != null) {
      Shape screenShape = myScreenView.getScreenShape();
      if (screenShape != null) {
        g.clip(screenShape);
      }
      // b/140428773 : Graphics.drawImage returns even it is not completed. Fill the default color here to avoid un-painted image.
      g.fillRect(myScreenViewVisibleSize.x, myScreenViewVisibleSize.y, myScreenViewVisibleSize.width, myScreenViewVisibleSize.height);
      UIUtil.drawImage(g, cachedVisibleImage, myScreenViewVisibleSize.x, myScreenViewVisibleSize.y, null);
    }
    g.dispose();
  }

  /**
   * Check whether the provided render result has new image to draw.
   *
   * @param renderResult The renderResult from {@link NlModel#getRenderResult()}
   * @return false if renderResult is null or the same as the previous one or if no image is available, true otherwise
   */
  private boolean newRenderImageAvailable(@Nullable RenderResult renderResult) {
    return renderResult != null && renderResult.hasImage() && renderResult != myLastRenderResult;
  }

  private void cancelHighQualityScaleRequests() {
    if (myScheduledFuture != null && !myScheduledFuture.isDone()) {
      myScheduledFuture.cancel(true);
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
   * @param ctx ScaleContext used to get the scaling of the physical screen this is displayed on.
   *            This is to support HiDPI screens with various scalings.
   */
  private void requestHighQualityScaledImage(@NotNull ScaleContext ctx) {
    if (myLastRenderResult == null) {
      return;
    }

    ImagePool.Image image = myLastRenderResult.getRenderedImage();
    // Obtain the factors to convert from screen view coordinates to our result image coordinates
    double xScaleFactor = (double)image.getWidth() / myScreenViewSize.width;
    double yScaleFactor = (double)image.getHeight() / myScreenViewSize.height;

    // Extract from the result image only the visible rectangle. The result image might be bigger or smaller than the actual ScreenView
    // size so we need to also rescale.
    int sx = (int)Math.round((myScreenViewVisibleSize.x - myScreenView.getX()) * xScaleFactor);
    int sy = (int)Math.round((myScreenViewVisibleSize.y - myScreenView.getY()) * yScaleFactor);
    int sw = (int)Math.round(myScreenViewVisibleSize.width * xScaleFactor);
    int sh = (int)Math.round(myScreenViewVisibleSize.height * yScaleFactor);

    if (sx + sw > image.getWidth()) {
      sw = image.getWidth() - sx;
    }
    if (sy + sh > image.getHeight()) {
      sh = image.getHeight() - sy;
    }

    if (sw <= 0 || sh <= 0) {
      Logger.getInstance(ScreenViewLayer.class).warn(
        String.format("requestHighQualityScaledImage with invalid size (sw=%d, sh=%d)", sw, sh));
      return;
    }

    BufferedImage imageCopy = image.getCopy(sx, sy, sw, sh);
    if (imageCopy == null) {
      return;
    }

    myRescaleRunnable.setSource(imageCopy, xScaleFactor, yScaleFactor, ctx);
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
  private static BufferedImage getRetinaScaledImage(@NotNull BufferedImage original,
                                                    double scaleX,
                                                    double scaleY,
                                                    @NotNull ScaleContext ctx,
                                                    boolean fastScaling) {
    // No scaling if very close to 1.0 (we check for 0.5 since we're doubling the output)
    double xRetinaScale = JBUIScale.sysScale(ctx) * scaleX;
    double yRetinaScale = JBUIScale.sysScale(ctx) * scaleY;

    if (fastScaling) {
      original = ImageUtils.lowQualityFastScale(original, xRetinaScale, yRetinaScale);
    }
    else {
      original = ImageUtils.scale(original, xRetinaScale, yRetinaScale);
    }

    return ImageUtils.convertToRetina(original, ctx);
  }

  @VisibleForTesting
  @NotNull
  static BufferedImage scaleOriginalImage(@NotNull BufferedImage source,
                                          double xScaleFactor,
                                          double yScaleFactor,
                                          @NotNull ScaleContext ctx) {
    BufferedImage scaledImage = null;
    if (UIUtil.isJreHiDPI(ctx) && ImageUtils.supportsRetina()) {
      scaledImage = getRetinaScaledImage(source, 1 / xScaleFactor, 1 / yScaleFactor, ctx, false);
    }
    if (scaledImage == null) {
      scaledImage = ImageUtils.scale(source, 1 / xScaleFactor, 1 / yScaleFactor);
    }
    return scaledImage;
  }

  /**
   * Implementation of {@link Runnable} to do a high quality scaling of {@link RenderResult} image in background.
   * When the scaling is done, the {@link DesignSurface} will be repainted.
   *
   * @see ImageUtils#scale(BufferedImage, double)
   */
  private static class RescaleRunnable implements Runnable {
    @NotNull private final Consumer<BufferedImage> myOnReadyCallback;
    private final Object lock = new Object();
    private BufferedImage mySourceImage;
    private double myXScaleFactor;
    private double myYScaleFactor;
    private ScaleContext myScaleContext;


    private RescaleRunnable(@NotNull Consumer<BufferedImage> onReadyCallback) {
      myOnReadyCallback = onReadyCallback;
    }

    public void setSource(@NotNull BufferedImage sourceImage, double xScaleFactor, double yScaleFactor, @NotNull ScaleContext ctx) {
      synchronized (lock) {
        mySourceImage = sourceImage;
        myXScaleFactor = xScaleFactor;
        myYScaleFactor = yScaleFactor;
        myScaleContext = ctx;
      }
    }

    @Override
    public void run() {
      BufferedImage source;
      double xScaleFactor;
      double yScaleFactor;
      ScaleContext ctx;
      synchronized (lock) {
        source = mySourceImage;
        xScaleFactor = myXScaleFactor;
        yScaleFactor = myYScaleFactor;
        ctx = myScaleContext;
        mySourceImage = null;
      }

      if (source == null) {
        return;
      }
      BufferedImage result = scaleOriginalImage(source, xScaleFactor, yScaleFactor, ctx);
      myOnReadyCallback.accept(result);
    }
  }

  private void onScaledResultReady(BufferedImage result) {
    myCachedVisibleImage = result;
    UIUtil.invokeLaterIfNeeded(
      () -> myScreenView.getSurface().repaint());
  }
}
