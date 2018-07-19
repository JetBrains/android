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

import org.junit.Test;

import java.awt.image.BufferedImage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class NonPooledImageTest {
  @Test
  public void checkCreation() {
    NonPooledImage image1 = NonPooledImage.create(100, 150, BufferedImage.TYPE_INT_ARGB);
    assertEquals(100, image1.getWidth());
    assertEquals(150, image1.getHeight());
    BufferedImage image1copy = image1.getCopy();
    // Every copy must be a new one
    assertNotEquals(image1copy, image1.getCopy());
    assertEquals(100, image1copy.getWidth());
    assertEquals(150, image1copy.getHeight());
    assertEquals(BufferedImage.TYPE_INT_ARGB, image1copy.getType());

    image1.dispose();
  }
}
