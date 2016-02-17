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


import com.google.webp.libwebp;
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.Iterator;
import java.util.Locale;

import static com.android.tools.idea.rendering.webp.NativeLibHelper.loadNativeLibraryIfNeeded;

/**
 * This needs the webp jni library loaded to function.
 *
 * @see IIORegistry
 */
public class WebpImageReaderSpi extends ImageReaderSpi {

  public static final String EXT_WEBP = "webp";

  private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
  private static final byte[] WEBP = {'W', 'E', 'B', 'P'};

  private static final int MAX_FILE_SIZE = 0x6400000;  // 100 Megs

  private static Logger ourLogger;

  static {
    IIORegistry.getDefaultInstance().registerServiceProvider(new WebpImageReaderSpi(), ImageReaderSpi.class);
  }

  /**
   * Ensures that the static initializer of the class has been run and it's registered as a service provider.
   */
  public static void ensureWebpRegistered() {
  }

  @NotNull
  static Logger getLogger() {
    if (ourLogger == null) {
      ourLogger = Logger.getInstance(WebpImageReaderSpi.class);
    }
    return ourLogger;
  }

  private WebpImageReaderSpi() {
    super();
    vendorName = "Google Inc.";
    version = "1";
    names = suffixes = new String[]{EXT_WEBP};
    MIMETypes = new String[]{"image/webp"};
    pluginClassName = WebpReader.class.getName();
    inputTypes = new Class<?>[]{ImageInputStream.class};
  }

  @Override
  public boolean canDecodeInput(@NotNull Object source) throws IOException {
    assert source instanceof ImageInputStream;
    ImageInputStream stream = (ImageInputStream) source;
    long length = stream.length();
    // The length may be -1 for files of unknown size.
    // Accept them for now and if needed, throw an IOException later.
    if (length > MAX_FILE_SIZE) {
      return false;
    }

    stream.mark();
    try {
      byte[] header = new byte[12];
      int bytesRead = stream.read(header, 0, 12);
      return bytesRead == 12 &&
             arrayEquals(header, 0, RIFF.length, RIFF) &&
             arrayEquals(header, 8, WEBP.length, WEBP) &&
             loadNativeLibraryIfNeeded();
    } finally {
      try {
        stream.reset();
      }
      catch (IOException e) {
        getLogger().error(e);
      }
    }
  }

  private static boolean arrayEquals(@NotNull byte[] a1, int offset, int len, @NotNull byte[] a2) {
    for (int i = 0; i < len; i++) {
      if (a1[offset + i] != a2[i]) {
        return false;
      }
    }
    return true;
  }

  @NotNull
  @Override
  public ImageReader createReaderInstance(Object extension) throws IOException {
    if (!loadNativeLibraryIfNeeded()) throw new IOException("webp decoder library not loaded.");
    return new WebpReader(this);
  }

  @NotNull
  @Override
  public String getDescription(Locale locale) {
    return "Webp Image decoding service.";
  }

  private static class WebpReader extends ImageReader {

    private static final String UNABLE_TO_READ_WEBP_IMAGE = "Unable to read webp image";

    @Nullable
    private byte[] myInputBytes;
    private final int[] myWidthOut = new int[1];
    private final int[] myHeightOut = new int[1];
    private int myError;

    public WebpReader(ImageReaderSpi originatingProvider) {
      super(originatingProvider);
    }

    @Override
    public void dispose() {
      myInputBytes = null;
    }

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
      super.setInput(input, seekForwardOnly, ignoreMetadata);
      myInputBytes = null;
      myError = 0;
    }

    @Override
    public int getNumImages(boolean allowSearch) throws IOException {
      return myInputBytes == null ? 0 : 1;
    }

    private void loadInfoIfNeeded() throws IOException {
      if (input != null && myInputBytes == null) {
        myInputBytes = readStreamFully((ImageInputStream) input);
        myError = libwebp.WebPGetInfo(myInputBytes, myInputBytes.length, myWidthOut, myHeightOut);
      }
    }

    @NotNull
    private static byte[] readStreamFully(@NotNull ImageInputStream stream) throws IOException {
      if (stream.length() != -1) {
        byte[] bytes = new byte[(int) stream.length()];  // Integer overflow prevented by canDecode check in reader spi above.

        stream.readFully(bytes);
        return bytes;
      }

      // Unknown file size
      ByteArrayOutputStream buffer = new ByteArrayOutputStream(0x100000);  // initialize with 1 Meg to minimize reallocs.
      final int bufferSize = 0x4000;    // 16k
      byte[] bytes = new byte[bufferSize];
      int idx;
      for (idx = 0; idx < MAX_FILE_SIZE / bufferSize; idx++) {  // Just to make sure we don't exceed MAX_FILE_SIZE
        int read = stream.read(bytes, 0, bufferSize);
        buffer.write(bytes, 0, read);
        if (read != bufferSize) {
          break;
        }
      }
      if (idx == MAX_FILE_SIZE / bufferSize) {
        throw new IOException("webp image too large");
      }
      return buffer.toByteArray();
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
      loadInfoIfNeeded();
      if (myError != 0) {
        // Yes, the above check is correct - zero value means an error.
        return myWidthOut[0];
      }
      throw new IOException(UNABLE_TO_READ_WEBP_IMAGE);
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
      loadInfoIfNeeded();
      if (myError != 0) {
        // Yes, the above check is correct - zero value means an error.
        return myHeightOut[0];
      }
      throw new IOException(UNABLE_TO_READ_WEBP_IMAGE);
    }

    @Nullable
    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
      return null;
    }

    @Nullable
    @Override
    public IIOMetadata getStreamMetadata() throws IOException {
      return null;
    }

    @Nullable
    @Override
    public IIOMetadata getImageMetadata(int imageIndex) throws IOException {
      return null;
    }

    @NotNull
    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
      loadInfoIfNeeded();
      if (myError == 0) {
        throw new IOException(UNABLE_TO_READ_WEBP_IMAGE);
      }
      assert myInputBytes != null;
      byte[] argb = libwebp.WebPDecodeARGB(myInputBytes, myInputBytes.length, myWidthOut, myHeightOut);
      @SuppressWarnings("UndesirableClassUsage")
      BufferedImage bi = new BufferedImage(myWidthOut[0], myHeightOut[0], BufferedImage.TYPE_INT_ARGB);
      // Copy the bytes read above to the image's data buffer.
      final int[] a = ((DataBufferInt) bi.getRaster().getDataBuffer()).getData();
      IntBuffer intBuf = ByteBuffer.wrap(argb).asIntBuffer();
      assert a.length == intBuf.remaining();
      intBuf.get(a);
      return bi;
    }
  }
}
