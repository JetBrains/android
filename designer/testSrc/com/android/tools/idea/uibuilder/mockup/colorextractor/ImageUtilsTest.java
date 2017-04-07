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
package com.android.tools.idea.uibuilder.mockup.colorextractor;

import junit.framework.TestCase;

import java.awt.image.BufferedImage;

public class ImageUtilsTest extends TestCase {
  public void testCreateScaledImage() throws Exception {
    BufferedImage original = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
    BufferedImage scaledImage = ImageUtils.createScaledImage(original, 50);
    assertEquals(25, scaledImage.getWidth());
    assertEquals(50, scaledImage.getHeight());
  }
}