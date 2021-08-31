/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.tools.idea.sdk;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.io.StreamUtil;
import com.intellij.util.io.CountingGZIPInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A temporary copy of com.intellij.util.net.NetUtils#copyStreamContent that supports downloading files greater than Integer.MAX_VALUE bytes
 * long.
 * <p>
 * TODO: Delete this once Intellij fixes https://youtrack.jetbrains.com/issue/IDEA-277209
 */
public class StudioNetUtils {


  /**
   * @param indicator             progress indicator
   * @param inputStream           source stream
   * @param outputStream          destination stream
   * @param expectedContentLength expected content length in bytes, used in progress indicator (negative means unknown length).
   *                              For gzipped content, it's an expected length of gzipped/compressed content.
   *                              E.g. for HTTP, it means how many bytes should be sent over the network.
   * @return the total number of bytes written to the destination stream (may exceed expectedContentLength for gzipped content)
   * @throws IOException              if IO error occur
   * @throws ProcessCanceledException if process was canceled.
   */
  public static long copyStreamContent(@Nullable ProgressIndicator indicator,
                                       @NotNull InputStream inputStream,
                                       @NotNull OutputStream outputStream,
                                       long expectedContentLength) throws IOException, ProcessCanceledException {
    if (indicator != null) {
      indicator.checkCanceled();
      indicator.setIndeterminate(expectedContentLength <= 0);
    }
    CountingGZIPInputStream gzipStream = inputStream instanceof CountingGZIPInputStream ? (CountingGZIPInputStream)inputStream : null;
    byte[] buffer = new byte[StreamUtil.BUFFER_SIZE];
    int count;
    long bytesWritten = 0;
    long bytesRead = 0;
    while ((count = inputStream.read(buffer)) > 0) {
      outputStream.write(buffer, 0, count);
      bytesWritten += count;
      bytesRead = gzipStream != null ? gzipStream.getCompressedBytesRead() : bytesWritten;

      if (indicator != null) {
        indicator.checkCanceled();
        if (expectedContentLength > 0) {
          indicator.setFraction((double)bytesRead / expectedContentLength);
        }
      }
    }
    if (gzipStream != null) {
      // Amount of read bytes may have changed when 'inputStream.read(buffer)' returns -1
      // E.g. reading GZIP trailer doesn't produce inflated stream data.
      bytesRead = gzipStream.getCompressedBytesRead();
      if (indicator != null && expectedContentLength > 0) {
        indicator.setFraction((double)bytesRead / expectedContentLength);
      }
    }

    if (indicator != null) {
      indicator.checkCanceled();
    }

    if (bytesRead < expectedContentLength) {
      throw new IOException("Connection closed at byte " + bytesRead + ". Expected " + expectedContentLength + " bytes.");
    }

    return bytesWritten;
  }
}
