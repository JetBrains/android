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
package com.android.tools.idea.rendering;

import com.android.tools.adtui.imagediff.ImageDiffUtil;
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
public class ImagePoolTest {
  private ImagePool myPool;

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
    } finally {
      g.dispose();
    }

    return image;
  }

  @Before
  public void before() {
    myPool = new ImagePool();
  }

  @After
  public void after() {
    myPool.dispose();
  }

  @Test
  public void testImagePooling() throws InterruptedException {
    CountDownLatch countDown1 = new CountDownLatch(1);
    CountDownLatch countDown2 = new CountDownLatch(1);
    AtomicBoolean secondImageFreed = new AtomicBoolean(false);

    ImagePool.ImageImpl image1 = myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, (image) -> countDown1.countDown());
    BufferedImage internalPtr = image1.myBuffer;
    ImagePool.ImageImpl image2 = myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, (image) -> {
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

    // Save the pointer to the internal image2 buffer
    internalPtr = image2.myBuffer;
    //noinspection UnusedAssignment
    image2 = null;
    gc();
    countDown2.await(3, TimeUnit.SECONDS);
    // We will get images from different size, and type, none of them should return the pooled image
    assertNotEquals(internalPtr, myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB_PRE, null).myBuffer);
    assertNotEquals(internalPtr, myPool.create(51, 50, BufferedImage.TYPE_INT_ARGB, null).myBuffer);
    assertNotEquals(internalPtr, myPool.create(50, 51, BufferedImage.TYPE_INT_ARGB, null).myBuffer);
    assertEquals(internalPtr, myPool.create(50, 50, BufferedImage.TYPE_INT_ARGB, null).myBuffer);
  }

  @Test
  public void testDefaultPooling() throws InterruptedException {
    CountDownLatch countDown = new CountDownLatch(1);
    ImagePool.ImageImpl image1 = myPool.create(10, 10, BufferedImage.TYPE_INT_ARGB, (b) -> countDown.countDown());

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

    ImagePool.ImageImpl image = (ImagePool.ImageImpl)myPool.copyOf(original);
    assertNotEquals(original, image.myBuffer);

    BufferedImage copy = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
    image.drawImageTo(copy);
    ImageDiffUtil.assertImageSimilar("pooledimage", original, copy, 0.0);

    copy = image.getCopy();
    assertNotEquals(copy, image.myBuffer);
    ImageDiffUtil.assertImageSimilar("pooledimage", original, copy, 0.0);
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
}