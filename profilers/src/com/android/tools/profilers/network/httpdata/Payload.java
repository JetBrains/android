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
package com.android.tools.profilers.network.httpdata;

import com.android.tools.profilers.network.NetworkConnectionsModel;
import com.google.profiler.protobuf3jarjar.ByteString;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.zip.GZIPInputStream;

/**
 * A class for fetching the payload data associated with an {@link HttpData} instance.
 */
public abstract class Payload {
  @NotNull private final NetworkConnectionsModel myModel;
  @Nullable private ByteString myCachedBytes;

  /**
   * Construct this class using {@link #newRequestPayload(NetworkConnectionsModel, HttpData)}
   * or {@link #newResponsePayload(NetworkConnectionsModel, HttpData)}
   */
  private Payload(@NotNull NetworkConnectionsModel model) {
    myModel = model;
  }

  @NotNull
  public static Payload newRequestPayload(@NotNull NetworkConnectionsModel model, @NotNull HttpData httpData) {
    return new Payload(model) {
      @Override
      protected String getId() {
        return httpData.getRequestPayloadId();
      }

      @NotNull
      @Override
      protected HttpData.Header getHeader() {
        return httpData.getRequestHeader();
      }
    };
  }

  @NotNull
  public static Payload newResponsePayload(@NotNull NetworkConnectionsModel model, @NotNull HttpData httpData) {
    return new Payload(model) {
      @Override
      protected String getId() {
        return httpData.getResponsePayloadId();
      }

      @NotNull
      @Override
      protected HttpData.Header getHeader() {
        return httpData.getResponseHeader();
      }
    };
  }

  protected abstract String getId();

  @NotNull
  protected abstract HttpData.Header getHeader();

  /**
   * Get this payload as a byte string.
   */
  @NotNull
  public final ByteString getBytes() {
    if (myCachedBytes != null) {
      return myCachedBytes;
    }

    myCachedBytes = myModel.requestBytes(getId());
    String contentEncoding = getHeader().getContentEncoding();
    if (contentEncoding.toLowerCase().contains("gzip")) {
      try (GZIPInputStream inputStream = new GZIPInputStream(new ByteArrayInputStream(myCachedBytes.toByteArray()))) {
        myCachedBytes = ByteString.copyFrom(FileUtil.loadBytes(inputStream));
      }
      catch (IOException ignored) {
        // If we got here, it means we failed to unzip data that was supposedly zipped. Just
        // fallback and return the content directly.
      }
    }

    return myCachedBytes;
  }

  /**
   * Convert this payload into a file containing its bytes, with a filename whose extension is
   * chosen based on this data's content type.
   *
   * This method will always return a file, although it will be empty if this payload is empty,
   * or if a temporary file couldn't be created (which shouldn't normally happen).
   */
  @NotNull
  public final File toFile() {
    File payloadFile;
    ByteString payload = getBytes();
    HttpData.ContentType contentType = getHeader().getContentType();
    try {
      payloadFile = FileUtil.createTempFile(getId(), StringUtil.notNullize(contentType.guessFileExtension()), true);
      FileUtil.writeToFile(payloadFile, payload.toByteArray());
      // We don't expect the following call to fail but don't care if it does
      //noinspection ResultOfMethodCallIgnored
      payloadFile.setReadOnly();
    }
    catch (IOException ignored) {
      payloadFile = new File("");
    }

    return payloadFile;
  }
}
