/*
 * Copyright (C) 2013 The Android Open Source Project
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
package com.android.tools.idea.ddms.screenshot;

import com.android.SdkConstants;
import com.android.dvlib.DeviceSchemaTest;
import com.android.resources.ScreenOrientation;
import com.android.sdklib.devices.Device;
import com.android.sdklib.devices.DeviceParser;
import com.android.tools.idea.rendering.ImageUtils;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.io.Files;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.android.AndroidTestBase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;

import static com.android.tools.idea.ddms.screenshot.DeviceArtPainter.DeviceData;
import static com.android.tools.idea.ddms.screenshot.DeviceArtPainter.FrameData;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * If adding a new device (new device art), run the tests to generate crop data and insert
 * this into the device art descriptors for the new devices, then look at generateCropData
 * and remove the early exit to have that method generate new, updated (cropped) images
 * and adjusted device-art descriptors.
 */
public class DeviceArtPainterTest {

  @Rule
  public TemporaryFolder myTemporaryFolder = new TemporaryFolder();

  @Test
  public void testGenerateCropData() throws Exception {
    // TODO: Assert that the crop data is right
    generateCropData();
  }

  // This test is disabled but code is preserved here; this is handy for quickly checking rendering results when tweaking the code to
  // assemble composite images. (Make sure you also turn off the thumbnail cache first! Return null from DeviceArtPainter#getCachedImage.)
  @Test
  @Ignore
  public void testRendering() throws Exception {
    DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
    for (DeviceArtDescriptor spec : framePainter.getDescriptors()) {
      if ("wear_round".equals(spec.getId())) {
        FrameData frameData = new DeviceData(null, spec).getFrameData(ScreenOrientation.LANDSCAPE, 320);
        BufferedImage image = frameData.getImage(true);
        @SuppressWarnings("SSBasedInspection")
        File file = File.createTempFile("test-rendering", "png");
        if (file.exists()) {
          boolean deleted = file.delete();
          assertTrue(deleted);
        }
        ImageIO.write(image, "PNG", file);
        if (file.exists() && SystemInfo.isMac) {
          Runtime.getRuntime().exec("/usr/bin/open " + file.getPath());
        }
      }
    }
  }

  @Test
  public void testCroppedRendering() throws Exception {
    File deviceArtPath = new File(AndroidTestBase.getAbsoluteTestDataPath(), FileUtil.join("..", "device-art-resources"));
    List<DeviceArtDescriptor> descriptors = DeviceArtDescriptor.getDescriptors(new File[]{deviceArtPath});

    DeviceArtDescriptor wear_square = findDescriptor(descriptors, "wear_square");
    DeviceArtDescriptor wear_round = findDescriptor(descriptors, "wear_round");

    assertNotNull(wear_square);
    assertNotNull(wear_round);

    Dimension size = wear_round.getScreenSize(ScreenOrientation.LANDSCAPE);
    BufferedImage sample = createSampleImage(size, Color.RED);

    BufferedImage framed = DeviceArtPainter.createFrame(sample, wear_round, true, false);

    // make sure that a location outside the round frame is empty
    // (if the mask was not applied, this would be the same color as the source image)
    Point loc = wear_round.getScreenPos(ScreenOrientation.LANDSCAPE);
    int c = framed.getRGB(loc.x, loc.y);
    assertEquals(0x0, c);

    // a point at the center should be the same as the source
    c = framed.getRGB(loc.x + size.width / 2, loc.y + size.height / 2);
    assertEquals(Color.RED.getRGB(), c);
  }

  @NotNull
  private static BufferedImage createSampleImage(Dimension size, Color color) {
    @SuppressWarnings("UndesirableClassUsage") // no need to support retina
    BufferedImage img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2d = img.createGraphics();
    g2d.setColor(color);
    g2d.fillRect(0, 0, size.width, size.height);
    g2d.dispose();
    return img;
  }

  @Nullable
  private static DeviceArtDescriptor findDescriptor(@NotNull List<DeviceArtDescriptor> descriptors, @NotNull String id) {
    for (DeviceArtDescriptor desc : descriptors) {
      if (desc.getId().equals(id)) {
        return desc;
      }
    }

    return null;
  }

  public void generateCropData() throws Exception {
    DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
    Device device = newDevice();
    for (DeviceArtDescriptor spec : framePainter.getDescriptors()) {
      DeviceData data = new DeviceData(device, spec);
      Rectangle cropRect = spec.getCrop(ScreenOrientation.LANDSCAPE);
      if (spec.getName().startsWith("Generic ")) {
        // No crop data for generic nine patches since they are stretchable
        continue;
      }
      if (cropRect != null && !cropRect.getSize().equals(spec.getScreenSize(ScreenOrientation.LANDSCAPE))) {
        // Already have crop data for this spec; skipping
        continue;
      }
      if (spec.getName().startsWith("Android TV")) {
        // These images are already cropped
        continue;
      }
      System.out.println("for spec " + spec.getName() + " -- " + spec.getId());
      FrameData landscapeData = data.getFrameData(ScreenOrientation.LANDSCAPE, Integer.MAX_VALUE);
      // Must use computeImage rather than getImage here since we want to get the
      // full size images, not the already cropped images

      BufferedImage effectsImage;
      Rectangle crop;
      ImageUtils.CropFilter filter = new ImageUtils.CropFilter() {
        @Override
        public boolean crop(BufferedImage bufferedImage, int x, int y) {
          int rgb = bufferedImage.getRGB(x, y);
          return ((rgb & 0xFF000000) >>> 24) < 2;
        }
      };

      FrameData portraitData = data.getFrameData(ScreenOrientation.PORTRAIT, Integer.MAX_VALUE);
      try {
        effectsImage = portraitData.computeImage(true, 0, 0, portraitData.getFrameWidth(), portraitData.getFrameHeight());
      } catch (OutOfMemoryError oome) {
        // This test sometimes fails on the build server because it runs out of memory; it's a memory
        // hungry test which sometimes fails when run as part of thousands of other tests.
        // Ignore those types of failures.
        // Make sure it's not failing to allocate memory due to some crazy large bounds we didn't anticipate:
        assertTrue(portraitData.getFrameWidth() < 4000);
        assertTrue(portraitData.getFrameHeight() < 4000);
        return;
      }

      assertNotNull(effectsImage);
      crop = ImageUtils.getCropBounds(effectsImage, filter, null);
      assertNotNull(crop);
      System.out.print("      port crop=\"");
      System.out.print(crop.x);
      System.out.print(",");
      System.out.print(crop.y);
      System.out.print(",");
      System.out.print(crop.width);
      System.out.print(",");
      System.out.print(crop.height);
      System.out.println("\"");


      try {
        effectsImage = landscapeData.computeImage(true, 0, 0, landscapeData.getFrameWidth(), landscapeData.getFrameHeight());
      } catch (OutOfMemoryError oome) {
        // See portrait case above
        assertTrue(landscapeData.getFrameWidth() < 4000);
        assertTrue(landscapeData.getFrameHeight() < 4000);
        return;
      }
      assertNotNull(effectsImage);
      crop = ImageUtils.getCropBounds(effectsImage, filter, null);
      assertNotNull(crop);
      System.out.print("      landscape crop=\"");
      System.out.print(crop.x);
      System.out.print(",");
      System.out.print(crop.y);
      System.out.print(",");
      System.out.print(crop.width);
      System.out.print(",");
      System.out.print(crop.height);
      System.out.println("\"");
    }
  }

  @SuppressWarnings("IOResourceOpenedButNotSafelyClosed")
  private static Device newDevice() throws Exception {
    Collection<Device> devices;
    InputStream stream = null;
    try {
      stream = DeviceSchemaTest.class.getResourceAsStream("devices_minimal.xml");
      devices = DeviceParser.parse(stream).values();
    } finally {
      if (stream != null) {
        stream.close();
      }
    }
    assertTrue(!devices.isEmpty());
    return devices.iterator().next();
  }

  @Nullable
  private static BufferedImage getImage(@NotNull File srcDir, @Nullable File file) {
    if (file == null) {
      return null;
    }
    if (!file.isAbsolute()) {
      file = new File(srcDir, file.getPath());
    }

    if (file.exists()) {
      try {
        return ImageIO.read(file);
      }
      catch (IOException e) {
        // pass
      }
    }

    return null;
  }

  // This test no longer applies; it was used to convert assets with a lot of padding into more tightly cropped screenshots.
  // We're preserving the code since for future device releases we might get new artwork which includes padding.
  @Test
  @Ignore
  public void testCropData() throws Exception {

    // Apply crop
    DeviceArtPainter framePainter = DeviceArtPainter.getInstance();
    Device device = newDevice();

    File srcDir = DeviceArtDescriptor.getBundledDescriptorsFolder();
    File destDir = new File(myTemporaryFolder.newFolder(), "device-art");
    if (!destDir.exists()) {
      boolean ok = destDir.mkdirs();
      assertTrue(ok);
    }

    StringBuilder sb = new StringBuilder(1000);
    sb.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
              "<!-- Copyright (C) 2013 The Android Open Source Project\n" +
              "\n" +
              "     Licensed under the Apache License, Version 2.0 (the \"License\");\n" +
              "     you may not use this file except in compliance with the License.\n" +
              "     You may obtain a copy of the License at\n" +
              "\n" +
              "          http://www.apache.org/licenses/LICENSE-2.0\n" +
              "\n" +
              "     Unless required by applicable law or agreed to in writing, software\n" +
              "     distributed under the License is distributed on an \"AS IS\" BASIS,\n" +
              "     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.\n" +
              "     See the License for the specific language governing permissions and\n" +
              "     limitations under the License.\n" +
              "-->\n" +
              "<devices>\n" +
              "\n");

    for (DeviceArtDescriptor spec : framePainter.getDescriptors()) {
      sb.append("  <device id=\"");
      sb.append(spec.getId());
      sb.append("\" name=\"");
      sb.append(spec.getName());
      sb.append("\">\n");
      DeviceData deviceData = new DeviceData(device, spec);
      for (ScreenOrientation orientation : ScreenOrientation.values()) {
        if (orientation == ScreenOrientation.SQUARE) {
          continue;
        }
        if (orientation != ScreenOrientation.LANDSCAPE && spec.getId().startsWith("tv_")) {
          // Android TV only uses landscape orientation
          continue;
        }

        Rectangle cropRect = spec.getCrop(orientation);

        sb.append("    <orientation name=\"");
        sb.append(orientation.getResourceValue());
        sb.append("\" ");

        DeviceArtDescriptor descriptor = deviceData.getDescriptor();

        if (spec.getName().startsWith("Generic ") || cropRect == null || spec.getName().startsWith("Android TV")) {
          System.out.println("Nothing to do for " + spec.getId() + " orientation " + orientation);
          cropRect = new Rectangle(0, 0, descriptor.getFrameSize(orientation).width, descriptor.getFrameSize(orientation).height);
        }

        sb.append("size=\"");
        sb.append(Integer.toString(cropRect.width));
        sb.append(",");
        sb.append(Integer.toString(cropRect.height));
        sb.append("\" screenPos=\"");

        sb.append(Integer.toString(descriptor.getScreenPos(orientation).x - cropRect.x));
        sb.append(",");
        sb.append(Integer.toString(descriptor.getScreenPos(orientation).y - cropRect.y));
        sb.append("\" screenSize=\"");
        sb.append(Integer.toString(descriptor.getScreenSize(orientation).width));
        sb.append(",");
        sb.append(Integer.toString(descriptor.getScreenSize(orientation).height));
        sb.append("\"");
        if (descriptor.getDropShadow(orientation) != null) {
          sb.append(" shadow=\"");
          //noinspection ConstantConditions
          sb.append(descriptor.getDropShadow(orientation).getName());
          sb.append("\"");
        }
        if (descriptor.getFrame(orientation) != null) {
          sb.append(" back=\"");
          //noinspection ConstantConditions
          sb.append(descriptor.getFrame(orientation).getName());
          sb.append("\"");
        }
        if (descriptor.getReflectionOverlay(orientation) != null) {
          sb.append(" lights=\"");
          //noinspection ConstantConditions
          sb.append(descriptor.getReflectionOverlay(orientation).getName());
          sb.append("\"");
        }
        if (descriptor.getMask(orientation) != null) {
          sb.append(" mask=\"");
          //noinspection ConstantConditions
          sb.append(descriptor.getMask(orientation).getName());
          sb.append("\"");
        }
        sb.append("/>\n");

        // Must use computeImage rather than getImage here since we want to get the
        // full size images, not the already cropped images

        writeCropped(srcDir, destDir, spec, cropRect, descriptor.getFrame(orientation));
        writeCropped(srcDir, destDir, spec, cropRect, descriptor.getDropShadow(orientation));
        writeCropped(srcDir, destDir, spec, cropRect, descriptor.getReflectionOverlay(orientation));
        writeCropped(srcDir, destDir, spec, cropRect, descriptor.getMask(orientation));
      }

      // (3) Rewrite emulator skin file
      File layoutFile = new File(srcDir, spec.getId() + File.separator + SdkConstants.FN_SKIN_LAYOUT);
      if (layoutFile.exists() && !spec.getId().startsWith("tv_")) { // no crop data in tv (and lack of portrait fails below)
        String layout = Files.toString(layoutFile, Charsets.UTF_8);
        final Rectangle portraitCrop = spec.getCrop(ScreenOrientation.PORTRAIT);
        assertNotNull("No crop data found; did you run this test on an already processed device-art.xml?", portraitCrop);
        final Rectangle landscapeCrop = spec.getCrop(ScreenOrientation.LANDSCAPE);
        layout = replace(layout, new String[]{"layouts {", "portrait {", "width "}, new Function<Integer, Integer>() {
          @Override
          public Integer apply(@Nullable Integer input) {
            return portraitCrop.width;
          }
        });
        layout = replace(layout, new String[]{"layouts {", "portrait {", "height "}, new Function<Integer, Integer>() {
          @Override
          public Integer apply(@Nullable Integer input) {
            return portraitCrop.height;
          }
        });
        layout = replace(layout, new String[]{"layouts {", "portrait {", "part2 {", "x "}, new Function<Integer, Integer>() {
          @Override
          public Integer apply(@Nullable Integer input) {
            //noinspection ConstantConditions
            return input - portraitCrop.x;
          }
        });
        layout = replace(layout, new String[]{"layouts {", "portrait {", "part2 {", "y "}, new Function<Integer, Integer>() {
          @Override
          public Integer apply(@Nullable Integer input) {
            //noinspection ConstantConditions
            return input - portraitCrop.y;
          }
        });

        // landscape
        layout = replace(layout, new String[]{"layouts {", "landscape {", "width "}, new Function<Integer, Integer>() {
          @Override
          public Integer apply(@Nullable Integer input) {
            return landscapeCrop.width;
          }
        });
        layout = replace(layout, new String[]{"layouts {", "landscape {", "height "}, new Function<Integer, Integer>() {
          @Override
          public Integer apply(@Nullable Integer input) {
            return landscapeCrop.height;
          }
        });
        layout = replace(layout, new String[]{"layouts {", "landscape {", "part2 {", "x "}, new Function<Integer, Integer>() {
          @Override
          public Integer apply(@Nullable Integer input) {
            //noinspection ConstantConditions
            return input - landscapeCrop.x;
          }
        });
        layout = replace(layout, new String[]{"layouts {", "landscape {", "part2 {", "y "}, new Function<Integer, Integer>() {
          @Override
          public Integer apply(@Nullable Integer input) {
            //noinspection ConstantConditions
            return input - landscapeCrop.y;
          }
        });

        File outputLayoutFile = new File(destDir, spec.getId() + File.separator + SdkConstants.FN_SKIN_LAYOUT);
        if (!outputLayoutFile.getParentFile().exists()) {
          boolean mkdirs = outputLayoutFile.getParentFile().mkdirs();
          assertTrue(mkdirs);
        }
        Files.write(layout, outputLayoutFile, Charsets.UTF_8);
      }

      sb.append("  </device>\n\n");
    }
    sb.append("\n</devices>\n");

    File deviceArt = new File(destDir, "device-art.xml");
    Files.write(sb.toString(), deviceArt, Charsets.UTF_8);
    System.out.println("Wrote device art file " + deviceArt);
  }

  private static String replace(String file, String[] sections, Function<Integer, Integer> replace) {
    int index = 0;
    for (String section : sections) {
      index = file.indexOf(section, index);
      assert index != -1 : section + " not found";
      index += section.length();
    }

    // We're now pointing to a token
    int lineEnd = file.indexOf('\n', index);
    assert lineEnd != -1;
    String word = file.substring(index, lineEnd);
    int input = Integer.parseInt(word);
    @SuppressWarnings("ConstantConditions")
    int replaced = replace.apply(input);
    return file.substring(0, index) + Integer.toString(replaced) + file.substring(lineEnd);
  }

  private static void writeCropped(File srcDir, File destDir, DeviceArtDescriptor spec, Rectangle cropRect, @Nullable File imageFile)
      throws IOException {
    if (imageFile == null) {
      // This image doesn't apply
      return;
    }
    BufferedImage source = getImage(srcDir, imageFile);
    if (source == null) {
      return;
    }

    BufferedImage cropped = cropImage(source, cropRect);
    assertNotNull(cropped);

    File dir = new File(destDir, spec.getId());
    if (!dir.exists()) {
      boolean ok = dir.mkdir();
      assertTrue(dir.getPath(), ok);
    }
    ImageIO.write(cropped, "PNG", new File(dir, imageFile.getName()));
  }

  private static BufferedImage cropImage(@NotNull BufferedImage image, @NotNull Rectangle cropBounds) {
    int x1 = cropBounds.x;
    int y1 = cropBounds.y;
    int width = cropBounds.width;
    int height = cropBounds.height;
    int x2 = x1 + width;
    int y2 = y1 + height;

    // Now extract the sub-image
    @SuppressWarnings("UndesirableClassUsage")
    BufferedImage cropped = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    Graphics g = cropped.getGraphics();
    //noinspection UseJBColor
    g.setColor(new Color(0, true));
    g.fillRect(0, 0, width, height);
    g.drawImage(image, 0, 0, width, height, x1, y1, x2, y2, null);
    g.dispose();

    return cropped;
  }
}
