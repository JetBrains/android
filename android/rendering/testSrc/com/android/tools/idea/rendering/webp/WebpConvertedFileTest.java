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
package com.android.tools.idea.rendering.webp;

import static com.android.testutils.ImageDiffUtil.assertImageSimilar;
import static com.google.common.truth.Truth.assertThat;

import com.android.tools.adtui.webp.WebpNativeLibDownloader;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.vfs.VirtualFile;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageOutputStream;
import org.jetbrains.android.AndroidTestCase;

public class WebpConvertedFileTest extends AndroidTestCase {
  @Override
  public void setUp() throws Exception {
    super.setUp();
    WebpNativeLibDownloader.ensureWebpRegistered();
  }

  public void testLossless() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.lossless = true;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable/icon1.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNotNull();
    assertThat(convertedFile.encoded).isNull();

    assertThat(convertedFile.sourceFile).isSameAs(file);
    assertThat(convertedFile.sourceFileSize).isEqualTo(2574);

    boolean converted = convertedFile.convert(settings);
    assertThat(converted).isTrue();
    assertThat(convertedFile.encoded).isNotNull();
    assertThat(convertedFile.saved).isGreaterThan(0L);

    assertImageSimilar(getName(), convertedFile.getSourceImage(), convertedFile.getEncodedImage(), 3);
  }

  public void testSkipTransparent() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = true;
    settings.lossless = true;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable/icon2.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNull();
  }

  public void testSkipLauncherIcons() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = true;
    settings.lossless = true;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable-mdpi/ic_launcher.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNull();
  }

  public void testSkipNinePatches() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipNinePatches = true;
    settings.lossless = true;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable/icon3.9.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNull();
  }

  public void testLossy() throws Exception {
    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.lossless = false;
    settings.quality = 80;

    VirtualFile file = myFixture.copyFileToProject("projects/basic/src/main/res/drawable/icon.png", "res/drawable/icon4.png");
    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNotNull();
    assertThat(convertedFile.encoded).isNull();

    assertThat(convertedFile.sourceFile).isSameAs(file);
    assertThat(convertedFile.sourceFileSize).isEqualTo(2574);

    boolean converted = convertedFile.convert(settings);
    assertThat(converted).isTrue();
    assertThat(convertedFile.encoded).isNotNull();
    assertThat(convertedFile.saved).isGreaterThan(0L);

    long prevSaved = convertedFile.saved;

    assertImageSimilar(getName(), convertedFile.getSourceImage(), convertedFile.getEncodedImage(), 3);

    // Re-encode at lower quality and check that we have savings
    settings.quality = 20;
    converted = convertedFile.convert(settings);
    assertThat(converted).isTrue();
    assertThat(convertedFile.encoded).isNotNull();
    assertThat(convertedFile.saved).isGreaterThan(prevSaved);
  }

  public void testAnimatedGifConversion() throws Exception {
    BufferedImage frame1 = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
    Graphics2D g1 = frame1.createGraphics();
    g1.setColor(Color.RED);
    g1.fillRect(0, 0, 64, 64);
    g1.dispose();

    BufferedImage frame2 = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2 = frame2.createGraphics();
    g2.setColor(Color.BLUE);
    g2.fillRect(0, 0, 64, 64);
    g2.dispose();

    byte[] gifBytes = createAnimatedGif(frame1, frame2);
    VirtualFile file = myFixture.getTempDirFixture().createFile("res/drawable/animation.gif");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      try {
        file.setBinaryContent(gifBytes);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.lossless = true;

    assertThat(ConvertToWebpAction.isEligibleForConversion(file, settings)).isTrue();

    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNotNull();

    boolean converted = convertedFile.convert(settings);
    assertThat(converted).isTrue();
    assertThat(convertedFile.encoded).isNotNull();
    assertThat(convertedFile.encoded.length).isGreaterThan(0);

    String header = new String(convertedFile.encoded, 0, 12, StandardCharsets.US_ASCII);
    assertThat(header.startsWith("RIFF")).isTrue();
    assertThat(header.endsWith("WEBP")).isTrue();

    String content = new String(convertedFile.encoded, StandardCharsets.US_ASCII);
    assertThat(content).contains("VP8X");
    assertThat(content).contains("ANIM");
    assertThat(content).contains("ANMF");

    BufferedImage encodedImage = convertedFile.getEncodedImage();
    assertThat(encodedImage).isNotNull();
    assertImageSimilar(getName(), convertedFile.getSourceImage(), encodedImage, 3);
  }

  public void testAnimatedGifLossyConversion() throws Exception {
    BufferedImage frame1 = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
    Graphics2D g1 = frame1.createGraphics();
    g1.setColor(Color.RED);
    g1.fillRect(0, 0, 64, 64);
    g1.dispose();

    BufferedImage frame2 = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2 = frame2.createGraphics();
    g2.setColor(Color.BLUE);
    g2.fillRect(0, 0, 64, 64);
    g2.dispose();

    byte[] gifBytes = createAnimatedGif(frame1, frame2);
    VirtualFile file = myFixture.getTempDirFixture().createFile("res/drawable/animation_lossy.gif");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      try {
        file.setBinaryContent(gifBytes);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.lossless = false;
    settings.quality = 75;

    assertThat(ConvertToWebpAction.isEligibleForConversion(file, settings)).isTrue();

    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNotNull();

    boolean converted = convertedFile.convert(settings);
    assertThat(converted).isTrue();
    assertThat(convertedFile.encoded).isNotNull();
    assertThat(convertedFile.encoded.length).isGreaterThan(0);

    String header = new String(convertedFile.encoded, 0, 12, StandardCharsets.US_ASCII);
    assertThat(header.startsWith("RIFF")).isTrue();
    assertThat(header.endsWith("WEBP")).isTrue();

    String content = new String(convertedFile.encoded, StandardCharsets.US_ASCII);
    assertThat(content).contains("VP8X");
    assertThat(content).contains("ANIM");
    assertThat(content).contains("ANMF");
    assertThat(content).contains("VP8 ");

    BufferedImage encodedImage = convertedFile.getEncodedImage();
    assertThat(encodedImage).isNotNull();
    assertImageSimilar(getName(), convertedFile.getSourceImage(), encodedImage, 5);
  }

  public void testSingleFrameGifConversion() throws Exception {
    BufferedImage frame = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = frame.createGraphics();
    g.setColor(Color.GREEN);
    g.fillRect(0, 0, 32, 32);
    g.dispose();

    byte[] gifBytes = createAnimatedGif(frame);
    VirtualFile file = myFixture.getTempDirFixture().createFile("res/drawable/single_frame.gif");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      try {
        file.setBinaryContent(gifBytes);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.lossless = true;

    assertThat(ConvertToWebpAction.isEligibleForConversion(file, settings)).isTrue();

    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNotNull();

    boolean converted = convertedFile.convert(settings);
    assertThat(converted).isTrue();
    assertThat(convertedFile.encoded).isNotNull();
    assertThat(convertedFile.encoded.length).isGreaterThan(0);

    // Single frame GIF should produce standard single-frame WebP (no ANIM/ANMF container chunks)
    String content = new String(convertedFile.encoded, StandardCharsets.US_ASCII);
    assertThat(content).doesNotContain("ANIM");
    assertThat(content).doesNotContain("ANMF");
    assertThat(convertedFile.isMultiFrame).isFalse();
    assertThat(convertedFile.frameCount).isEqualTo(1);

    BufferedImage encodedImage = convertedFile.getEncodedImage();
    assertThat(encodedImage).isNotNull();
    assertImageSimilar(getName(), convertedFile.getSourceImage(), encodedImage, 3);
  }

  public void testAnimatedGifPreviewAndFullConversion() throws Exception {
    BufferedImage frame1 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
    Graphics2D g1 = frame1.createGraphics();
    g1.setColor(Color.RED);
    g1.fillRect(0, 0, 32, 32);
    g1.dispose();

    BufferedImage frame2 = new BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2 = frame2.createGraphics();
    g2.setColor(Color.BLUE);
    g2.fillRect(0, 0, 32, 32);
    g2.dispose();

    byte[] gifBytes = createAnimatedGif(frame1, frame2);
    VirtualFile file = myFixture.getTempDirFixture().createFile("res/drawable/animation_preview.gif");
    WriteCommandAction.runWriteCommandAction(getProject(), () -> {
      try {
        file.setBinaryContent(gifBytes);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });

    WebpConversionSettings settings = new WebpConversionSettings();
    settings.skipTransparentImages = false;
    settings.lossless = true;

    WebpConvertedFile convertedFile = WebpConvertedFile.create(file, settings);
    assertThat(convertedFile).isNotNull();
    assertThat(convertedFile.isMultiFrame).isTrue();
    assertThat(convertedFile.frameCount).isEqualTo(2);

    // Preview conversion: only convert the first frame
    BufferedImage firstFrame = convertedFile.getSourceImage();
    assertThat(firstFrame).isNotNull();
    boolean previewConverted = convertedFile.convert(firstFrame, settings);
    assertThat(previewConverted).isTrue();
    String previewContent = new String(convertedFile.encoded, StandardCharsets.US_ASCII);
    assertThat(previewContent).doesNotContain("ANIM");
    assertThat(previewContent).doesNotContain("ANMF");

    // Full conversion (e.g. final background task): converts the full animation
    boolean fullConverted = convertedFile.convert(settings);
    assertThat(fullConverted).isTrue();
    String fullContent = new String(convertedFile.encoded, StandardCharsets.US_ASCII);
    assertThat(fullContent).contains("ANIM");
    assertThat(fullContent).contains("ANMF");
  }

  private static byte[] createAnimatedGif(BufferedImage... frames) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageWriter writer = ImageIO.getImageWritersByFormatName("GIF").next();
    try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
      writer.setOutput(ios);
      writer.prepareWriteSequence(null);
      for (BufferedImage frame : frames) {
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(frame);
        IIOMetadata metadata = writer.getDefaultImageMetadata(type, null);
        String metaFormat = metadata.getNativeMetadataFormatName();
        IIOMetadataNode root = (IIOMetadataNode)metadata.getAsTree(metaFormat);
        IIOMetadataNode gce = new IIOMetadataNode("GraphicControlExtension");
        gce.setAttribute("disposalMethod", "none");
        gce.setAttribute("userInputFlag", "FALSE");
        gce.setAttribute("transparentColorFlag", "FALSE");
        gce.setAttribute("delayTime", "10");
        gce.setAttribute("transparentColorIndex", "0");
        root.appendChild(gce);
        metadata.setFromTree(metaFormat, root);

        writer.writeToSequence(new IIOImage(frame, null, metadata), null);
      }
      writer.endWriteSequence();
    }
    finally {
      writer.dispose();
    }
    return baos.toByteArray();
  }
}
