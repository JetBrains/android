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
package com.android.tools.adtui.webp;

import com.android.tools.adtui.imagediff.ImageDiffUtil;
import com.google.common.collect.BoundType;
import com.google.common.collect.Range;
import junit.framework.TestCase;
import org.jetbrains.annotations.NotNull;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;

import com.google.common.truth.Truth;

public class WebpSupportTest extends TestCase {

  public void testRegistration() throws Exception {
    // Make sure it understands both webp and WEBP as valid format names (it's case sensitive)
    assertTrue(ImageIO.getImageWritersByFormatName("webp").hasNext());
    assertTrue(ImageIO.getImageWritersByFormatName("WEBP").hasNext());

    assertTrue(ImageIO.getImageReadersByFormatName("webp").hasNext());
    assertTrue(ImageIO.getImageReadersByFormatName("WEBP").hasNext());

    assertTrue(ImageIO.getImageReadersByMIMEType("image/webp").hasNext());
    assertTrue(ImageIO.getImageWritersByMIMEType("image/webp").hasNext());
  }

  public void testCodec() throws Exception {
    BufferedImage image = createSampleImage(BufferedImage.TYPE_INT_ARGB);

    // Test encoder
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(2048);

    // Default parameters (currently lossy encoding, 75%)
    ImageIO.write(image, "WEBP", outputStream);
    byte[] encoded = outputStream.toByteArray();
    assertTrue(encoded.length > 0);

    // Test decoder
    InputStream stream = new ByteArrayInputStream(encoded);
    BufferedImage decoded = ImageIO.read(stream);
    assertNotNull(decoded);

    ImageDiffUtil.assertImageSimilar(getName(), image, decoded, 3);
  }

  public void testLossyWithTransparency() throws Exception {
    BufferedImage image = createSampleImage(BufferedImage.TYPE_INT_ARGB);

    int x = 100;
    int y = 100;
    int rgba = image.getRGB(x, y);
    int alpha = 136;
    image.setRGB(x, y, alpha << 24 | (rgba & 0xFFFFFF));

    // Test encoder
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(2048);

    // Default parameters (currently lossy encoding, 75%)
    WebpImageWriterSpi.writeImage(image, outputStream, false, 75);
    byte[] encoded = outputStream.toByteArray();
    assertTrue(encoded.length > 0);

    // Test decoder
    InputStream stream = new ByteArrayInputStream(encoded);
    BufferedImage decoded = ImageIO.read(stream);
    assertNotNull(decoded);

    ImageDiffUtil.assertImageSimilar(getName(), image, decoded, 3);

    // Check that alpha was preserved
    int encodedAlpha = decoded.getRGB(x, y) >>> 24;
    Truth.assertThat(encodedAlpha).isIn(Range.range(alpha - 1, BoundType.CLOSED, alpha + 1, BoundType.CLOSED));
  }

  public void testConvertIndexedPalette() throws Exception {
    byte[] png = Base64.getDecoder().decode(
      ""
      + "iVBORw0KGgoAAAANSUhEUgAAAIsAAAB1CAMAAABJX+KkAAAC/VBMVEUhNnjqCywARpc7N4YRQpgKR4ckQXoMRpMASpsAS5ZBPG4AToAPR5pVOGECTJFpMXAA"
      + "TJ4RSJXtFRoCT5roFyVGREjoGB8AUKE+R0VeOnFFRU/lGSx+M1pHRkSfLENNRT2RL1IJUJzYHygAU6RESUAAVJ8MUpgAVacOUp4AVqFKSUw7Tz67KT4AV6LX"
      + "IzUSU58AWKSIOFLHKTMBWqUXVaIWV5c2VU8GW6cZVqMAX6kAYLIMXqMOXakAYawRXqoAY64AY7QUX6sAZbA6X0EAZqqZO5oAZraSPpuJQZoAZ7goZGCWP5UH"
      + "ZrgzYHIGZ7OPQpQcZY6SQo4bY6kAarR1SaUHaa6BR50BbaMAbasAbbEAbL6JSJIcZ6B7TJoAbrlsUZtsUKYDcZcRarbYOkkAcLtjV5JeV6gdaMAWa7cAcrcF"
      + "cMJMXa5TXKSNTY4Bcr0XbbNGYLM2ZL5bY183cT8CdrSEU5AAeKcAdr9DZK0Lda1kYmWAWHgKdMAAeMIfcLYgca84abgAesQubrUOd7wFecgAfMYAfcEeeZE+"
      + "ba0AfccAfc8AfskVfo8RgnQEf7o7ek8Bf8oAgMkcfKIZer8OhIkneq0ef54GgshubHAdfbsLhcNAgkoUin9YcqmXYKTUVWSXZZh2dHg6i00XknLeW1o1h5si"
      + "lkYsiLsplzJFhowjmEEwkmUll1onmjumaatEkUs3lkWMcrqZcZ4loDkvkMJIib7yX2pqgb07kLltgrKGe7Kuc6cpo3glqGaJh4svq1Ggg6FCqFBflq0wrVxK"
      + "plk+rEw0r04tskkwskE5r0dcoW0xtDxTp2rteHtZnsdsoJl1ooRqpn1hq9aLp4Win6OfoZ6UpKW+mKbKkdGSqJmfpKeDq7h7rcnHmM15ruGrqKz0mJ+BypNz"
      + "xu/6qKK7ub3FtPeZxOLgseDXtdWvw8m+vtDZteGvw9K3vu6/wri9wr7CwMW8wsX7t77XxNnvvOjlxujF2fLG6/nN89fg+f7u9f7n/Or4+Pn3+vDu/PmSo/qj"
      + "AAAAAWJLR0QAiAUdSAAAAAlwSFlzAAALEwAACxMBAJqcGAAAAAd0SU1FB+AKEREDCTeQu1kAAAslSURBVGje7dgPWBP3GQfwzKlwjtLFmKBQ2kY8ZtCA9aFl"
      + "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"
    );

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));

    // Test encoder
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(2048);

    // Default parameters (currently lossy encoding, 75%)
    ImageIO.write(image, "WEBP", outputStream);
    byte[] encoded = outputStream.toByteArray();
    assertTrue(encoded.length > 0);

    // Test decoder
    InputStream stream = new ByteArrayInputStream(encoded);
    BufferedImage decoded = ImageIO.read(stream);
    assertNotNull(decoded);

    ImageDiffUtil.assertImageSimilar(getName(), image, decoded, 3);
  }

  @NotNull
  private static BufferedImage createSampleImage(int type) {
    //noinspection UndesirableClassUsage
    BufferedImage image = new BufferedImage(300, 400, type);
    Graphics2D graphics = image.createGraphics();
    //noinspection UseJBColor
    graphics.setColor(Color.GREEN);
    graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
    //noinspection UseJBColor
    graphics.setColor(Color.BLUE);
    graphics.fillRect(0, 0, image.getWidth() / 2, image.getHeight() / 2);
    graphics.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f));
    graphics.fillRect(image.getWidth() / 2, image.getHeight() / 2, image.getWidth(), image.getHeight());

    graphics.dispose();
    return image;
  }
}