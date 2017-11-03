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
import com.android.tools.idea.common.surface.DesignSurface;
import com.android.tools.idea.common.surface.Layer;
import com.android.tools.idea.rendering.ImagePool;
import com.android.tools.adtui.ImageUtils;
import com.android.tools.idea.rendering.RenderResult;
import com.android.tools.idea.common.model.NlModel;
import com.google.common.collect.ImmutableMap;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Responsible for painting a screen view
 */
public class ScreenViewLayer extends Layer {

  public final static Map<RenderingHints.Key, Object> HQ_RENDERING_HITS = ImmutableMap.of(
    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON,
    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY,
    RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR
  );

  public final static Map<RenderingHints.Key, Object> LQ_RENDERING_HITS = ImmutableMap.of(
    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF,
    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED
  );

  public static final int REQUEST_SCALE_DEBOUNCE_TIME_IN_MS = 300;
  private final ScreenView myScreenView;

  /**
   * The source image we scaled from
   */
  @Nullable private ImagePool.Image myImage;
  /**
   * Cached scaled image
   */
  @Nullable private BufferedImage myScaledDownImage;
  /**
   * Cached last render result
   */
  @Nullable private RenderResult myLastRenderResult;
  /**
   * The scale at which we cached the scaled image
   */
  private double myCachedScale;

  private final ScheduledExecutorService myScheduledExecutorService;
  private final RescaleRunnable myRescaleRunnable = new RescaleRunnable();
  @Nullable private ScheduledFuture<?> myScheduledFuture;
  private Rectangle mySizeRectangle = new Rectangle();
  private Dimension myScreenViewSize = new Dimension();
  private boolean myIsRescaling;

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

  @Override
  public void paint(@NotNull Graphics2D g) {
    myScreenViewSize = myScreenView.getSize(myScreenViewSize);

    mySizeRectangle.setBounds(myScreenView.getX(), myScreenView.getY(),
                              myScreenViewSize.width, myScreenViewSize.height);
    Rectangle2D.intersect(mySizeRectangle, g.getClipBounds(), mySizeRectangle);
    if (mySizeRectangle.isEmpty()) {
      return;
    }

    RenderResult renderResult = myScreenView.getResult();
    if (renderResultHasChanged(renderResult)) {
      myLastRenderResult = renderResult;
      myImage = renderResult.getRenderedImage();
      myCachedScale = -1; // reset the scale to be sure that a new scaled image is requested when the result has changed
    }

    if (myImage == null) {
      return;
    }

    Shape prevClip = null;
    Shape screenShape = myScreenView.getScreenShape();
    if (screenShape != null) {
      prevClip = g.getClip();
      g.clip(screenShape);
    }

    double scale = myScreenView.getScale();
    if (scale != myCachedScale) {
      myCachedScale = scale;
      myScaledDownImage = null;
      if (myCachedScale < 1.0) {
        requestHighQualityScaledImage();
      }
    }

    drawImage(g);

    if (prevClip != null) {
      g.setClip(prevClip);
    }
  }

  private void drawImage(@NotNull Graphics2D g) {
    assert myImage != null : "RenderResult Image is null and this should not happen at this point";
    RenderingHints hints = g.getRenderingHints();

    if (myScaledDownImage != null && myCachedScale < 1.0) {
      // Draw the scaled down image in high quality
      g.setRenderingHints(HQ_RENDERING_HITS);
      UIUtil.drawImage(g, myScaledDownImage, myScreenView.getX(), myScreenView.getY(), null);
    }
    else {
      // If the image is being scaled down or the image needs to be only scaled up, we can directly draw
      // the image
      g.setRenderingHints(myIsRescaling ? LQ_RENDERING_HITS : HQ_RENDERING_HITS);
      myImage.drawImageTo(g, myScreenView.getX(), myScreenView.getY(),
                          (int)Math.round(myImage.getWidth() * myCachedScale),
                          (int)Math.round(myImage.getHeight() * myCachedScale));
      g.setRenderingHints(hints);
    }

    g.setRenderingHints(hints);
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
    myIsRescaling = true;
    if (myScheduledFuture != null && !myScheduledFuture.isDone()) {
      myScheduledFuture.cancel(false);
    }
    myScheduledFuture = myScheduledExecutorService.schedule(myRescaleRunnable, REQUEST_SCALE_DEBOUNCE_TIME_IN_MS, TimeUnit.MILLISECONDS);
  }

  @Override
  public void dispose() {
    super.dispose();
    myScheduledExecutorService.shutdown();
  }

  /**
   * Implementation of {@link Runnable} to do a high quality scaling of {@link #myImage} in background.
   * When the scaling is done, the {@link DesignSurface} will be repainted.
   *
   * @see ImageUtils#scale(BufferedImage, double)
   */
  private class RescaleRunnable implements Runnable {

    @Override
    public void run() {
      scaleOriginalImage();
    }

    private void scaleOriginalImage() {
      myScaledDownImage = null;
      ImagePool.Image image = myImage;
      if (image == null) {
        return;
      }
      if (UIUtil.isRetina() && ImageUtils.supportsRetina()) {
        myScaledDownImage = getRetinaScaledImage(image, myCachedScale, false);
      }
      if (myScaledDownImage == null) {
        BufferedImage imageCopy = image.getCopy();
        myScaledDownImage = ImageUtils.scale(imageCopy, myCachedScale);
      }
      myIsRescaling = false;
      UIUtil.invokeLaterIfNeeded(
        () -> myScreenView.getSurface().repaint());
    }
  }

  @Nullable
  private static BufferedImage getRetinaScaledImage(@NotNull ImagePool.Image pooledImage, double scale, boolean fastScaling) {
    if (scale > 1.01) {
      // When scaling up significantly, use normal painting logic; no need to pixel double into a
      // double res image buffer!
      return null;
    }

    BufferedImage original = pooledImage.getCopy();
    if (original == null) {
      return null;
    }

    // No scaling if very close to 1.0 (we check for 0.5 since we're doubling the output)
    if (Math.abs(scale - 0.5) > 0.001) {
      double retinaScale = 2 * scale;
      if (fastScaling) {
        original = ImageUtils.lowQualityFastScale(original, retinaScale, retinaScale);
      }
      else {
        original = ImageUtils.scale(original, retinaScale, retinaScale);
      }
    }

    return ImageUtils.convertToRetina(original);
  }
}
