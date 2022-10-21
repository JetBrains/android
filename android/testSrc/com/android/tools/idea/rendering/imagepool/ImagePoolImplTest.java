/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.tools.idea.rendering.imagepool;

import com.android.testutils.ImageDiffUtil;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

@SuppressWarnings("UndesirableClassUsage")
public class ImagePoolImplTest {
  private ImagePoolImpl myPool;

  // Try to force a gc round
  private static void gc() {
    System.gc();
    System.gc();
    System.gc();
  }

  /**
   * Returns a sample 50x50 image, half blue, half red
   */
  private static BufferedImage getSampleImage() {
    BufferedImage image = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g = (Graphics2D)image.getGraphics();
    try {
      g.setColor(Color.RED);
      g.fillRect(0, 0, 25, 50);
      g.setColor(Color.BLUE);
      g.fillRect(25, 0, 50, 50);
    }
    finally {
      g.dispose();
    }

    return image;
  }

  @Before
  public void before() {
    myPool = new ImagePoolImpl(new int[]{50, 500, 1000, 1500, 2000, 5000}, (w, h) -> (type) -> {
      // Images below 1k, do not pool
      if (w * h < 1000) {
        return 0;
      }

      return 50_000_000 / (w * h);
    });
  }

  @After
  public void after() {
    myPool.dispose();
  }

  @Test
  public void testImagePooling() throws InterruptedException, IOException {
    CountDownLatch countDown1 = new CountDownLatch(1);
    CountDownLatch countDown2 = new CountDownLatch(1);
    AtomicBoolean secondImageFreed = new AtomicBoolean(false);

    ImagePoolImpl.ImageImpl image1 = myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, (image) -> countDown1.countDown());
    image1.drawFrom(getSampleImage());
    BufferedImage internalPtr = image1.myBuffer;
    ImagePoolImpl.ImageImpl image2 = myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, (image) -> {
      countDown2.countDown();
      secondImageFreed.set(true);
    });
    assertNotEquals(image1.myBuffer, image2.myBuffer);

    //noinspection UnusedAssignment
    image1 = null;
    gc();

    // Wait for the image to be collected
    countDown1.await(3, TimeUnit.SECONDS);
    image1 = myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, null);
    assertEquals(image1.myBuffer, internalPtr);
    assertFalse(secondImageFreed.get());
    // The image is being reused. Check that it's a clean image
    ImageDiffUtil
      .assertImageSimilar("clean", new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB), image1.myBuffer.getSubimage(0, 0, 50, 50), 0.0);

    //noinspection UnusedAssignment
    image2 = null;
    gc();
    countDown2.await(3, TimeUnit.SECONDS);

    ImagePoolImpl.ImageImpl tmpImage = myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB_PRE, null);
    assertEquals(50, tmpImage.getWidth());
    assertEquals(50, tmpImage.getHeight());
    assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, tmpImage.myBuffer.getType());

    tmpImage = myPool.create(51, 50, BufferedImage.TYPE_INT_ARGB, null);
    assertEquals(51, tmpImage.getWidth());
    assertEquals(50, tmpImage.getHeight());
    assertEquals(BufferedImage.TYPE_INT_ARGB, tmpImage.myBuffer.getType());

    tmpImage = myPool.create(50, 51, BufferedImage.TYPE_INT_ARGB, null);
    assertEquals(50, tmpImage.getWidth());
    assertEquals(51, tmpImage.getHeight());
    assertEquals(BufferedImage.TYPE_INT_ARGB, tmpImage.myBuffer.getType());

    //noinspection UnusedAssignment
    tmpImage = null;
    gc();
  }

  @Test
  public void testManualFree() {
    ImagePool.Stats stats = myPool.getStats();
    assertNotNull(stats);
    ImagePoolImpl.ImageImpl image = myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, null);
    BufferedImage internalPtr = image.myBuffer;

    assertNotEquals(internalPtr, myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, null).myBuffer);
    assertEquals(2_000_000, stats.totalBytesAllocated());
    assertEquals(2_000_000, stats.totalBytesInUse());
    ImagePoolImageDisposer.disposeImage(image);
    assertEquals(2_000_000, stats.totalBytesAllocated());
    assertEquals(1_000_000, stats.totalBytesInUse());
    assertEquals(internalPtr, myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, null).myBuffer);
    assertEquals(2_000_000, stats.totalBytesAllocated());
    assertEquals(2_000_000, stats.totalBytesInUse());
    assertNull(image.getCopy());

    //noinspection UnusedAssignment
    image = null;
    gc();
  }

  @Test
  public void testDefaultPooling() throws InterruptedException {
    // Small images won't be pooled
    CountDownLatch countDown = new CountDownLatch(1);
    ImagePoolImpl.ImageImpl image1 = myPool.create(10, 10, BufferedImage.TYPE_INT_ARGB, (b) -> countDown.countDown());

    BufferedImage internalPtr = image1.myBuffer;
    //noinspection UnusedAssignment
    image1 = null;
    gc();
    countDown.await(3, TimeUnit.SECONDS);
    assertNotEquals(internalPtr, myPool.create(10, 10, BufferedImage.TYPE_INT_ARGB, null).myBuffer);
  }

  @Test
  public void testImageCopy() throws IOException {
    BufferedImage original = getSampleImage();

    ImagePoolImpl.ImageImpl image = (ImagePoolImpl.ImageImpl)myPool.copyOf(original);
    assertNotEquals(original, image.myBuffer);

    BufferedImage copy = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
    image.drawImageTo(copy);
    ImageDiffUtil.assertImageSimilar("pooledimage", original, copy, 0.0);

    copy = image.getCopy();
    assertNotEquals(copy, image.myBuffer);
    ImageDiffUtil.assertImageSimilar("pooledimage", original, copy, 0.0);

    copy = image.getCopy(0, 0, 25, 50);
    assertNotEquals(copy, image.myBuffer);
    assertEquals(25, copy.getWidth());
    assertEquals(50, copy.getHeight());
    ImageDiffUtil.assertImageSimilar("pooledimage", original.getSubimage(0, 0, 25, 50), copy, 0.0);

    copy = image.getCopy(10, 10, 25, 25);
    assertNotEquals(copy, image.myBuffer);
    assertEquals(25, copy.getWidth());
    assertEquals(25, copy.getHeight());
    ImageDiffUtil.assertImageSimilar("pooledimage", original.getSubimage(10, 10, 25, 25), copy, 0.0);

    try {
      //noinspection UnusedAssignment
      copy = image.getCopy(0, 0, 25, 150);
      fail("IndexOutOfBoundsException expected for height out of bounds");
    }
    catch (IndexOutOfBoundsException ignored) {
    }
  }

  @Test
  public void testNullImage() throws IOException {
    ImagePool.Image image = myPool.copyOf(null);
    assertNotNull(image);
    assertEquals(0, image.getWidth());
    assertEquals(0, image.getHeight());
    assertNull(image.getCopy());

    BufferedImage original = getSampleImage();
    BufferedImage buffer = getSampleImage();
    ImageDiffUtil.assertImageSimilar("sample", original, buffer, 0.0);

    // Using the null image shouldn't affect the destination
    image.drawImageTo(buffer);
    ImageDiffUtil.assertImageSimilar("sample", original, buffer, 0.0);
  }

  @Test
  public void testPaint() throws IOException {
    BufferedImage sample = getSampleImage();
    ImagePool.Image image = myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, null);
    image.paint((g) -> {
      g.setColor(Color.RED);
      g.fillRect(0, 0, 25, 50);
    });
    boolean threw = false;
    try {
      ImageDiffUtil.assertImageSimilar("sample", sample, image.getCopy(), 0.0);
    }
    catch (AssertionError ignored) {
      threw = true;
    }
    assertTrue("Images must be different", threw);

    image.paint((g) -> {
      g.setColor(Color.RED);
      g.fillRect(0, 0, 25, 50);
      g.setColor(Color.BLUE);
      g.fillRect(25, 0, 25, 50);
    });
    ImageDiffUtil.assertImageSimilar("sample", sample, image.getCopy(), 0.0);
    image.paint((g) -> {
      // This is a valid way to make modifications into the image and shouldn't break anything
      image.drawImageTo(g, 0, 0, image.getWidth(), image.getHeight());
    });
    ImageDiffUtil.assertImageSimilar("sample", sample, image.getCopy(), 0.0);
  }

  @Test
  public void testPaintWithOffset() throws IOException {
    BufferedImage sample = getSampleImage();
    ImagePool.Image image = myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, null);
    image.paint((g) -> {
      g.setColor(Color.RED);
      g.fillRect(0, 0, 25, 50);
      g.setColor(Color.BLUE);
      g.fillRect(25, 0, 25, 50);
    });

    BufferedImage sampleImagePlusOffset = new BufferedImage(sample.getWidth() * 2, sample.getHeight() * 2, sample.getType());
    Graphics g = sampleImagePlusOffset.getGraphics();
    g.drawImage(sample, sample.getWidth(), sample.getHeight(), sample.getWidth() * 2, sample.getHeight() * 2, 0, 0, sample.getWidth(),
                sample.getHeight(), null);
    g.dispose();

    BufferedImage testImagePlusOffset = new BufferedImage(sample.getWidth() * 2, sample.getHeight() * 2, sample.getType());

    g = testImagePlusOffset.getGraphics();
    image.drawImageTo(g, sample.getWidth(), sample.getHeight(), image.getWidth(), image.getHeight());
    ImageDiffUtil.assertImageSimilar("offsetSample", sampleImagePlusOffset, testImagePlusOffset, 0.0);
  }
}